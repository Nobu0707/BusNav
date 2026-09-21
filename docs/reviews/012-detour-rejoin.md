# Review012 — Phase009 Detour / Rejoin

## 1. Scope

PRESCRIBED専用の明示的迂回作成、preview、採用、案内、前方の所定経路復帰。
FREE detour、自動reroute、traffic provider、音声、遠隔配車は対象外。

## 2. BASE_SHA

作業開始時の実repoから取得: `dca95f2e96acd1ed4a293e880fd942592fec272d`。
git status/log/rev-parse/diff、ignore-space-at-eol差分を確認した。
開始時の未追跡 `.vscode/`、`gradle/gradle-daemon-jvm.properties` は保持し、commit/archiveへ含めない。

## 3. Phase008.5D review

前PhaseはPASS / COMPLETE扱い。baseline単体270件、端末61件。
FreeNavigationStateHolderの実装は変更していない。

## 4. Current state audit

NavigationUiState/StateHolder、NavigationMode、PrescribedRouteRecord/Repository、
RouteMatcher/RouteMatchIndex/RouteDeviationDetector、NavigationProgressCalculator、
HighwayGuidanceCalculator、RoutePlan/RoutePlanEditorStateHolder、
RoutingRequest/RoutingEngine/RouteCalculationStateHolder、
MapController/overlay、FreeNavigationStateHolder、NavigationScreenを監査した。
既存activeRouteは両modeの案内対象で、所定snapshotの独立保持が必要だった。
計算holderのgeneration guard、Defaultでのindex構築、中央cursor、ViewModel保持を再利用した。

## 5. PRESCRIBED / FREE boundary

保存ID・所定snapshot・車両snapshot・案内開始済みのPRESCRIBEDだけ開始可能。
FREE開始時は所定snapshot/profile/IDをclear。「迂回」は無効。
FREE recalc/preview/start/arrival/endを維持する。

## 6. Prescribed / navigation route split

prescribedRouteSnapshotは不変、activeRouteは現在案内対象。
迂回ACTIVEでもnavigationMode=PRESCRIBED、activePrescribedRouteIdは保存UUIDのまま。
prescribedSubmodeはNORMAL/DETOUR。元routeのprepared calculator/indexも保持する。

## 7. Detour domain

DetourReason、RejoinTarget、DetourDraftPoint(VIA/SHAPING)、DetourDraft、
DetourCandidate、ActiveDetour、DetourConfig、RejoinDetectorを追加。
DetourStateHolderはplanning/draft/計算/preview/採用/取消、NavigationStateHolderはactive guidanceと復帰監視を所有する。

## 8. Anchor progress

所定routeでMATCHED・ON_ROUTE・25m以内の最後のprogressと単調時刻を保持。
逸脱時も消さない。履歴なし・5分超なら開始不可。0mへの暗黙fallbackなし。

## 9. Raw GPS START

計算ボタン操作時のLocationState.pointを使用。match projectionやanchor pointは使わない。
unitと両端末E2Eでraw位置との一致を検証する。

## 10. Location quality

位置権限、取得エラー、単調時計で10秒以内、accuracy 50m以内。
FreeNavigationConfigの共通品質判定を再利用。採用時にも再確認。

## 11. Rejoin candidates

RouteDistanceIndex上の補間点を、anchor + 1/3/5kmで生成。
500mより前方、10km以内、間隔500m、最大3件、progress昇順。
候補表示・選択はHTTP 0回。rankingなし。

## 12. Safety buffers

EXIT/RAMP/KEEP/MERGE/U_TURN/DESTINATIONとHighwayDecisionの前後200m、
目的地手前300mを除外する。0候補を許容し、bufferを緩和しない。

## 13. Manual rejoin

中央cursorを元所定routeへ投影する。80m以内、前方、距離範囲、分岐・目的地bufferを検査。
経路重複で異なるprogressが曖昧な場合も拒否。理由を表示し、MANUAL RejoinTargetに正規化する。
arbitrary coordinateをDESTへ直接渡さない。

## 14. VIA / SHAPING

「経由地」「通過指定」の表記を維持。中央cursorから追加、上下順序変更・削除が可能。
STARTとREJOINは固定。元所定RoutePlanは編集しない。

## 15. VehicleProfile source

openPrescribedRoute時にrecord.vehicleProfileをsnapshotへ保持する。
テストは開発既定値とは異なる保存車両を使い、全detour requestで一致を検査する。
Room保存recordの内容と件数は不変。

## 16. RoutingRequest

raw START → draft points順 → 所定route上のREJOIN DESTINATION。
RoutePlan validationと既存RoutingEngineを再利用する。計算操作1回で1 request。

## 17. Stale / cancel

draft revisionとcalculation generation、prescribedSessionToken / ID / route identity / profileで検査する。
target/point/順序変更、cancel、session終了、FREE切替後の古い応答を適用しない。
NonCancellable fake engineで確認。renameは維持、route/profile変更通知は旧sessionをclearする。

## 18. Preview

SuccessはPREVIEWのみ。元routeの案内を維持し、別candidateに保存する。
距離・時間・元所定routeの復帰位置・地点数・保存車両寸法/重量を表示する。

## 19. Explicit activation

「この迂回経路を使用」のみでactiveRouteを切り替える。
採用時の追加HTTPは0回。回転でpreviewを保持する。

## 20. Overlay

元所定線、水色／太い紫の迂回線、番号候補、選択枠、VIA/SHAPING、raw GPSを共存させる。
手動選択が自動候補と重なる場合は選択markerを優先する。
元route→detour→地点→自車の順で、style/theme reload後にも再登録する。
縦横previewの画像を確認。採用ボタン表示とcamera fit完了を待ってから撮影する。
関東live事前迂回のプレビューは約3.7km/9分、元所定線・別道路を通る迂回線・VIA・復帰marker・raw GPSを確認した。
画像はbuild配下に保存しarchiveへ含めない。

## 21. Guidance

activeRouteの一般/高速案内と既存逸脱検知を再利用。
planning UIはLIGHT、ACTIVEナビは既存のnight/tunnel DARK policy。
speed lock中も案内計算を続け、計画画面に案内カードを表示する。

## 22. Detour deviation

迂回routeに対してON_ROUTE→SUSPECTED→OFF_ROUTEを検査。
「迂回経路から外れている可能性があります」を表示し、自動再計画しない。
手動再計画の取消では旧active detourを保持する。
明示終了は所定routeへ戻し、現位置で逸脱検知を再開する。

## 23. Prescribed rejoin matcher

通常案内で作った元経路のmatcher/indexを保持し、ACTIVE中に独立stateで照合する。
detour matcherとともにDefault上で計算し、世代・route identity・location identity確認後にMainへ適用する。
復帰確定はdetour側の逸脱判定より優先する。

## 24. Forward constraint

RouteMatchConstraintのminProgressMetersをanchor + 500mへ設定する。
score候補を選ぶ前にprojection progressを制限する。候補なしはnull match。
過去区間との交差では復帰しない。通常matcherのデフォルト挙動は不変。

## 25. Early rejoin

floor以上ならplanned target手前でも復帰可能。target通過後も許容。
exact index一致は要求しない。actual progressを保存する。

## 26. Thresholds

MATCHED、距離25m以内、精度30m以内、利用可能なheading差45度以内。
distinct連続3fix以上かつ2秒以上。古いfix/証拠間隔10秒以上はリセット。
headingは既存matcherの速度2.5m/s以上で使用、利用不可なら距離・精度・継続証拠で判定。
AMBIGUOUS/UNRELIABLEを確認fixに数えない。

## 27. Completion

元calculator/matcherを復元し、confirmed projection/stateを引き継いで現fixを処理する。
案内は実復帰地点の次maneuverから再開し、起点やplanned targetへ戻らない。
active detour/indexを解放、「所定経路に復帰しました」を4秒表示。HTTP増分0。

## 28. FREE isolation

FREEへ所定snapshotやdetourを持ち込まない。FreeNavigationStateHolderは無変更。
既存FREEの26 unitケースと3端末シナリオを回帰対象にする。

## 29. No auto reroute

candidate/manual target 0、明示calculate +1、preview/activate +0、逸脱 +0、rejoin +0。
再計画も明示操作のみ。復帰判定domainにRoutingEngine依存なし。

## 30. Safety interaction

speed既知で2m/s超なら候補・cursor・点編集・計算・採用をUI/holder両方で禁止。
speed nullは自動走行扱いにしない。cancel/明示終了は可能。
「安全な場所に停車して迂回経路を設定してください」を表示。

## 31. Performance

4001点×2 route、1000 synthetic fixのdual matcherをJVM単体テストで測定。
正式Debug unit runは **0.253 ms/update**（2系統合計）。indexは開始時に1回構築し、測定loopで再構築しない。
端末上の実走性能・長時間熱負荷評価は対象外。

## 32. Unit tests

308件（baseline270 + Phase009 38追加）、失敗0。
candidate/buffer/manual、raw START/profile/order、preview/採用、call-count、
forward/early/target-passed、fix/duration/accuracy/ambiguity/heading、
deviation/rejoin優先、session/stale/cancel、FREE isolation、speed、performanceを検証。

## 33. Instrumentation

DetourFlowTest 4件を追加。fake逸脱・fake事前迂回・live関東逸脱・live関東事前迂回。
Room保存→open/start、候補番号、manual cursor、VIA/SHAPING、preview、採用、
detour deviation、復帰、元案内再開、再計画取消、明示終了を試験する。
選択/draft/preview/activeのrecreation、縦横preview、LIGHT/DARKも確認。

## 34. Live Kanto

東京駅付近→上野駅付近の公開経路を保存し、保存車両profileから迂回を計算する。
逸脱模擬位置と、ON_ROUTEから西側VIAを置く事前迂回を実行する。
事前迂回は元経路から30m超離れるgeometryを確認する。
元経路から70〜250m離れ、matcherが曖昧でない公開合成点でOFF_ROUTE成立を待って操作する。
選択した前方pointへ模擬位置を進め、HTTPなしの復帰と元案内を確認する。

## 35. Emulator

Pixel 8 / Android 16。初回は既存AVDが-Vulkan指定のためnative rendererで失敗。
起動を-gpu swiftshader -feature Vulkan -no-snapshotへ変更して解消。アプリ依存の変更はしていない。
正式全suite **65件PASS、失敗0・省略0**。テスト強化後のDetour専用4件も全件実行・失敗0。

## 36. Physical Android

SOG06 / Android 14、接続state=device。実走なし、公開/合成fixのみ。
正式全suite **65件PASS、失敗0・省略0**。テスト強化後のDetour専用4件も全件実行・失敗0。
端末serialは本書/commit/archiveへ記録しない。

## 37. Regressions / quality

test/lint/assembleDebug/assembleRelease/assembleDebugAndroidTestはPASS。
lintはerror 0、warning 17、hint 1（既存15 warningに画面寸法/ログ使用の2件）。
Valhalla、basemap、一般/高速案内、matcher、theme、editor、library、FREE、
Developer Connections persistenceを両端末の全65件で確認した。最終HEADではANDROID_SERIALで実機を限定し、同じ全suiteを含むrun-review-checksを再実行する。結果はarchive同梱checksを正とする。

## 38. Privacy / environment

raw trace、個人住所、端末serial、runtime DB、full route JSONをcommitしない。
diagnosticsは状態遷移名だけ。route/gps値をログへ追加しない。
WSLがコマンド終了後に停止してlive probeが省略された試行はPASS根拠に含めない。
検証中はWSLを維持し、HTTP200を確認して両端末へADB reverseで接続した。
ローカル接続値は実行時設定だけで、release endpointに影響しない。

## 39. Limitations

process death復元、traffic feed、自動closure回避、音声、遠隔配車、FREE detourは対象外。
道路/進捗が曖昧な場合は復帰を保留する。近接道路・立体交差の誤判別限界は既存matcherと同じ。
候補は希望offsetをfilterする方式であり、安全地点の網羅探索や道路交通法上の安全性保証ではない。
VIAなしでは元道路が再使用され得る。詳細は[仕様書](../detour-rejoin.md)。

## 40. Phase010 handoff

DetourReason / DetourDraft / RejoinTarget / VIA / SHAPING / explicit activationを交通規制sourceの入口にできる。
Phase010でVICS / Traffic / Road Restrictionsを追加する。silent reroute禁止を継続する。

## 41. Commits

- `6ca89aa` — feat: add explicit prescribed detour planning and forward rejoin
- `7373165` — test: wait for detour preview rendering and confirm live departure
- 文書確定commit — docs: complete Phase009 detour and rejoin review

最終HEADはarchiveのmeta/review-info.txtと最終報告に収録する。

## 42. Archives

BASE_SHAをBaseRefとしてrun-review-checks.ps1 / make-review-archive.ps1 / make-full-review-archive.ps1を実行する。
self-checkでruntime DB/GPS/serial/local env/PBF/MBTiles/graph/APK/build/secretsを除外する。
最終結果は各zipのmeta/archive-self-check.txtとchecks/review-check-summary.txtを参照。

Status: **PASS / COMPLETE**（最終HEADの正式チェック・archive自己検査を完了条件とし、結果は同梱meta/checksと最終報告に収録）。
