# Review 008: Detailed local vector basemap

## 1. Phase overview

Phase 004.5 として、既存の中部地方 OSM PBF から OpenMapTiles 互換 MBTiles を生成し、ローカル TileServer GL を介して MapLibre Android に詳細ベースマップを表示する開発基盤を追加した。Phase 005 の maneuver / Navigation Guidance は実装していない。

## 2. BASE_SHA

`7da14df7e853956410d314b30fb004618da035e8`

## 3. Current problem

従来のデモスタイルは海岸線中心で、道路、道路名、高速道路、IC/JCT 相当、地名を確認しながら START / DEST / VIA / SHAPING を配置できなかった。Valhalla が経路を返しても、入力地点と道路網の関係を画面上で検証できない状態だった。

## 4. Architecture

表示用タイルと routing graph を分離した。既存 OSM PBF は Valhalla と Planetiler の双方が読むが、Android は Valhalla の `/route` と TileServer GL の style / TileJSON / vector tile / glyph を別 endpoint で利用する。BusNav backend や独自道路情報は本 Phase の対象外である。

## 5. OSM PBF reuse

既存の `~/busnav/valhalla/custom_files/chubu-latest.osm.pbf`（510,262,495 bytes）を確認し、そのまま再利用した。中部地方 PBF の重複 download や repository 内への copy は行っていない。`BUSNAV_OSM_PBF` で明示 path を指定でき、未指定時だけ既知 path を probe する。

## 6. Tile generation method

`openmaptiles/planetiler-openmaptiles:latest` を固定入口として OpenMapTiles profile を実行し、`chubu.mbtiles` を生成した。実行時 Planetiler は 0.10.2（git `0e5588c4a6e8c29a270a33afe8df62027d889604`）、image digest は `sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89`。生成物は 301,981,696 bytes、z0–14、61,783 tiles、16,630,319 features で、repository 外の `~/.local/share/busnav/basemap/chubu.mbtiles` に置いた。

## 7. TileServer GL

`maptiler/tileserver-gl:v5.6.0`（digest `sha256:3a9ccdb24820b6814c8119bcc8a4376c39867cb0ffe69d62919ef898b90c2427`）を Docker Compose で起動する。MBTiles、style、config、glyph を read-only mount し、host は `127.0.0.1:8080`、container は 8080 を使用する。`check-tileserver.sh` は style、TileJSON、日本語 glyph、富士周辺の既知 vector tile を検証する。

## 8. Emulator network path

Windows / WSL host は `http://localhost:8080`、Android Emulator は `http://10.0.2.2:8080` を使用する。TileServer の Host header 対応により、style 内 resource URL も Emulator から到達可能な `10.0.2.2` になる。Valhalla の `10.0.2.2:8002` とは port と責務を分離した。

## 9. MapLibre integration

MapLibre Native Android 13.6.1 の既存 `MapScreen` / `MapController` に `BasemapController` を統合した。debug の `BASEMAP_STYLE_URL` は既定で `http://10.0.2.2:8080/styles/busnav/style.json`、Gradle property `busnavBasemapStyleUrl` で上書きできる。release の既定値は空文字で、localhost を配布 build に残さない。

## 10. Style source

BusNav 管理の Mapbox Style JSON は `tools/basemap/style/busnav.json` に置いた。vector source は TileServer の `chubu` data endpoint を使用し、style resource は TileServer による request host 展開を利用する。API key や secret は含まない。

## 11. Glyph / sprite handling

日本語 glyph は OpenMapTiles fonts の `Klokantech Noto Sans CJK Regular` を `prepare-fonts.sh` で repository 外に取得し、TileServer の `/fonts/{fontstack}/{range}.pbf` から配信する。`12288-12543.pbf`（148,239 bytes）を health check で確認した。現 style は sprite icon を使用しないため sprite dependency はない。

## 12. Japanese labels

道路名・地名・junction label は `name:ja`、`name`、`name:latin` の順で coalesce する。Emulator の目視確認で「長野市」「名古屋市」などの日本語地名を確認した。

## 13. Displayed road classes

motorway、motorway link / ramp、trunk、primary、secondary、tertiary、residential、service を表示する。加えて road labels、route/ref numbers、motorway junction 相当、tunnel、bridge、railway、river/water、coastline、city/town/place labels、high zoom の building を含む。

## 14. Road hierarchy

motorway を最も強く、trunk / primary、secondary / tertiary、residential / service の順に線幅・色・表示 zoom を調整した。低 zoom は motorway / trunk / major place、中 zoom は primary / secondary / ref / town / rail / water、高 zoom は local road / road name / building / junction detail を主対象とする。

## 15. Dark theme

既存の BusNav dark theme に合わせた low-glare 配色とし、海・陸・建物の彩度を抑えた。道路は階層を判別できる contrast を保ちつつ、candidate / active route overlay より目立たない。

## 16. Route overlay z-order

`OverlayLayerOrder` に BusNav layer ID を集約し、planned route、candidate / active route、START、VIA、SHAPING、DEST、vehicle を basemap の後に追加する。style load 完了時に overlay source / layer を再登録するため、詳細 style から fallback への reload 後も overlay が復元される。

## 17. Fallback behavior

詳細 style が初回 load に失敗した場合、`asset://basemap/fallback-style.json` の dark plain background へ一度だけ切り替える。詳細 style 成功後の tile/source error では破壊的な style reload を行わず、現在の overlay を維持したまま unavailable 状態を通知する。fallback 自体の失敗だけ既存 map error callback へ通知する。

## 18. Basemap state

`BasemapState` は `LOADING`、`AVAILABLE`、`UNAVAILABLE`。server 未接続または release fallback 時は「詳細地図サーバー未接続」を小さく表示する。routing state とは独立しており、basemap unavailable を Valhalla failure として扱わない。

## 19. Attribution

map 左下に `© OpenMapTiles © OpenStreetMap contributors` を常時表示する。MapLibre の attribution icon のみへ依存せず、ユーザーがデータ帰属を直接確認できる。

## 20. License notes

OSM data は ODbL と attribution 要件、OpenMapTiles schema / style は各 license と attribution、Planetiler / planetiler-openmaptiles / TileServer GL / OpenMapTiles fonts は各 upstream license に従う。詳細と upstream link は `docs/development/basemap.md` と `tools/basemap/README.md` に記録した。font binary は Git に含めない。

## 21. Generated-file Git exclusion

`.gitignore` に `*.osm.pbf`、`*.mbtiles`、`*.pmtiles` と basemap runtime / Planetiler work / generated style cache を追加した。review archive の file filter にも同拡張子と Planetiler layerstats を追加し、PBF、tiles、fonts、cache、APK、build、logcat、secret が archive に混入しない構成にした。

## 22. Deterministic tests

local server 不要の unit test で style URL config、debug/release 判定、state transition、fallback、URL sanitization、layer order、attribution を検証する。MapLibre native pixel rendering は unit test に持ち込まない。最終 `test` は 95 tests、failures 0、errors 0 で PASS。

## 23. Connected tests

Compose / instrumentation では map container、route editor overlay、unavailable message、fallback 中の editor 継続を検証する。`BasemapRuntimeSmokeTest` は server 到達時だけ style、known vector tile、日本語 glyph、Emulator 上の `AVAILABLE` を検証する。connected suite は 17 tests。

## 24. TileServer OFF smoke

TileServer 停止状態で `connectedDebugAndroidTest` を実行し、17 tests、failures 0、errors 0 を確認した。live basemap probe は `SKIP: local TileServer unavailable` を記録して return し、通常 suite と fallback UX は PASS した。

## 25. TileServer ON smoke

TileServer 起動状態で style、TileJSON、glyph、`/data/chubu/10/906/404.pbf` の health check が PASS。Pixel 8 AVD（Android 16）の最終 connected suite は 17 tests、failures 0、errors 0、skipped 0。live basemap smoke を実行し、style / tile / glyph 到達と `BasemapState.AVAILABLE` を確認した。

## 26. Road-point-selection manual smoke

Pixel 8 AVD（Android 16）で中部地方を表示し、道路と日本語地名を見ながら START / DEST / VIA / SHAPING の4種類を長押し追加できた。各 marker は basemap より前面に表示された。既知の通行可能区間を START / DEST に設定した別試行では Valhalla が 42.1 km、推定1時間27分の candidate route を返し、道路・ラベルの上に候補線が表示された。自動 live routing smoke は短距離・長距離を含む7回の計算を通したが、手動point-selectionの3ケース要件とは別である。follow-upの追加2例を下記に記録する。一時 screenshot は `build/manual-evidence/` のみに保存し Git へ含めない。

## 27. Performance

3D building、terrain、hillshade、過剰な POI は入れていない。道路確認に必要な layer と zoom gate を優先し、style は低～中価格帯 Android 端末を意識した。生成 runtime は auxiliary data / fonts を含め約1.7 GB、配信用 MBTiles は約302 MB。

## 28. Known limitations

本構成は開発用 HTTP server で、本番 CDN / authentication / cache policy は未実装。endpoint snapping、reverse geocoding、POI search、全国 tile、PMTiles offline、route-corridor cache、独自大型車道路 DB、交通実績、VICS、map matching は対象外。OSM の道路属性・名称・大型車制限の欠損や誤りは残り、実車運行の安全性を保証しない。

## 29. Phase 005 handoff

Phase 005 は既存 Valhalla response に maneuver / road name / turn direction / distance / route progress を追加し、今回の詳細地図上で Navigation Guidance を検証する。Phase 004.5 では maneuver、JCT 模式図、音声案内を実装していない。

## 30. Phase 007 handoff

将来の production / offline phase では、development の TileServer GL + MBTiles から production tile server / CDN、または MapLibre 13.6.1 の PMTiles source と route-corridor cache を比較する。`BasemapConfig` の `LOCAL_DEV` / `REMOTE_STYLE` / `FALLBACK` 境界をその切替点として使う。

## 31. Commit SHA

- `8946d867b77856f6a528b960647edfcefe1e86d8` — `feat: add detailed local vector basemap`
- 本 Review008 の確定 commit と最終 HEAD は review archive の `meta/review-info.txt` を正とする。

## 32. Archive names

生成予定の lightweight alias は `busnav-review-latest.zip`、full alias は `busnav-full-review-latest.zip`。immutable archive 名、HEAD、BASE_SHA、self-check は各 archive の `meta/review-info.txt` と `meta/archive-self-check.txt` を正とする。

## 33. Phase 004.5 follow-up / BASE_SHA

Follow-up BASE_SHA: `f9be101b90f32e9dc81c9f234a28fd4261ff0abb`。
最終HEAD・commit一覧は最終HEADで生成するarchiveの `meta/review-info.txt` を正とする。
Phase005は実装していない。

ChatGPT follow-up findings: 未接続basemap smokeのreturn/PASS扱い、detail内URL/secret未除去、
Planetiler latest/font branch HEAD依存、手動3ケース未達を修正。併せて実機向けDebug接続設定を追加した。

## 34. Developer Connection Settings

- 入口はDebugのルート編集ヘッダー「開発接続設定」。車両条件とは別表示。
- `developer/` に設定モデル、repository interface、DataStore store、connection checkerを配置。
  `ui/settings/developer/` に画面と結果メッセージを配置し、将来Settings → Developer Optionsへ移設可能。
- Preferences DataStore 1.2.1に `valhallaBaseUrl` / `basemapBaseUrl` のoverrideを保存。
  未設定時はBuildConfig、保存時はoverride、reset時はキー削除。再起動相当のstore再生成でも維持。
- Debug既定値はValhalla `http://10.0.2.2:8002`、地図 `http://10.0.2.2:8080`。
  実機はPC LAN IP（例 `http://192.168.1.100:8002` / `:8080`）。実機に10.0.2.2を使わせない説明を表示。
- http/https・host必須、userinfo/query/fragment拒否、path空か `/` のみ、port 1–65535、空port拒否。
  空白trim、末尾slash正規化。不正入力をそのままOkHttp/MapLibreへ渡さない。
- ValhallaはGET `/status`、地図はGET `/styles/busnav/style.json`。入力中のURLをテストし、勝手に保存しない。
  成功、HTTP error、timeout、DNS/host接続失敗、invalid URLを区別。redirectは追跡しない。
  bodyや例外詳細をUIへ表示しない。
- 次のrouting requestでrepositoryの最新URLを解決する。地図変更はMapView/MapControllerを維持してstyleのみreload。
  overlay sources/layersを再登録し、経路候補・START/DEST/VIA/SHAPING・カメラを保つ。fallbackから復帰可能。
- Releaseは入口/画面をDEBUG条件で無効化し、DataStoreを開かず固定configを使う。
  地図fallback、routing未設定はCONFIGURATION。開発propertyをReleaseへ流用せず、必要な場合のみ別の
  `busnavReleaseValhallaBaseUrl`（HTTPS hostname、userinfo/query/fragment禁止）を使う。
- Debug manifestだけcleartext=true、Release manifestは明示的にfalse。
  Firewall/WSL forwardingの自動変更はしていない。TileServerのbind既定値は127.0.0.1のまま。

## 35. Diagnostics and reproducibility

`MapDiagnostics` はdetail内のURLをhost/portへ短縮し、userinfo/query/fragmentを除去する。
percent-encoded URL、malformed URL-like text、bearer、token/password/API key系値、JWT様文字列も除去する。
sanitizeの後に240文字へtruncateし、途中のsecret断片を残さない。通常のエラーテキストは維持する。

Planetiler 0.10.2 / git `0e5588c4a6e8c29a270a33afe8df62027d889604` を既存確認済みdigest
`sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89`で固定。
TileServer GLは `v5.6.0@sha256:3a9ccdb24820b6814c8119bcc8a4376c39867cb0ffe69d62919ef898b90c2427`。
OpenMapTiles schemaは生成済みMBTiles metadataで3.16.0を確認した。

fonts revisionは `025ff2b2f84cc0fdf11f7b1d74b3a784595fe7a4`、tar.gz SHA-256は
`c8106d0af721bbb6adf55005fc4a9ab4ac2d5ade7b9fd8a26ab21530951b7b2f`。
固定URLのdownload→SHA-256検証→展開と、キャッシュglyph全体のmanifest再検証を実際にPASSした。
ライセンスは固定revisionのNoto Sans CJK / OpenMapTiles fontsに従う。
PBFや補助地理データの更新まで同一化する保証はなく、同等再生成では入力ファイルも保持する。

## 36. Manual point-selection / Emulator

Pixel 8 AVD / Android 16で、ADBの画面キャプチャを目視し、画面の道路付近を長押しして配置した。
地図上の既知座標を直接state holderへ投入した自動テストとは区別する。

| Case | 道路を見た操作 | 結果 |
| --- | --- | --- |
| 既存ケース | 前回§26の道路付近START/DEST | 42.1 km・推定1時間27分、candidate overlay確認（前回記録） |
| 追加A | 名古屋北側・35.24N/136.88E付近を拡大。建物・街路網・主要道路を確認後、仮の中心点を道路上のSTARTに置き換え、別の道路にDEST | 0.5 km・1分、道路上にL字のcandidate線と両端markerを目視PASS |
| 追加B | Aとは別の南側区間へ移動。主要道路にSTART、別の一般道にDEST、見えている道路上にVIAとSHAPING | 0.5 km・1分、折れ線経路と4種markerが道路上に表示、目視PASS |

初期の拡大用中心点は最終STARTへ置換しており、海や山へのblind placementを採用していない。
今回の合計は既存1例＋追加2例の3ケース。追加Bの再探索も成功。
ローカル一時画像は `build/manual-case-a.png` / `build/manual-case-b.png` 等。Git/ZIPには含めない。

手動の設定画面確認: Emulator既定値を表示、Valhalla/地図の両接続テスト成功。
不正schemeの入力で保存拒否・field下エラーを確認。
地図URLを `http://10.0.2.2:8081` に保存するとfallbackへ移り、候補線と4種marker/カメラが維持された。
reset後は詳細道路・labelが復帰し、同じ経路の再探索も成功。再起動は不要だった。

## 37. Tests and local-service OFF/ON evidence

Unit tests: 105 tests / failures 0 / errors 0。repository defaults/save/reload/reset、
LAN/Emulator/HTTPS URL、拒否ケース、MockWebServerの両service 200/HTTP failure/timeout、接続拒否、
redirect非追跡、同じrouting engineの次requestが新serverへ届くこと、diagnostic redactionを検証した。
Windows JVMではAndroidのpre-26 File.renameTo置換制約を避け、同じPreferences protobufのOkioStorageを使用。
Android実ストレージの再生成・resetはconnected testで別途PASS。

UI tests: portrait/landscapeで既定値・invalidエラー・LAN保存・reset・接続結果の成功/失敗表示をPASS。
ネイティブ地図reloadテストではfallback→詳細style、経路source、4種類のmarker layer、overlay layers、カメラ維持を検証。
描画前のnative source queryでMapLibre rendererが落ちるため、テストではstyleのsource/layer再登録を確認し、
実データの道路上表示は手動ケースで確認した。 再実行中にAVDプロセス終了が発生したため、reloadテストも実画面のMapScreenを使用し、MapViewの生成・破棄を本番と同じライフサイクルに統一した。AVD再起動後の全21件はPASS。

BasemapRuntimeSmokeTestはreturnを廃止し `Assume.assumeTrue` を使用する。
Basemap/Valhallaのprobeとlive testはBuildConfigの明示endpointを使い、ユーザーDataStoreへ依存しない。

- 両サービスOFF: `connectedDebugAndroidTest` のsuiteはPASS（21 tests）。
- 4 live cases（basemap resource、basemap reload、Valhalla engine、Valhalla UI）は正式assumption skip。
  直接 `am instrument -w -r` でも4件すべて `INSTRUMENTATION_STATUS_CODE: -4`、終了 `OK (4 tests)` を確認。
- **現在のAGP 9.4.0 XML exporterの表示制約**: 同じOFF実行のXMLは4つの
  `AssumptionViolatedException`をfailureタグに出し、集計はfailures=4 / skipped=0となった。
  Gradleタスクは成功し、runnerの-4とは一致しない。raw XMLを書き換えていない。
  本レビューの「4 skipped」はrunnerの正式結果を指し、XMLのskipped=4を主張しない。
- 両サービスON: 最終suite結果は最終HEADの `connected-debug-android-test.txt` と下記確定結果を参照。

`test` / `lint` / `assembleDebug` / `assembleRelease` / `assembleDebugAndroidTest` はPASS。
既存の依存更新・ログ利用等のlint警告と、JDKのUnsafe非推奨警告は残るがerrorはない。
最終HEADで `scripts/run-review-checks.ps1 -BaseRef <follow-up BASE_SHA>` を再実行し、Releaseもログへ含める。

## 38. Physical device / limits / archives

Physical Android test: NOT RUN
Reason: no physical device attached

実機LAN URLの入力・正規化・永続化・request反映は設計/単体/Emulatorテストで検証した。
実LAN経路の疎通自体は未検証。[実機手順](../development/device-testing.md) に
Windows IP、Docker publish/bind、Firewall、WSL2 forwarding、端末browser、アプリ確認手順を記載した。
実機model/serialは存在しないため記録しない。

Review ZIP/full ZIPは最終HEADの検証後に作成し、`meta/archive-self-check.txt` を確認する。
local.properties、APK/build、PBF/MBTiles/PMTiles、logcat、DataStore runtime、secret、device identifierを除外。
既存未追跡の `gradle/gradle-daemon-jvm.properties` と作業中に現れた `.vscode/` は利用者側ファイルとして変更・stageしない。

## 39. Final acceptance

Phase 004.5: COMPLETE

最終実装のON suite: 21 tests / failures 0 / errors 0 / skipped 0。
unit 105件、lint、Debug/Release、androidTest buildはPASS。OFFは正式assumption skip 4件、suite PASS。
手動point-selectionは3例、設定変更→fallback→reset復帰→再探索もPASS。
実機未接続のNOT RUNとAGPのassumption XML表示制約は上記の通り。

成果物は `busnav-review-latest.zip` と `busnav-full-review-latest.zip`。
最終HEADに対するcheck receiptとarchive self-checkは各ZIPのmeta/を正とし、
HEAD/BaseRef一致、禁止ファイル/端末IDの除外確認を含めて受け入れる。
Phase005の実装は行っていない。

Archive privacy: 過去Review006aのemulator識別子も匿名化した。削除diff行に残る識別子はZIP生成時にも `[device-id-redacted]` に置換するため、該当する文書diffは元の識別子を復元しない。バイナリは変更しない。
