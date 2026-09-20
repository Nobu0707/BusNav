# Japan routing と地域別 basemap

Phase 004.5.1 は全国 Valhalla と関東・中部の詳細地図を用意する開発環境整備です。
Map Matching、逸脱検知、自動 reroute、detour は対象外です。

## Coverage と保存場所

Valhalla は日本全国の単一グラフを使います。東京から静岡・山梨・長野・愛知へ向かう
経路が extract 境界を越えるためです。表示地図は Kanto / Chubu を別々に選びます。
**routing coverage と表示 coverage は独立**です。Kanto 選択中も全国探索できますが、
中部側の詳細地図は空になる場合があります。将来の全国 MBTiles/PMTiles または複数 source
統合は別タスクとし、今回は全国 vector tiles を生成しません。

大容量データ・ログ・Docker cache は Git 外に保存します。

| データ | WSL 既定パス |
| --- | --- |
| Japan / Kanto PBF | `~/busnav/osm/{japan,kanto}-latest.osm.pbf` |
| 旧 Chubu PBF / graph | `~/busnav/valhalla/custom_files/` |
| Japan graph / receipt | `~/busnav/valhalla-japan/` |
| MBTiles / glyphs / 派生 style | `~/.local/share/busnav/basemap/` |

## Download と build（WSL Ubuntu）

最初に `df -h ~`、`free -h`、`nproc`、`du -sh ~/busnav ~/.local/share/busnav` と
Windows の `Get-PSDrive -PSProvider FileSystem` を確認します。数GBのPBFに加え、graph、
tar、Planetiler の一時領域と旧成果物を共存できる容量が必要です。不足時は開始しません。

```bash
cd /mnt/c/projects/BusNav
bash tools/basemap/download-osm.sh japan
bash tools/basemap/download-osm.sh kanto
bash tools/routing/build-japan.sh
bash tools/basemap/generate-region-tiles.sh kanto ~/busnav/osm/kanto-latest.osm.pbf
bash tools/basemap/start-tileserver.sh
```

ダウンロード元は [Geofabrik Japan](https://download.geofabrik.de/asia/japan.html) と
[Kanto](https://download.geofabrik.de/asia/japan/kanto.html)。`.part` の再開と公式MD5検証後に
確定名へ移します。既存ファイルは検証して再利用し、不一致なら停止します。古い正常なPBFを
削除せず、新しい `BUSNAV_OSM_DATA_DIR` へ取得してください。取得日時と更新日時は receipt
に残します。更新中に upstream の latest が変わりchecksumが不一致となった場合も停止します。

Valhalla は `3.9.0-a3a5631c4` の既存image digest
`sha256:42a9678526bd04558121968a6cffaefe9bbc483f6703deed94be1c1260879c95` を固定します。
`latest` は pull しません。6 threads、24GiB memory / 28GiB memory+swap を設定しています。
実行ホストに合わせて `BUSNAV_BUILD_THREADS` とscriptの上限を調整してください。
PBFは同一filesystem上のhard linkで再利用します。別filesystemなら同一filesystemのruntimeを
指定してください。build中は `docker stats --no-stream`、`free -h`、`df -h ~` を監視します。
admin DB warning はログへ記録し、最終exit code、成功メッセージ、tar、smokeで判断します。

Planetiler は従来のimage digest
`sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89` を維持します。
既存Chubu生成は `bash tools/basemap/generate-chubu-tiles.sh` で互換利用できます。
上書きは通常拒否し、明示的な `BUSNAV_FORCE_REGENERATE=1` でもpendingへbuildしてSQLite検証後に
置換します。タイル生成は同時に複数走らせないでください（一時領域を共有します）。

## Preview、切替、rollback

```bash
bash tools/routing/start-japan.sh preview   # 127.0.0.1:18002 で全route smoke
bash tools/routing/start-japan.sh activate  # smoke後だけ既存:8002を切替
python3 tools/routing/check-japan.py        # :8002 status + truck/maneuvers/geometry
bash tools/basemap/check-tileserver.sh
```

build中は旧Chubu serviceを停止しません。activateは旧containerを停止して
`busnav-valhalla-chubu-backup` へrenameし、同じdigestでJapanを `0.0.0.0:8002` に起動します。
health / route smoke失敗時は旧containerへrollbackします。明示的な復帰も可能です。

```bash
bash tools/routing/start-japan.sh rollback
```

旧Chubu graph、PBF、MBTilesは削除しません。次回更新は日付付きの新runtimeを
`BUSNAV_JAPAN_RUNTIME` に指定し、download → build → preview → smoke → switchの順に進めます。
build/preview containerやbackup名は再利用を拒否するため、既存containerの状態とmountを
確認してから個別にrenameしてください。稼働中runtimeを再buildしません。

TileServerは `maptiler/tileserver-gl:v5.6.0` の既存digestを維持します。
単一の `tools/basemap/style/busnav.json` からsourceだけを変えてruntimeへstyleを生成し、
`/styles/busnav-kanto/style.json`、`/styles/busnav-chubu/style.json` と
`/data/kanto.json`、`/data/chubu.json` を配信します。旧 `/styles/busnav/style.json` はChubu互換です。

停止は `docker stop busnav-valhalla` と `bash tools/basemap/stop-tileserver.sh`。
通常再開は `docker start busnav-valhalla` と `bash tools/basemap/start-tileserver.sh`。
両serviceは `unless-stopped` ですが、手動停止状態では自動再開しません。Windows/WSL再起動後は
WSL distroとDocker daemon/Desktop自体の起動が必要です。restart policyだけでWSLを起動しません。

## Android

Debugのルート編集 → 開発接続設定に Base URL と「地図地域」があります。
Kantoが既定値で、Chubuも選べます。保存時にDataStoreへ保存し、再起動後も維持します。
resetはURL overrideとregionを除去しKantoへ戻します。

| 環境 | Valhalla | Basemap |
| --- | --- | --- |
| Emulator | `http://10.0.2.2:8002` | `http://10.0.2.2:8080` |
| USB実機 | PCのLAN reachable host + `:8002` | 同じhost + `:8080` |

実LAN IPはsourceへ書きません。実機向けTileServer bindはGit対象外の
`tools/basemap/.env` に `BUSNAV_TILESERVER_BIND=0.0.0.0` を指定します。
repository defaultは127.0.0.1です。Firewall、Hyper-V、network mode、routerは自動変更しません。

地図接続テストは選択地域のTileJSONを確認し、404なら地域データ不足を明示します。
地域変更はMapLibre styleだけをreloadし、active/candidate route、START/DEST/VIA/SHAPING、
guidance、高速案内の状態を保持します。読み込み失敗時は既存fallbackを使用し、別地域へ自動切替しません。
Releaseは開発設定入口とDataStoreを利用せず、開発用HTTP endpointを埋め込みません。

## 検証とtroubleshooting

Windows adbの `devices -l` で実機のstate=deviceを確認します。Linux adbのみで判断しません。
端末serialはreviewへ残しません。unit、lint、Debug/Release、androidTest build後、
`ANDROID_SERIAL` で対象を限定して `connectedDebugAndroidTest` を実施します。
接続先はDeveloper Connectionsで端末ごとに設定します。

- 両regionのstyle / TileJSON / 代表PBFが200、日本語glyphが取得できること。
- 東京内、埼玉内、東京→埼玉、東京→静岡とChubu内のtruck/maneuversを確認。
- 100km超のshapeをdecodeし、maneuver indexとresponse parseを確認。
- 実機・EmulatorでKanto表示、一般道/高速案内、region切替、overlay保持を確認。
- server OFFのlive testはJUnit assumption skip。server ONでのregion不足は失敗として扱う。
- 地図が空ならregion coverage、TileJSON、style、glyph、接続先、bindの順に確認。
- 実車走行は不要。公共道路のテスト地点を使い、個人GPS履歴を保存しない。

検証記録は [Review011](../reviews/011-kanto-japan-routing-environment.md) を参照してください。

Gradleのconnected testは再インストールでアプリ設定を初期化する場合があります。実機のlive testは
実行時だけ次のGradle propertyを渡せます（実LAN hostはリポジトリへ保存しません）。

~~~powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.testValhallaBaseUrl=http://<LAN_HOST>:8002' `
  '-Pandroid.testInstrumentationRunnerArguments.testBasemapBaseUrl=http://<LAN_HOST>:8080'
~~~

テスト補助が本番と同じDeveloper Connections DataStoreへ保存してからlive probeを行います。
エミュレータは引数不要です。BUILD SUCCESSFULだけでなくXMLのfailure/assumption/skipが0か確認します。
connected test後にアプリを通常利用する場合はDebug APKを再installし、開発接続設定を保存します。
