# Review011 — Phase004.5.1 関東地図 / Japan routing

## 1–5. 概要、BASE_SHA、背景と方針

BASE_SHA: `42f19050ef6dc4dfce8b328f3999a1ba7016d57f`。
2026-09-20、Windows 11 / WSL2 Ubuntu / Docker / Android実機・Emulatorで検証。
開始時tracked差分なし。既存untracked `.vscode/` と `gradle/gradle-daemon-jvm.properties` は保持し未commit。

全国Valhallaを新規runtimeで構築し、previewでsmoke後に8002へ切替えた。
関東の実走行準備にはKanto詳細地図が必要であり、長距離バス経路は関東・中部のextract境界を
越えるためroutingはJapan全域とした。表示地図はKanto / Chubuを選択する。
Phase008 Map Matching / 逸脱検知 / auto reroute / detourは実装していない。

## 6–7. Source PBFとstorage

正式配布元: [Japan](https://download.geofabrik.de/asia/japan.html)、
[Kanto](https://download.geofabrik.de/asia/japan/kanto.html)。取得日2026-09-20。

| 項目 | Japan | Kanto |
| --- | --- | --- |
| WSL path | `~/busnav/osm/japan-latest.osm.pbf` | `~/busnav/osm/kanto-latest.osm.pbf` |
| bytes | 2,529,753,441 | 508,908,692 |
| source modified UTC | 2026-09-19 23:27:41 | 2026-09-19 23:27:11 |
| download完了 UTC | 2026-09-20 11:16:44 | 2026-09-20 11:13:06 |
| official MD5 | `55804564c837d119b86162534d989721` | `6d98011f1cc716df756bfeb03d21d51f` |
| integrity | PASS、fileでOSM PBF識別 | PASS、fileでOSM PBF識別 |

再実行で同一PBFのchecksum再検証と再downloadなしを確認した。
旧 `~/busnav/valhalla/custom_files/chubu-latest.osm.pbf` は再取得していない。
Japan buildにはhard linkを使い、PBFの二重コピーを避けた。
開始時WSL root空き922GiB、Windows C空き約1,025GiB。
MBTiles、fonts、派生styleは `~/.local/share/busnav/basemap/`、
Japan graph/log/receiptは `~/busnav/valhalla-japan/`。すべてGit外。

## 8–11. Japan build、resources、warnings、graph

| 項目 | 実測 |
| --- | --- |
| image | `ghcr.io/valhalla/valhalla-scripted@sha256:42a9678526bd04558121968a6cffaefe9bbc483f6703deed94be1c1260879c95` |
| version | `3.9.0-a3a5631c4`、既存と同一 |
| build start / end UTC | 2026-09-20 11:18:49 / 11:25:59 |
| elapsed | 430秒（7分10秒） |
| host | 20論理CPU、31GiB RAM、8GiB swap |
| build | 6 threads、24GiB memory / 28GiB memory+swap上限 |
| resource監視 | docker stats / free / df。終盤観測18.41GiB、OOMなし |
| exit | 0、Successfully built files、tar完了 |
| graph tile count | 1,500 `.gph` |
| graph files bytes | 3,600,209,184 |
| valhalla_tiles.tar bytes | 3,602,872,320 |

WARNレベル22件、初期build開始などのWARNING 2件、RTTOPO geometry warning 22件。
ERRORレベル33件はすべて `admin_access.admin_id` のNOT NULL制約で、Japan extractに含まれない
海外adminの既知の警告。missing foreign polygon、duplicate candidate level2=12も記録された。
fatal failureを無視したものではなく、exit0、1,500タイル、tar、version、全route smokeで成功判定した。
buildログ・start/end receiptは上記runtimeへ保存。

## 12–14. Kanto Planetiler、MBTiles、TileServer

PlanetilerはPhase004.5と同じ
`openmaptiles/planetiler-openmaptiles@sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89`。
OpenMapTiles schema 3.16.0、Java heap4GiB、mmap、20 threads、build 77秒。
既存water/natural-earth等のsourcesを再利用。geometry修復・extract境界のmissing way等の
data diagnosticsがあり、FINISHED、SQLite quick_check、代表tileと画面表示で検証した。

| 項目 | Kanto | Chubu（保持） |
| --- | --- | --- |
| MBTiles bytes | 276,660,224 | 301,981,696 |
| zoom | 0–14 | 0–14 |
| bounds | 134.04515,18.62505,155.60582,37.15988 | 135.4393,34.26649,139.909,38.90717 |
| format / layer count | pbf / 13 | pbf / 13 |
| SQLite quick_check | ok | ok |

Kantoの広いboundsは離島を含む配布extract由来で、bbox内全域の詳細道路coverageを意味しない。
TileServer GL 5.6.0と既存digest、fonts/glyphs、日本語label、道路styleを維持。
単一tracked styleからruntimeでsourceだけを地域別に置換し、巨大style複製はcommitしていない。

- `/data/kanto.json`、`/data/chubu.json`: HTTP200。
- `/styles/busnav-kanto/style.json`、`/styles/busnav-chubu/style.json`: HTTP200。
- `/styles/busnav/style.json`: Chubu互換を維持。
- Kanto z10/909/403、Chubu z10/906/404 PBF: HTTP200。
- 日本語glyph range12288–12543: HTTP200。

## 15–18. Region model、DataStore、reloadとoverlay

`BasemapRegion.KANTO/CHUBU` と `BasemapConfig.forRegion` でURL生成を集約。
Debug既定Kanto、無効な保存値はKantoへfallback、DataStore再生成で選択保持、resetで削除。
地域接続テストは選択TileJSONを確認し、404を地域データ不足として表示する。
server不通時は既存fallback、勝手にChubuへ切替しない。

MapViewを保持してstyleだけをreloadし、既存controllerがroute/point sourceを再登録する。
Kanto→Chubu、Chubu→Kanto、fallback→詳細地図の3テストでroute、START/DEST/VIA/SHAPING、
source/layer、同じMapView、camera保持を確認した。routing engineとguidanceの状態を変更する処理は追加していない。

## 19–23. Android / Japan / Kanto / cross-region smoke

| Server truck route | 距離 | 結果 |
| --- | --- | --- |
| 東京→埼玉 | 34.8km | PASS |
| 東京→神奈川 | 33.7km | PASS |
| 東京→静岡 | 176.1km | PASS |
| 東京→山梨 | 126.9km | PASS |
| 中部内 | 61.7km | PASS |

preview18002と稼働8002の双方でHTTP200、version3.9.0、trip/summary/legs、truck、
directions_type=maneuvers、非空maneuvers、polyline6 decode、index範囲、100km超を確認。
preview初回応答は各0.10–0.19秒、timeoutなし。

| 環境 | Basemap / route | 検証 |
| --- | --- | --- |
| Emulator Android16 | Kanto / 東京・埼玉・高速 | UI探索・案内・画像目視 |
| Emulator Android16 | Chubu / 中部内 | Phase004.1/005/006 live regression |
| Emulator Android16 | Kanto / 関東→中部 | 100km超候補route表示 |
| Physical Android14 | Kanto / 東京内・埼玉内・東京→埼玉 | UI探索、地図長押しによるSTART/DEST配置 |
| Physical Android14 | Kanto / 高速 | 一般/高速案内、縦横、Activity再生成、画像目視 |

実機はWindows adbでstate=deviceを確認。serialと実LAN IPは本書へ記録しない。
公開道路付近のテスト地点と注入位置を使用し、実車走行・個人GPS traceは使用していない。
実機・Emulatorの画像で道路階層、日本語道路名、建物、ランプ、route overlay、attributionを確認。
地図を移動・拡大してネイティブ長押しで地点配置し、東京内から県境越えまで候補表示を検証。
画像はローカルbuild内に保存しarchive対象外。

Gradle再installで実機設定が初期化され、追加live testがassumption skipになったケースを検出した。
`testValhallaBaseUrl` / `testBasemapBaseUrl` の実行時引数で本番DataStoreへ設定して再実行。
実機全37件でfailures/errors/skipped=0、追加した実機の地図長押しsmokeもPASS。
Emulatorの最終一括結果はarchive内checksとXML集計で確認する。
再検証時に既存NavigationRotationTestがDataStore読込前にViewModelを取得する競合を検出。
ComposeのidleだけでなくNavigation画面の存在を待つようテストを修正し、再検証した。
実サーバー接続テスト、Kanto/Chubu保存、Activity再生成後のKanto保持も検証対象。

## 24–26. Regression / release / quality

unit 172件PASS。lint、assembleDebug、assembleRelease、assembleDebugAndroidTest PASS。
Phase004.1 response parse/長距離、Phase004.5 fallback/Developer Connections、Phase005一般案内、
Phase006高速/JCT案内と再計算・再生成をunitおよびlive instrumentationで検証。
auto reroute追加なし。Releaseは開発入口非表示、開発DataStoreを開かず、basemap fallbackを維持。
Debug用LAN endpointをrelease/sourceへ追加していない。

## 27–29. Git exclusion / cleanup / rollback

PBF/MBTiles/PMTiles/graph/cache/APK/logcat/local.properties/実.envは未commit。
archive禁止判定へ `.gph`、valhalla_tiles、valhalla_tiles.tar、Planetiler作業領域を追加し、
代表禁止pathのself-check PASS。既存untracked user filesは変更・削除していない。
旧Chubu PBF/graph/MBTilesを保持。一時領域の独断削除はしていない。

旧containerは停止状態の `busnav-valhalla-chubu-backup` へ保持。
Japanはread-only graph mountと `unless-stopped` で0.0.0.0:8002へ公開。
TileServerも既存ローカルbindと `unless-stopped` を維持。
`bash tools/routing/start-japan.sh rollback` で旧containerを戻せる。
実際の障害注入rollbackテストやWindows再起動テストは未実施。
WSL/Docker自体の起動と、手動停止時のrestart policy制約はdevelopment guideへ記載。
Firewall / Hyper-V / network mode / routerの変更なし。

## 30–31. Known limitations / Phase008 handoff

全国routingと地域別表示coverageは独立。Kanto→Chubu探索成功でも選択外の詳細地図は空になり得る。
将来の全国MBTiles/PMTilesまたはmulti-source統合は別Phase。
Japan extract由来のadmin/geometry warningが残る。実運行品質の保証や実走行試験の代替ではない。
Phase008に必要なJapan Valhalla、Kanto/Chubu地図、実機接続、関東route planningを用意した。
Phase008自体は未着手。実走行は安全な環境・助手席操作を前提とし、走行中の操作を要求しない。

## 32–33. Commits / archives

- `2f5b641fee2e1a93fd5df3d54f6a5f7dab03c181`: Japan routing / Kanto basemap workflow。
- `91b2a87caa837ec761c09243611e8cc60a9378be`: Developer region selectionとunit/instrumentation/live smoke。
- 本書を含むdocs commit後のHEADを最終review対象とする。完全SHAはarchiveのmetaに収録。

最終HEADに `scripts/run-review-checks.ps1 -BaseRef 42f19050ef6dc4dfce8b328f3999a1ba7016d57f` を実行し、
成功後にreview/full archiveを `-SkipChecks` で作成する。自己参照SHAを本文へ埋め込まず、
archive metaのHEAD/BaseRef、checks、self-checkを最終記録とする。
出力は `busnav-review-latest.zip` と `busnav-full-review-latest.zip`。
禁止生成物、実.env、local.properties、device serial、secretの混入0を検証する。
