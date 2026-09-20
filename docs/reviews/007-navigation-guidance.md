# Review007 — Phase005 Navigation Guidance

## 1. 概要・版

Valhalla maneuver を計算済みルートに保持し、現在位置から次案内・その次・残距離を日本語表示する基盤を追加した。所定経路の自動変更や自動再探索はない。

- BASE_SHA: 2262f67ae03028c7e8cfaa52fc4efbc1f1470cb7
- Implementation commit: e05e76fa2234a29863c73837da5f008cde283fad (feat: add navigation guidance from Valhalla maneuvers)
- 本文の追加は後続 docs commit。最終 HEAD / BaseRef と検証の対応は archive meta と checks/review-check-summary.txt が正本。
- 変更ファイル: 下記33ファイル。開始時に追跡済み差分なし。.vscode/ と gradle/gradle-daemon-jvm.properties は未追跡のまま保持した。

## 2. Valhalla・domain・index

Request は directions_type=none から maneuvers へ変更。polyline6、kilometers、truck寸法/重量/options、最新URL provider、IO/computation dispatcherは維持する。実 3.9.0-a3a5631c4 を使用した。

Leg に maneuver list、maneuver に type/instruction/verbal pre/post/street names/begin/end/length/time/sign を追加。maneuvers modeはnarrative文字列を省略するため、欠落を許容し日本語formatterを使う。unknown fieldsはignoreUnknownKeys。raw integerはdata layerのpure mappingでSTART、左右/緩急/U-turn、ramp、exit、keep、merge、roundabout、ferry、destinationへ分類し、未対応はUNKNOWN。

SignはEXIT_NUMBER/BRANCH/TOWARD/NAMEとtext/consecutive_countを保持。type/text単位で先頭の順序を保って重複除去し、表示は出口番号→路線→方面→名称、signなしならstreet name。

legごとにdecoded.size、境界のdropsFirst、globalStartOffsetを用いる。offset=結合済み点数-(重複境界なら1)。A-B-C + C-D-E はA-B-C-D-Eとなり、次legのlocal begin/end 0/1/2はglobal 2/3/4。境界非重複も別テスト。範囲外/逆順/非単調のindexはMANEUVER_INDEXからINVALID_RESPONSEへ伝える。silent clampはしない。ScheduledRouteがgeometryとguidanceを一緒に所有し、等価比較にもguidanceを含める。

## 3. 距離・投影・進捗

RouteDistanceIndexはHaversine segment距離を累積し、route適用時に1回作成。配列公開時はcopy。RouteProjectorは局所equirectangularでsegmentへ投影し、最近傍をHaversine距離で比較。hintは初期候補/1mm以内のtie用で、全scanを残してglobal nearestを維持する。退化segment、前後端、長segmentを検証した。

**simple projection != Map Matching**。並行道路、上下線、立体交差、JCTでは近距離でも誤対応し得る。distanceFromRouteMetersを必ず保持する。閾値はconfigで30m以下reliable、30m超80m以下uncertain、80m超unreliable。GPS accuracyも同じ30m閾値を越えると表示抑制する。確率的confidenceや逸脱確定ではない。

NavigationProgressCalculatorはpure、NavigationProgressTrackerはstateful。nextはbegin累積距離+15mが現在progress以上の最初のmaneuver、next-nextはその次。15m通過猶予中の距離は0。小さい逆行(15m以下)は前回値を維持し、それより大きい逆行は許容。不確実なfixは前回reliable値を汚染しない。

**距離軸原則:** distanceToNext=max(0, geometry cumulative[next.beginGeometryIndex]-current geometry progress)。Valhalla maneuver.lengthはmetadataであり、live残距離から一切差し引かない。累積0/100/250/400m、progress175m、next index3、maneuver.length50mでも225mになる回帰テストを追加。

## 4. Formatter・走行UI・安全な表示

日本語formatterはprimary/secondary/simple symbolを生成する。距離はm、1桁小数km、10km以上は丸めたkm。未来の目的地は「目的地へ」、近くでは「目的地です」。縦画面は上カード、横は左guide領域で主案内/距離/道路方面/その次を表示し、地図を主領域として残す。

位置なし/権限なし/位置エラーは「位置情報待ち」。不確実時は「経路付近の位置を確認中」として操作・距離・next-nextを消す。no routeとno guidanceも区別する。色だけに頼らず大きな文字を使用。操作ボタンの追加なし。

手動回転で適用routeがsampleへ戻る問題を検出し、NavigationViewModelへ状態を保持した。Activity再生成後もgeometry/guidance同一snapshotを維持する回帰テストを追加。process death永続化は未対応。

NavigationStateHolderにはRoutingEngine依存がなく、位置更新から再探索/候補置換/所定経路変更は呼び出せない。live UI testは不確実位置を注入した後もrouting呼び出し回数が増えないことを確認する。

## 5. Performance・threading・diagnostics

毎fixはO(geometry points + maneuvers)、距離indexとmaneuver begin距離はcacheする。JSON再parseや距離index再構築はしない。index準備とprojectionはinjectable Dispatchers.Default、state反映はUI scope。古い計算はcancel、route置換時はcache/previous progressをreset。既存routing IO/CPU dispatcherを変更していない。

既存diagnosticsにMANEUVER_INDEX/MANEUVERS段階とnavigation.guidance.emptyを追加。serialized field型不正は既存JSON_DECODE。未知typeはエラーにしない。GPS/full routeの追加logはない。

## 6. 検証結果

| Check | 実行結果 |
| --- | --- |
| Unit tests | 141件、failure/error 0（新規36件） |
| lint | PASS |
| assembleDebug | PASS |
| assembleDebugAndroidTest | PASS |
| assembleRelease | PASS |
| connectedDebugAndroidTest | 27件、failure/error/skip 0 |
| Valhalla runtime | 既存長距離3回 + maneuver先頭/到着/global index確認 PASS |
| Valhalla UI runtime | 既存short/long計7回 PASS |
| Guidance runtime | 実探索→適用→instruction/distance/next-next→不確実→再探索2回 PASS |
| UI deterministic | portrait/landscapeの通常/不確実4件 PASS |
| Rotation | 実Activity再生成後のroute/guidance保持 PASS |
| Basemap | runtime、日本語glyph/vector、hot reload/overlay regression PASS |
| Developer Connections | 縦横の保存/reset/接続状態、DataStore再生成によるpersist regression PASS |

最終HEADでは指定run-review-checks.ps1を実行し、上記に加えてassembleReleaseも確認する。正確な最終runの成否とHEADは同梱checksのreceiptを参照。

Fixtures: localhost実3.9.0から一般道8 maneuvers、高速16 maneuvers（4 maneuverにsign、ramp/exit/keepを含む）を保存。全shape/index関係を保持。synthetic fixtureは4種類sign、重複、consecutive_count、optional narrative、unknown field。取得条件はapp/src/test/resources/valhalla/README.md。

## 7. Manual / device evidence

Emulator: sdk_gphone64_x86_64 / Android 16。標準10.0.2.2:8002 / 10.0.2.2:8080で実アプリ確認。地図長押しSTART/DEST→探索4.3km→適用→mock位置前進で「左方向の出口へ」「2.3 km」「上井出IC」「その次: 緩やかに左方向へ」を確認。詳細道路・日本語道路名・青いroute overlayが同時表示された。

回転修正後に再探索10.7km→適用→横画面で所定経路保持と不確実表示を確認。route開始付近へmock位置を置き、横画面で「出発」「0 m」「その次: 右折」を確認。エミュレーターの回転設定は元の値へ戻した。再探索とINVALID_RESPONSEなしは手動およびlive UI testで確認。スクリーンショットやraw位置列はarchiveへ含めず、要約receiptだけをchecksへ保存する。

Physical Phase005 smoke: **NOT RUN**
Reason: **no physical device attached**

実機LAN接続はユーザーによるPhase004.5 post-archive確認済みという前提を維持し、今回の実機案内成功と混同しない。実機のDeveloper Settings/LAN endpoint、Valhalla/TileServer物理接続、設定persist、guidanceは今回NOT RUN。serial/device-id、Wi-Fi MAC、credentials、raw GPS trackはreceiptへ記録しない。

## 8. Housekeeping・既知制約・Phase006

- tools/basemap/.envを最初の修正で明示ignoreし、内容を表示/stageしていない。Git/archive対象外。
- Review008 follow-up digestの脱字をcomposeと同じsha256:3a9ccdb24820b6814c8119bcc8a4376c39867cb0ffe69d62919ef898b90c2427へ訂正。image変更なし。
- Firewall、WSL networkingMode、Docker bind、endpoint UIは変更なし。既存の最新URL provider、basemap hot reload、release safetyを維持。
- 本格Map Matching、自動reroute、逸脱確定、detour/rejoin、VICS、音声、本番backendは未実装。
- 長距離はfull scanでcorrectness優先。交差/平行道路の曖昧性、暫定threshold、narrative未提供時の簡易日本語、process death永続化なしを制限として残す。
- Phase006: 型付きsignを再利用しIC/JCT専用画面、lane、shield、模式図、SA/PAを検討。本Phaseでは実装しない。

## 9. Archives・変更ファイル

公開名: busnav-review-latest.zip / busnav-full-review-latest.zip。
版付き名はbusnav-review-<final HEAD 12桁>-<UTC>.zip / busnav-full-review-<final HEAD 12桁>-<UTC>.zip。
最終HEADでrun-review-checks後、両make-*を-BaseRef 2262f67ae03028c7e8cfaa52fc4efbc1f1470cb7 -SkipChecksで実行する。HEAD/BaseRef、禁止ファイルなし、slash entry、Phase005 source包含はarchive self-checkと追加検査で確認する。

- .gitignore
- README.md
- app/src/androidTest/java/net/nobu0707/busnav/data/ValhallaRuntimeSmokeTest.kt
- app/src/androidTest/java/net/nobu0707/busnav/ui/navigation/GuidanceCardTest.kt
- app/src/androidTest/java/net/nobu0707/busnav/ui/navigation/GuidanceRuntimeSmokeTest.kt
- app/src/androidTest/java/net/nobu0707/busnav/ui/navigation/NavigationRotationTest.kt
- app/src/main/java/net/nobu0707/busnav/data/ValhallaManeuverMapper.kt
- app/src/main/java/net/nobu0707/busnav/data/ValhallaModels.kt
- app/src/main/java/net/nobu0707/busnav/data/ValhallaRouteResponseParser.kt
- app/src/main/java/net/nobu0707/busnav/domain/navigation/NavigationProgress.kt
- app/src/main/java/net/nobu0707/busnav/domain/navigation/RouteDistanceIndex.kt
- app/src/main/java/net/nobu0707/busnav/domain/navigation/RouteGuidance.kt
- app/src/main/java/net/nobu0707/busnav/domain/navigation/RouteProjector.kt
- app/src/main/java/net/nobu0707/busnav/domain/route/ScheduledRoute.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/GuidanceCard.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/GuidanceUiState.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/NavigationScreen.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/NavigationStateHolder.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/NavigationUiState.kt
- app/src/main/java/net/nobu0707/busnav/ui/navigation/NavigationViewModel.kt
- app/src/test/java/net/nobu0707/busnav/data/ValhallaGuidanceTest.kt
- app/src/test/java/net/nobu0707/busnav/data/ValhallaRoutingEngineTest.kt
- app/src/test/java/net/nobu0707/busnav/domain/navigation/NavigationProgressTest.kt
- app/src/test/java/net/nobu0707/busnav/ui/navigation/GuidanceUiStateTest.kt
- app/src/test/resources/valhalla/README.md
- app/src/test/resources/valhalla/maneuvers-highway-3.9.0.json
- app/src/test/resources/valhalla/maneuvers-local-3.9.0.json
- app/src/test/resources/valhalla/maneuvers-signs-synthetic.json
- docs/architecture.md
- docs/navigation-guidance.md
- docs/reviews/008-detailed-basemap.md
- docs/ui/navigation-layout.md
- docs/reviews/007-navigation-guidance.md
