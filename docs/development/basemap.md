# 詳細 basemap 開発手順

## 目的と構成

Phase 004.5 の開発経路は OpenStreetMap Chubu PBF を Planetiler の OpenMapTiles profile で MBTiles にし、TileServer GL から MapLibre Android へ HTTP 配信します。Valhalla は同じ PBF から別 graph を作る routing 専用サービスで、tile を返しません。

```text
chubu-latest.osm.pbf
  +-> Valhalla graph -> :8002/route
  +-> Planetiler -> chubu.mbtiles -> TileServer GL :8080 -> MapLibre
```

## Prerequisite

- Windows 11 + WSL2 Ubuntu
- Docker Engine / Docker Desktop と `docker compose`
- 既存 Chubu PBF
- 初回だけ Docker image、Planetiler 補助地理データ、Noto Sans glyph を取得できるネットワーク
- 生成時は PBF の 5～10 倍を目安にした空き SSD と 4 GiB 程度の JVM heap

確認済み候補は `$HOME/busnav/valhalla/custom_files/chubu-latest.osm.pbf` です。別の場所なら `BUSNAV_OSM_PBF` を指定します。スクリプトは PBF をダウンロードしません。

## Generate

```bash
cd /mnt/c/projects/BusNav
export BUSNAV_OSM_PBF="$HOME/busnav/valhalla/custom_files/chubu-latest.osm.pbf"
./tools/basemap/generate-chubu-tiles.sh
```

出力既定値は `~/.local/share/busnav/basemap/chubu.mbtiles` です。再生成時だけ `BUSNAV_FORCE_REGENERATE=1` を設定します。heap は `BUSNAV_PLANETILER_JAVA_OPTIONS=-Xmx6g` のように変更できます。

## Start / health / stop

```bash
./tools/basemap/start-tileserver.sh
./tools/basemap/check-tileserver.sh
curl --fail http://localhost:8080/styles/busnav/style.json
./tools/basemap/stop-tileserver.sh
```

TileServer は `maptiler/tileserver-gl:v5.6.0`、host port は 8080 です。Emulator は host loopback を `10.0.2.2` で参照するため、アプリの debug default は `http://10.0.2.2:8080/styles/busnav/style.json` です。Valhalla の 8002 と混同しないでください。

接続先はDebugアプリの「開発接続設定」で変更できます。ビルド既定値は `busnavBasemapStyleUrl` の標準 `/styles/busnav/style.json` endpointから取得します。Releaseはこのpropertyを取り込まずfallback固定です。実機向けのLAN IP・bind・Firewall確認は [device-testing.md](device-testing.md) を参照してください。

## Style / labels

`tools/basemap/style/busnav.json` は low-glare dark style です。motorway、trunk/primary、secondary/tertiary、minor/service の順に強弱を付け、rail、水域、建物、bridge/tunnel、route ref、道路名、place、motorway junction を表示します。POI と 3D、terrain、hillshade は入れません。

ラベルは `name:ja -> name -> name:latin`、道路番号は `ref` を使います。OpenMapTiles の Klokantech Noto Sans CJK Regular glyph を TileServer の `/fonts` からローカル配信します。glyph binary は Git に含めません。

## Failure / troubleshooting

- 「詳細地図サーバー未接続」: WSL で `check-tileserver.sh`、Windows で `curl http://localhost:8080/styles/busnav/style.json` を確認します。
- style は見えるが label がない: `fonts/Klokantech Noto Sans CJK Regular/12288-12543.pbf` と `/fonts.json` を確認します。
- style は見えるが道路がない: `/data/chubu.json` と `/data/chubu/10/906/404.pbf` を確認します。
- port conflict: Valhalla は 8002、TileServer は 8080 です。
- 再生成: server を止め、`BUSNAV_FORCE_REGENERATE=1 generate-chubu-tiles.sh` を実行します。
- 容量確認: `du -sh ~/.local/share/busnav/basemap`。PBF/MBTiles/cache/font は review archive にも含めません。

TileServer が無い場合、MapLibre は埋め込み dark background に fallback します。route editor、長押し、Valhalla route 計算は独立して継続します。

## Attribution / licenses

画面には `© OpenMapTiles © OpenStreetMap contributors` を表示します。OSM database は ODbL、OpenMapTiles schema/cartography は BSD/CC-BY、Planetiler は Apache-2.0、TileServer GL は BSD-2-Clause、OpenMapTiles fonts の収録 font は OFL または Apache 系です。各 upstream の license/NOTICE を配布形態に応じて再確認してください。

## Production / offline

Development は MBTiles + TileServer GL + HTTP です。Production は HTTPS tile server/CDN を別途設計し、localhost を使いません。Offline は PMTiles または route-corridor cache を将来候補としますが、Phase 004.5 では実装しません。

## 再現性の固定値

| Component | 固定値 |
| --- | --- |
| Planetiler | 0.10.2 / git `0e5588c4a6e8c29a270a33afe8df62027d889604` |
| Planetiler image | `openmaptiles/planetiler-openmaptiles@sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89` |
| TileServer GL | `v5.6.0@sha256:3a9ccdb24820b6814c8119bcc8a4376c39867cb0ffe69d62919ef898b90c2427` |
| OpenMapTiles schema | 3.16.0（生成済みMBTiles metadataのversionで確認） |
| Font/glyph source | [openmaptiles/fonts commit 025ff2b2f84cc0fdf11f7b1d74b3a784595fe7a4](https://github.com/openmaptiles/fonts/tree/025ff2b2f84cc0fdf11f7b1d74b3a784595fe7a4) |
| Font archive SHA-256 | `c8106d0af721bbb6adf55005fc4a9ab4ac2d5ade7b9fd8a26ab21530951b7b2f` |

Planetilerの既存レビューで確認済みdigestを使い、存在を未確認のversion tagは付加しません。
fontsは固定commitのtar.gzを取得し、SHA-256検証後だけ展開します。保存済みglyphもmanifestのchecksumを確認します。
Noto Sans CJK glyphのlicense/attributionは固定revisionのupstreamに従います。

入力PBFやPlanetilerが初回取得する補助データも同じファイルを保持してください。
今回のpinは実行ツールとglyphを固定し、OSM更新・補助データ更新を含むbyte-for-byte同一生成まで保証するものではありません。

## Phase 004.5.1 regional basemaps

全国Valhallaのrouting coverageとKanto/Chubuの表示coverageは独立しています。BasemapRegionをDeveloper ConnectionsのDataStoreへ保存し、BasemapConfigが地域別style URLを生成します。MapViewと経路状態を保持したままstyleをreloadします。単一style templateからruntimeで2地域のstyleを生成し、日本語glyphと旧Chubu成果物を維持します。[構築・切替・rollback手順](japan-routing-and-regional-basemaps.md)。

## Phase010.5A — Japanese road networks and shields

The profile now adds `route_network` / `route_ref` to transportation and transportation_name.
The original tile schema lacked classification on road lines, so Kanto and Chubu require a
one-time rebuild with the existing regional PBFs. Valhalla does not require regeneration.

WSL needs JDK 21+ (`javac` and `javap`) in addition to the existing Docker/Python tools.
`build-road-profile.sh` compiles against the exact pinned container classes; its dependency
cache and compiled output stay in `build/`. Do not substitute a different Planetiler image
without updating and validating both compilation and execution together. Generate the two
regions sequentially because the existing runtime temporary directory is shared:

```bash
bash tools/basemap/test-road-profile.sh
BUSNAV_FORCE_REGENERATE=1 bash tools/basemap/generate-region-tiles.sh kanto
BUSNAV_FORCE_REGENERATE=1 bash tools/basemap/generate-region-tiles.sh chubu
bash tools/basemap/start-tileserver.sh
python3 tools/basemap/audit-road-properties.py ~/.local/share/busnav/basemap/kanto.mbtiles
docker exec -i busnav-tileserver node < tools/basemap/validate-road-style.cjs
```

Generation writes a pending MBTiles file and validates it before replacement. A running
TileServer retains its old SQLite handle until restarted, so finish both rebuilds, then run
start-tileserver.sh. Large artifacts remain outside Git. The original relation fields and
OMT schema version stay intact. Android-owned vector images are registered at runtime;
TileServer does not serve a per-route-number sprite. See [road visual language](../japanese-road-visual-language.md)
and [Review014a](../reviews/014a-japanese-road-map-style.md).
