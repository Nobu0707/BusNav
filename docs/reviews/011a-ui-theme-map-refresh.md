# Review011a — Phase008.5A UI Theme / Map Refresh

## Scope and Git baseline

- BASE_SHA: a91d239d3400fa7c35d9a669da11ea1557bf1641
- Start: 2026-09-21, Windows PowerShell. git status / log / rev-parse HEAD / diff を確認。
- 開始時tracked差分なし。既存untracked .vscode/、gradle/gradle-daemon-jvm.propertiesは保持・未commit。
- Phase008はCOMPLETEを維持。Phase009 Detour / Rejoin、auto rerouteは未実装。
- 実装commit: fa2e273 (runtime theme / map / controls / tests)。
- docs commit: 19a4103。追加の検証修正commitを含む完全なHEAD/commit一覧はarchive metadataを正とする。

## Theme / solar / tunnel

基本LIGHT。案内付き採用経路をナビ画面で表示中だけACTIVEとし、
dark = navigationActive && (isNight || isTunnel)。未測位、編集、設定はLIGHT。
独立したセッション開始/停止操作は既存実装にないため、既存の経路採用による案内開始に合わせた。
Compose、status/navigation barアイコン、basemapをruntime切替し、Activityを再起動しない。

SolarCalculatorはNOAA一般式をpure Kotlin実装。GeoPoint/LocalDate/ZoneId、均時差/赤緯/時角、
天頂角90.833度。外部APIなし。sunriseを含む昼、sunsetを含む夜。
5km移動、日付/ZoneId変更で再計算し、夜判定を毎秒評価。
東京の春分/夏至/冬至、正確な境界、欠測、閏日、DST、極地、cache変更のUnit Testを追加。
minSdk23向けjava.time core library desugaringを有効化した。

Kanto/Chubuの実TileJSONでOpenMapTiles3.16.0 transportation.brunnelを監査。
実detector利用可能。loaded road featureのbrunnel=tunnelと15m以内の距離だけを根拠にする。
鉄道・GPS欠落等から推測しない。複数道路の属性不一致、tile/source未load、欠測はUNKNOWN。
accuracy<=30m、位置鮮度<=10秒を要求。連続進入2秒、退出4秒、UNKNOWN保持上限8秒。
実タイルのtunnel/surface geometryを選び、合成GPSで実provider経由の進入・退出を検証した。

## Presentation changes and audit

- Kanto/ChubuのLightと既存Darkを提供。日本語labels、道路、水域、鉄道、建物を維持。
- Light/Dark fallbackも提供。style切替は同一MapView/cameraを保持してoverlayを再登録する。
- route/candidateの共通描画source、START/DEST/VIA/SHAPING、current locationを保持。
- Phase008のdeviationはComposeバナーであり、native style変更の外側に保持される。
- 全Composableの固定黒文字を監査。Color.Black / 0xFF000000文字指定はなし。
  原因はbare TextがMaterialThemeだけではLocalContentColorを受け取れない点。
  ThemeがonSurfaceを提供し、Card/Surface/buttonのsemantic content colorを維持した。
  guidance/highway/deviation/editor/settings/bottom barを確認した。
- 自車はSymbolLayer iconSize=2f。bitmap形状、center anchor、heading/map alignment、
  座標sourceは維持。instrumentationでサイズ、heading、source/layerを検証し、実画面で位置を確認。
- 道路幅はzoom4〜22で単調増加。motorway > trunk > primary > secondary > tertiary >
  minor(residential) > service。trunkを独立し、route line/casingもzoomに応じてscale。
- 下部は「ルート / 迂回 / 規制 / 音声 / 表示」。maxLines=1、softWrap=false、
  固定幅撤去・weight配分・中央配置。ルート編集のaccessibility説明と機能を維持。

## Tests / build / regression

| Check | Result |
| --- | --- |
| test | PASS — Unit 218、failure/error 0 |
| lint | PASS — 既存中心の警告は残る。NewApiはdesugaringで解決 |
| assembleDebug | PASS |
| assembleRelease | PASS |
| assembleDebugAndroidTest | PASS |
| connectedDebugAndroidTest | PASS — Emulator 47 / Physical 47、failure/error/skip 0 |
| Phase008 matching/deviation regression | PASS — deterministic/live highway、deviation/recovery、raw marker、rotation |
| Light/Dark region style switch | Kanto/Chubu両方向、fallback/region切替、route/全地点/vehicle/deviation/camera |
| Compose labels | 320dp相当portrait / 狭いlandscape列、light/dark、fontScale1.0/1.3 |
| Compose content color | runtime明暗切替、bare Text対背景contrast > 7 |
| Git diff check / style server check | PASS |

最初のlintでAPI23のjava.time互換不足を検出しdesugaringを追加。
ラベルテストの文字幅overflowをText.fillMaxWidthによる明示的領域確保で修正し、
実機/Emulatorの両方で再検証した。
検証ログのsun.misc.Unsafe警告は既存ライブラリ/JDK由来で、テスト失敗ではない。
追加検証でquerySourceFeaturesの件数を元GeoJSON件数と同一視した待機条件がtimeoutしたため撤去した。
同APIは現在load済みのタイルを照会し、元データ全件の取得APIではない。
overlayはsource/layer、size/heading、同一MapView/camera、およびruntimeの実画面で検証する。
この検証修正後の最終HEADで全チェックを再実行する。

## Emulator / Physical smoke and visual QA

Emulator: Pixel 8 / Android16。Physical: SOG06 / Android14。
同じ既存ローカルTileServer/ValhallaにUSB reverse経由で接続。
端末時計は変更せず、テストClockを注入。公開道路データと合成GPSを使用し、実走行・個人traceではない。

| Smoke | Emulator | Physical |
| --- | --- | --- |
| inactive + night => LIGHT | PASS | PASS |
| navigation + day => LIGHT | PASS | PASS |
| navigation + night => DARK | PASS | PASS |
| navigation + tunnel/day => DARK | PASS | PASS |
| tunnel exit/day => LIGHT | PASS | PASS |
| editor + night => LIGHT | PASS | PASS |
| dark文字 / deviationバナー | PASS | PASS |
| 自車矢印 / route保持 | PASS | PASS |
| zoom14 / zoom18道路表示 | PASS | PASS |
| 下部単一行 / portrait / landscape | PASS | PASS |

build/phase0085-smoke/{emulator,physical}/に昼夜、トンネル、退出、編集、zoom比較、
横画面の画像を保存し目視確認した。画像、APK、ログはcommit/archive対象外。

## Limits

- 太陽計算はNOAA近似式の海面基準。地形遮蔽、気象、建物による実際の日照は扱わない。
- Tunnelはload済み現在地近傍タイルに依存する。手動pan、欠測、長いGPS遮断、未tag tunnel、
  積層道路はUNKNOWNになり得る。推測や別APIで補完しない。
- 将来の明示的navigation session APIが追加されたらnavigationActiveの入力だけを置換する。
- Custom style URLの任意色変換は行わず、既知BusNav endpointsを対象とする。
- Phase009機能の追加なし。迂回等の将来操作は無効のまま。

## Review / archives / exclusions

設計: [theme-map-presentation.md](../theme-map-presentation.md)。
README、architecture、navigation-layout、basemap手順を更新した。

最終HEADに対しscripts/run-review-checks.ps1 -BaseRef a91d239d3400fa7c35d9a669da11ea1557bf1641を実行し、
成功した同一HEADのchecksを使ってreview/full archiveを生成する。
busnav-review-latest.zip / busnav-full-review-latest.zipのmetadataにBASEと最終HEADを記録する。
archive self-checkで禁止ファイル、秘密設定、生成物、デバイスserial等の混入を検証する。

local.properties、tools/basemap/.env、大容量地図、routing graph、build/APK/logcatは未commit。
既存untrackedを保持。git reset --hard / checkout -- . / clean -fd / add -Aは使用していない。

Phase008.5A: COMPLETE。最終checksとarchive self-checkが成功していることをdelivery時に確認する。
