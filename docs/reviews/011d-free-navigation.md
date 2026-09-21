# Review011d — Phase008.5D Free Navigation

## 1. Scope

現在地→地図中央カーソルで目的地設定→大型車経路計算→preview→明示開始→一般道/高速案内・matching→到着確認→明示終了。
手動再計算を含み、自動 reroute と Phase009 detour/rejoin は含まない。

## 2. BASE_SHA

作業開始時に取得した HEAD: `d4ee5188c9131287f64322490526fee8b6895187`。
git status/log/rev-parse/diff と ignore-space-at-eol の preflight を実施。tracked changes はなし。
既存未追跡 .vscode/ と gradle/gradle-daemon-jvm.properties は保持し、commit/archive から除外する。

## 3. Phase008.5C review

指示どおり PASS / COMPLETE として継承。Room 2.8.4 + KSP 2.3.4、DB version 1、payload schema、stable UUID、exact geometry/guidance/sign/profile と既存保存操作を変更しない。
baseline: unit 244、connected 58/device。

## 4. Current state audit

旧 applyCalculatedRoute は saved ID を消して started=true とする一方 mode を変更しなかった。
旧 NavigationRoute は saved ID の null または started と maneuvers の有無から active を推測していた。
operationsSummary、route overview、deviation 文言は所定経路固定。経路読込だけで guidance 更新を行っていた。
この監査を踏まえ explicit mode / start と UI 文言を修正した。旧経路テストの入力にも明示開始を追加した。

## 5. NavigationMode invariants

PRESCRIBED は保存経路と stable ID、open=false/start=true。FREE は saved ID=null と FreeNavigationPlan、preview=false/start=true。
clear は activeRoute/freePlan/ID/started を消す。mode は最後の値でもよく、route 不在時は非active。
古い汎用 repository からの未保存経路ロードは FREE preview として扱う。本番起動時 repository は経路なし。

## 6. Session lifecycle

FreeNavigationStateHolder の SELECTING→CALCULATING→PREVIEW は設定画面の状態。
NavigationStateHolder の mode/route/freePlan/started が active session の source of truth。
previewFreeRoute / startFreeNavigation / replaceFreeRoute / clearRoute を分離。
editor candidate は previewEditorCandidate、旧 applyCalculatedRoute は明示適用用互換入口のみ。
共有計算にも generation guard を追加し、同revisionの再要求で古い結果が混入しない。

## 7. Destination selector

下部5項目維持。「ルート」の menu から FREE/library/editor。
MapSelectionCursor 共用、MapSelectionMode.NONE/ROUTE_POINT/FREE_DESTINATION を分離。
FREE は pan/zoom→中央カーソル→「目的地に設定」。終了時 reader を解除、長押しは使用しない。
操作時に「安全な場所で操作してください」。

## 8. Raw GPS START

計算ごとに NavigationUiState.location.point を読む。projection、旧経路座標、保存STARTは使用しない。
FreeNavigationPlan は destination/name のみ。一時 RoutePlan は free-UUID、既存 toRoutingRequest を使用。

## 9. Location quality

FreeNavigationConfig: age<=10,000ms、accuracy<=50m。
elapsedRealtimeMillis と monotonic elapsedRealtime。wall clock 不使用。
no permission/no location/disabled/stale/poor accuracy の各メッセージと計算抑止。null/負値/非有限精度、未来timestampも拒否。
既存 matcher の40m精度上限は維持。

## 10. VehicleProfile

DEVELOPMENT_LARGE_BUS: 12m x 2.5m x 3.5m、16t、軸重10t。state holder に他profileを注入することも可能。
FREEでもtruck costing。既存プロフィールとrequest変換を再利用し、car costingに変更しない。

## 11. Calculation

既存 RoutingEngine と RouteCalculationStateHolder を再利用。MainActivity の baseUrlProvider が Developer Connections 最新URLを取得。
single shared location source。destination変更/cancel/画面離脱で失効。非協調キャンセル応答をテスト。

## 12. Preview

route line、raw現在地、目的地marker、距離、時間、車両条件を表示。
既存 editor camera fit と余白計算を再利用。「案内開始」「目的地を変更」「キャンセル」。横向きは地図と操作欄の2列で、主操作を詳細スクロールの外に固定。
FREE選択とcandidateはViewModelに保持。計算成功だけで案内を開始しない。
画像検査で計算中とpreviewの地図サイズ差によるfitの切れを発見し、previewへの遷移で地図を再生成して実際の表示領域でfitするよう修正。端末テストは経路の緯度/経度の各端点が地図領域内に入ることも確認する。

## 13. Explicit start

「案内開始」でのみ started=true。FREE初回previewはNavigationUiStateにもcandidateを持つが案内非active。
maneuvers無しは「案内情報なし」を示し経路線案内を許可する。

## 14. Theme integration

isNavigationStarted && activeRoute を source とする。saved ID / maneuver 有無による推論は廃止。
preview/editor/library/設定はLIGHT。開始済み案内画面のみ夜間・トンネルでDARK。
再計算preview中も旧sessionを保持するが、その画面表示はLIGHT。

## 15. Wording

operationsSummary、overview contentDescription、SUSPECTED/OFF_ROUTE/RECOVERING を mode-aware に変更。
PRESCRIBEDは「所定経路」、FREEは「案内経路」、未選択は「経路：未選択」。

## 16. Guidance

既存 NavigationProgress、一般道案内、HighwayGuidance をそのまま使用。
maneuver無しの扱いを明示し、lane count等の捏造なし。

## 17. Map matching

開始後のみmatching/guidance更新。経路置換でindex/matcher/deviation/highwayをreset、arrivalもreset。
raw markerはrouteへsnapしない。初期ロードとclearの競合もgenerationで失効させる。

## 18. Manual recalc

「現在地から再計算」で最新raw GPSと同目的地から1要求。
candidateは別保持。「新しい経路を使用」で置換。失敗・cancelは旧route/startedを保持。

## 19. No auto reroute

OFF_ROUTEだけのcall増分=0、手動tap=+1、preview/confirm=+0。
unitと端末E2Eで確認。route matcher/arrival detectorはRoutingEngineを参照しない。

## 20. Arrival

pure ArrivalDetector: EN_ROUTE / APPROACHING / ARRIVED。
approach<=250m、arrival残距離<=50m AND raw目的地距離<=75m AND accuracy<=50m AND reliable progress。
2 distinct consecutive fixes、鮮度とfix間隔<=10sec。重複/古いfix、単発spike、低精度、不確かな進捗を除外。
「目的地周辺です」を表示。

## 21. End navigation

到着で自動clearしない。「案内終了」でroute/plan/marker/started/matcher/deviation/highway/arrivalを消す。
前の所定経路は復元しない。ライブラリレコード削除とは別操作。

## 22. Library isolation

FREEは保存repositoryへの依存なし。単体テストで保存records/write count不変、端末テストでRoomレコード完全一致と件数不変。
FREE終了後に保存routeのopen/startを再確認。

## 23. Session switch

案内中にFREE/library/editorへ移る場合は終了確認。cancelで旧session保持。
state holderもactive routeの上書きを拒否。libraryは終了後に開く方針。
所定経路→FREEとFREE→所定経路の両方向を検証。

## 24. Rotation

ViewModelが選択、目的地、preview、active route、cameraを保持。
端末で各段階のActivity recreationとFREE previewの縦横変更を試験する。
SDK View/listenerをViewModelに保持しない。process deathのFREE復元は対象外。

## 25. Tests

単体テスト270件（baseline 244 + 26追加）、失敗0。lintエラー0、警告15、hint 1。
Debug / Release / AndroidTest APKビルドPASS。端末全suiteは61件/device（baseline 58 + FREE E2E 3）。
位置品質、raw START、previewと明示開始、no-auto +0 / manual +1、失敗時保持、到着の誤判定防止、終了、保存非干渉、切替拒否をunitで検証。
FREE E2Eはfake一般道・高速とlive関東。既存回帰テストは新しいルートmenuと明示開始の手順へ更新し、editor遷移完了とスクロール位置を待って操作する。

## 26. Emulator

Pixel 8 / Android 16。全61件PASS、失敗0・skip0。fake一般道・高速とlive関東を含む。
中央cursorのnative座標、raw START、preview LIGHT、明示start後DARK、一般/高速カード、OFF_ROUTEと手動再計算、到着と終了、各段階recreationと縦横表示を確認。

## 27. Physical

接続状態=deviceのSOG06 / Android 14実機でも全61件PASS、失敗0・skip0。実走・実GPS trace採取はなし。
Japan Valhalla + Kanto basemapで東京駅付近→上野付近の公開模擬経路（約5.4km/11分）を計算し、全体previewと明示startを確認。画像検査でもraw marker・目的地・全経路・開始ボタンの表示を確認。
端末serialは本書/commit/archiveに記録しない。公共地点の模擬位置のみ使用。

## 28. Known limits

FREE VIA、保存への変換UI、process-death自動復元、auto rerouteは対象外。
車両条件は既存開発profile。basemapはKanto配信範囲の制約を維持。cross-region FREE smokeは今回未実施。
到着は道路上の近接確認でありバス乗降位置の保証ではない。

## 29. Phase009 handoff

PRESCRIBED専用Detour/Rejoin policyから別途実装。FREEと混同しない。「迂回」はplaceholder維持。
mode/started/freePlan、明示切替、世代管理とroute replacement resetを使用する。

## 30. Future auto-reroute

FREE向けの独立した再計算lifecycle、頻度制限、位置品質、応答世代、利用者通知を設計する。
本phaseではOFF_ROUTEを再計算トリガーに接続しない。

## 31. Commits

- `ce90b820b7b4a3bb9261a7dbf5a969e303f558af` — feat: add explicit free navigation from current location
- `b0f76009e50a2b902375a05f5e7657418a9db194` — fix: fit free route previews to the visible map viewport
- 文書確定commit — docs: complete Phase008.5D free navigation review

最終HEADの完全SHAはarchive `meta/review-info.txt`と最終報告に収録する（本文自身のcommit SHAを埋め込む循環を避ける）。

## 32. Archives

BaseRefは取得済みBASE_SHA。run-review-checks.ps1 / make-review-archive.ps1 / make-full-review-archive.ps1を使用。
review/full archiveは自己検査PASS・禁止entry=0を完了条件とする。実行結果は各zipの`meta/archive-self-check.txt`、最終HEADとBASE_SHAは`meta/review-info.txt`、正式チェック結果は`checks/review-check-summary.txt`に収録する。
最終報告で生成したzipと自己検査の結果を提示する。
runtime DB/GPS/serial/local env/map data/build/APK/secretsと既存ローカルIDE/JVM設定は除外。

Status: **PASS / COMPLETE**（archiveの自己検査結果は同梱metaと最終報告を参照）。
