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

別 style endpoint を試す場合だけ Windows 側の `gradle.properties` などで `busnavBasemapStyleUrl=https://...` を指定します。release はこの property を取り込まず fallback 固定です。

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
