# Review 005: RoutePlan editor

## 1. Phase 003 概要

将来の大型バス経路探索入力となる RoutePlan domain、validation、pure editing operations、RoutingRequest 境界、in-memory editor state、縦横専用編集画面、MapLibre 長押し、ScheduledRoute と共存する preview overlay を追加した。Valhalla、HTTP、道路沿いの経路計算は行わない。

## 2. BASE_SHA

`c067dfc2ba5e19cd7c93380e6db6c666e57a59f7`

全検査と archive はこの SHA を `-BaseRef` に指定する。

## 3. 最終 HEAD

実装と全品質検査を行った HEAD は `71f62cf8631ac83403feab8574d4ae6efc019adb`。本書の検査結果反映を含む最終化コミット SHA は自己参照になるため、最終再検査後の `build/review-checks/review-check-summary.txt` と archive の `meta/review-info.txt` を最終 HEAD の正とする。

## 4. 変更ファイル

- domain: `domain/routeplan/*`
- state/UI: `ui/routeplan/*`、`ui/navigation/NavigationScreen.kt`
- map: `MapScreen.kt`、`MapController.kt`、`RoutePlanOverlayController.kt`
- cancellation: `NavigationStateHolder.kt`
- tests: `domain/routeplan/*Test.kt`、`ui/routeplan/*Test.kt`、Navigation tests
- docs/build: `README.md`、`docs/architecture.md`、`docs/domain/route-plan.md`、`docs/ui/*`、version catalog/app dependencies、本書

## 5. RoutePlan 設計

RoutePlan は id、任意 name、コピー済み List<RoutePlanPoint> を持つ immutable な編集入力である。空、片端点のみ、中間点のみを許容し、完成条件を constructor に強制しない。Android SDK、MapLibre、Valhalla に非依存である。

## 6. ScheduledRoute との責務分離

RoutePlan は「どこをどう通りたいか」、ScheduledRoute は探索後の実走行 geometry である。RoutePlan の point 列を ScheduledRoute.geometry へ流用しない。preview は編集補助の直線であり、画面にも経路探索前と明記した。

## 7. RoutePlanPointType

START、DESTINATION、VIA、SHAPING の4種。STOPは運行地点としてScheduledRoute側、REJOINは将来の迂回計画側に残した。VIA/SHAPINGの意味はdomain/UIともValhalla固有語へ結合していない。

## 8. Validation

START/DESTINATION各1点、STARTが先、全中間点が両端間、point ID一意を routing ready 条件とした。GeoPoint が座標範囲/NaNを拒否する。不完全状態とvalidation errorは編集可能で、RoutingRequest生成だけを拒否する。

## 9. 編集 operations

START/DESTINATION設定・置換、VIA/SHAPING追加、削除、中間点move、VIA/SHAPING切替、中間点clear、plan clearをpure operationで実装した。端点を先頭/末尾へ固定し、端点move/type changeと対象不存在はno-op。ID generatorは注入可能である。

## 10. StateHolder 構成

RoutePlanEditorStateHolderがplan、選択point、追加mode、validation、dirty、plan overview requestをStateFlowで保持する。NavigationUiStateへ編集詳細を詰め込まない。小さなRoutePlanEditorViewModelはActivity再生成中のin-memory保持だけを担う。

## 11. Navigation/Edit 画面分離

Navigationの補助操作に「ルート編集」入口と要約だけを追加した。複雑編集は専用画面に限定し、Android back/戻る/編集完了でNavigationへ戻る。確定してもScheduledRouteは再計算しない。

## 12. 縦 UI

header、可変地図、追加mode/説明/scroll可能なpoint list/完了操作を縦配置した。Map領域とeditor領域はweightで残り高さを共有する。

## 13. 21:9 横 UI

左38%をeditor/list、右62%をMapとした。実幅600dp以上かつ横長で切り替え、固定pixel幅にしない。

## 14. Map 長押し実装

追加typeを先に選択し、MapLibre OnMapLongClickListenerのLatLngをMapController内でGeoPointへ変換してcallbackする。毎回4択dialogを出さず、連続中間点追加の操作数を抑える方式を採用した。無効座標はLogへ記録して無視する。

## 15. preview overlay

専用source/layer ID、直線LineString、4種CircleLayerをRoutePlanOverlayControllerに分離した。0/1点では線を空にし、point markerは表示できる。

## 16. ScheduledRoute との視覚的区別

ScheduledRouteは水色5px実線+casing、RoutePlanはオレンジ3px/opacity 0.72の細線である。markerはtypeごとに色と半径を変え、UI上は文字labelでも区別する。両overlayを編集画面で同時表示する。

## 17. style reload

MapControllerが最新route/planを保持し、style callbackごとに両controllerをinstallする。各controllerはsource/layer存在確認後に追加し、既存sourceのGeoJSONを更新するので二重追加しない。

## 18. reorder 方式と採用理由

中間点だけを対象とする「上へ」「下へ」ボタン方式を採用した。外部reorder依存を追加せず、Compose/アクセシビリティ/testで挙動を固定できるためである。START/DESTINATIONは常に端に固定する。

## 19. START/DESTINATION 誤操作対策

中間点は即削除、START/DESTINATIONは確認dialog後に削除する。端点はreorder/type toggle UIを持たず、再設定は既存端点の置換となる。

## 20. CancellationException 修正

ScheduledRoute repository取得はtry/catchへ変更し、CancellationExceptionを必ず再throw、通常ExceptionだけをrouteErrorへ変換する。Location FlowのcatchもCancellationExceptionを明示再throwする。Map側runCatchingはいずれもsuspend処理ではない。

## 21. Unit Test

8 suites、45 tests。RoutePlanのempty/端点置換/中間点追加削除/type toggle/reorder/固定端点/clear、validation各状態、RoutingRequest、StateHolder追加・選択・削除・bounds・完了、Navigation cancellationを検証した。

## 22. Compose Test

既存5件にeditor4件を追加し、計9件をAndroidTest APKへコンパイルした。editorの縦empty、横layout、point delete/move/toggle callback、完了callback、Navigationの編集入口をMapLibre/OpenGLなしで検証する。

## 23. 実行コマンド

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat assembleDebugAndroidTest --console=plain
powershell -ExecutionPolicy Bypass -File scripts\run-review-checks.ps1 -BaseRef c067dfc2ba5e19cd7c93380e6db6c666e57a59f7
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef c067dfc2ba5e19cd7c93380e6db6c666e57a59f7 -SkipChecks
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1 -BaseRef c067dfc2ba5e19cd7c93380e6db6c666e57a59f7 -SkipChecks
```

## 24. test 結果

PASS。45 tests、failures=0、errors=0。

## 25. lint 結果

PASS。error 0。既存のdependency更新通知のみで、Phase 003由来のCompose Modifier/Log警告は解消した。

## 26. assemble 結果

PASS。`assembleDebug`、`assembleDebugAndroidTest`とも成功し、debug/app test APKを生成した。

## 27. connected test

SKIP。adb は `C:\projects\android-sdk\platform-tools\adb.exe` で発見され、`adb devices` は exit 0 / device 0台だった。ADB checkはPASS、connectedDebugAndroidTestは「端末/emulatorなし」でSKIPとなった。adb未発見/0台はSKIP、adb devices失敗はFAILとするPhase 002の判定を維持した。

## 28. manual verification

SKIP。接続端末がないため、editor起動、Map長押し、VIA/SHAPING、reorder、delete、rotation、preview/ScheduledRoute共存の実機目視は未実施。Kotlin compile、45 Unit tests、9 Compose testsのAPK compile、source/layer ID、state/validationで代替確認した。

## 29. 既知の制限

- 地図style/目視確認にはnetworkと実機/emulatorが必要
- previewは道路に沿わない直線
- process death後のplan復元なし
- 大量point時の操作はbutton reorderで、drag/dropなし
- 日付変更線を跨ぐbounds補正なし

## 30. 未実装機能

Valhalla/HTTP/backend、車両profile/大型バス規制、道路segment指定、VICS、Room/cloud保存、検索/逆geocode/snap-to-road、map matching/逸脱/自動reroute/復帰、JCT/音声/休憩/ETA/遅延を実装していない。

## 31. Phase 004 への推奨

RoutingEngine interfaceをrouting packageへ追加し、validated RoutingRequestをValhalla adapterへ渡し、結果をScheduledRouteへ変換する。通信/JSON errorとdomain validationを分離し、VIA/SHAPING mapping、vehicle profileをadapter境界で定義する。

## 32. commit SHA

- `71f62cf8631ac83403feab8574d4ae6efc019adb` — `feat: add route plan editor foundation`
- Review 005最終化コミット — 最終`git log`とarchive metadataに記録

## 33. review archive 名

- lightweight alias: `busnav-review-latest.zip`
- full alias: `busnav-full-review-latest.zip`
- immutable: `busnav-review-<HEAD>-<timestamp>.zip`、`busnav-full-review-<HEAD>-<timestamp>.zip`

厳密なimmutable名、HEAD、BASE_SHA、self-checkは各archiveの`meta/review-info.txt`と`meta/archive-self-check.txt`を正とする。
