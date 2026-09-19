# Review 004: Scheduled Route Rendering

## 1. Phase 002 実装概要

Android/MapLibre 非依存の所定経路 domain、差替可能な repository、debug 専用 sample route、Navigation state/UI、MapLibre の route/point overlay、経路全体表示を追加した。Phase 001 の位置情報、自車表示、縦画面 map-first、横画面 3 カラムは維持している。

## 2. BASE_SHA

`2ee0821c101c914abcdf3e2bdb160fe643d060ca`

Phase 002 の全検査と archive はこの SHA を `-BaseRef` に指定する。

## 3. 最終 HEAD

実装コミットは `82176f8c176cecbd32040010b4582a44f34cfdf9`。Review 004 初版を含む検証 HEAD は `0a2c97cdb374652bbc377b389288145d0b3fc3ed`。検証結果を反映する本書の最終化コミットは自己参照になるため、その厳密な最終 SHA は `review-check-summary.txt` と archive の `meta/review-info.txt` に記録する。

## 4. 変更ファイル

- domain/repository: `domain/route/*`、`data/route/InMemoryScheduledRouteRepository.kt`
- map: `MapController.kt`、`MapScreen.kt`、`RouteOverlayController.kt`
- state/UI/DI: `NavigationUiState.kt`、`NavigationStateHolder.kt`、`NavigationScreen.kt`、`MainActivity.kt`、`app/build.gradle.kts`
- tests: `RouteGeometryTest.kt`、`ScheduledRouteTest.kt`、`NavigationStateHolderTest.kt`、`NavigationScreenTest.kt`
- review tooling: `review-common.ps1`、`run-review-checks.ps1`
- docs: `README.md`、`architecture.md`、`domain/scheduled-route.md`、`ui/navigation-layout.md`、`review-archive.md`、本書

## 5. ScheduledRoute domain 設計

`ScheduledRoute` は id/name/geometry/points/metadata を保持する。id/name の空文字、point id の重複を拒否し、START と DESTINATION を各 1 点要求する。list は構築時にコピーする。domain package は Android SDK/MapLibre を import しない。

## 6. RouteGeometry 設計

`RouteGeometry` は `List<GeoPoint>` をコピーし、2 点未満を拒否する。`first`、`last` と min/max latitude/longitude の `RouteBounds` を純粋 Kotlin で計算する。同一座標の連続は有効、日付変更線補正は対象外である。

## 7. RoutePoint / RoutePointType

point は id/type/position/name を保持する。type は START、DESTINATION、STOP、VIA、SHAPING、REJOIN。Phase 002 で地図表示するのは START / DESTINATION / STOP で、道路区間制約は地点 model に混在させていない。

## 8. Repository 設計

状態層は `ScheduledRouteRepository.getActiveRoute()` のみに依存する。Phase 002 は suspend API の in-memory 実装を手動注入し、将来の DB/API 置換点を確保した。取得失敗は `routeError` に隔離する。

## 9. sample route 方針

東京付近の架空座標 7 geometry point、START、STOP 2 点、DESTINATION からなる「開発用サンプルルート」を repository package に置いた。実在路線ではない旨を metadata/docs に明記した。`BuildConfig.DEBUG` だけが sample を注入し、release は route=null で正常表示する。

## 10. MapLibre source/layer 構成

- geometry source: `busnav-scheduled-route-source`
- point source: `busnav-scheduled-route-points-source`
- line: casing `#14252E` / 9px、main `#5BD6FF` / 5px
- point: START 緑、STOP 黄、DESTINATION 赤の CircleLayer。白 stroke で暗い地図上の判別性を確保
- 自車 SymbolLayer は route layer 構築後に追加し、共存する

変換は `RouteOverlayController` に限定し、domain へ MapLibre 型を漏らしていない。

## 11. style reload 対策

`MapController` が最新 route を保持する。初回 style callback と `OnDidFinishLoadingStyleListener` の双方から overlay を設置し、各 source/layer は存在確認後に追加、GeoJSON は既存 source を更新する。二重 callback でも同名追加を行わない。描画例外は `Log.e` で追跡可能にし、Map 全体を落とさない。

## 12. 経路全体表示

active route がある時だけ有効な「経路全体」ボタンを追加した。押下時は StateHolder が location follow を OFF にし、request id を進める。MapController は geometry bounds を MapView 内で 64dp 相当の padding 付き fit する。style 準備前の要求は request id を保持し、style 適用後に処理する。

## 13. Navigation state 統合

`activeRoute`、`isRouteLoading`、`routeError`、`routeOverviewRequestId` を追加した。route=null と repository failure は位置情報状態から独立しており、GPS がなくても route を描画できる。現在地操作は follow を再度 ON にする。

## 14. 縦横 UI 統合

縦は地図 `weight(1f)` を維持し、運行情報を 70dp に抑えた。横は 24/58/18% の 3 カラムを維持する。両方の運行情報に route 名または未選択を表示し、地図上の現在地/経路全体操作を分離した。両ボタンに test tag と content description がある。

## 15. テスト一覧

- RouteGeometry: 2 点生成、1 点拒否、複数座標 bounds、同一点 bounds
- ScheduledRoute/sample: id/name、START/DESTINATION、geometry endpoint、STOP
- StateHolder: repository route/null/failure、手動 gesture、現在地復帰、route overview、route=null overview
- 既存: GeoPoint/LocationState、layout resolver
- Compose androidTest: 縦主要領域、横 3 カラム、現在地 callback、route 名/経路全体 callback

Unit Test は 5 suite、17 tests。instrumentation は 4 tests を APK までコンパイルした。

## 16. 実行コマンド

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat assembleDebugAndroidTest --console=plain
powershell -ExecutionPolicy Bypass -File scripts\run-review-checks.ps1 -BaseRef 2ee0821c101c914abcdf3e2bdb160fe643d060ca
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef 2ee0821c101c914abcdf3e2bdb160fe643d060ca -SkipChecks
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1 -BaseRef 2ee0821c101c914abcdf3e2bdb160fe643d060ca -SkipChecks
```

## 17. test 結果

PASS。17 tests、failures=0、errors=0。

## 18. lint 結果

PASS。`lint` は error なしで完了し、HTML/SARIF report を生成した。

## 19. build 結果

PASS。`assembleDebug` と `assembleDebugAndroidTest` が完了し、`app-debug.apk` と `app-debug-androidTest.apk` の存在を確認した。

## 20. connected test

SKIP。`adb devices` は exit 0 で PASS したが、emulator/physical device が 0 台だった。理由は `review-check-summary.txt` / `connected-debug-android-test.txt` に保存した。androidTest の compile/package は PASS。

## 21. manual verification

端末がないため、地図上の route line/marker、縦横 rotation、現在地との見た目の共存、経路全体 camera fit の目視確認は未実施。自動検証では Kotlin compile、Compose test compile、source/layer ID、bounds/state 遷移を確認した。

## 22. 既知の制限

- 公開 demo style と地図描画にはネットワークが必要
- VIA/SHAPING/REJOIN は model に存在するが Phase 002 では非表示
- 日付変更線を跨ぐ bounds は非対応
- release build は永続 route source がないため未選択表示
- 実機での style reload/rotation/camera fit の目視確認は未実施

## 23. 意図的に未実装

Valhalla、大型バス経路探索、route editor/保存、Room/API、道路セグメント制約、VICS/渋滞/通行止め、迂回、復帰、map matching、逸脱判定、音声/JCT/IC/SA・PA、ETA/遅延、offline/cloud は実装していない。

## 24. 次 Phase 推奨

実 route source を repository 背後に接続し、まず RoutePoint の編集/検証と route versioning を定義する。その後、所定経路とは別 model/source で迂回候補を扱い、REJOIN 判定を純粋 domain service として追加する。実機で style reload と camera padding を評価する。

## 25. コミット

- `82176f8c176cecbd32040010b4582a44f34cfdf9` — `feat: add scheduled route domain and map rendering`
- Review 004 文書コミット — 最終 Git log と archive metadata に記録

## 26. Review archive

- lightweight alias: `busnav-review-latest.zip`
- full alias: `busnav-full-review-latest.zip`
- immutable timestamped archive 名と self-check は最終生成後の `meta/review-info.txt` / `meta/archive-self-check.txt` に記録

両 archive は `BaseRef=2ee0821c101c914abcdf3e2bdb160fe643d060ca` を使用し、Phase 002 全差分を収録する。

## 27. adb チェック改善

判定を `Get-AdbCheckDisposition` へ切り出した。smoke test は次の 4 ケースで PASS した。

| adb | `adb devices` | device | 結果 |
| --- | --- | --- | --- |
| 未発見 | 未実行 | - | ADB SKIP / connected SKIP |
| 発見 | exit 0 | 0 | ADB PASS / connected SKIP |
| 発見 | exit 23 | 0 | ADB FAIL / connected FAIL |
| 発見 | exit 0 | 1 | ADB PASS / connected RUN |

異常終了理由は summary に記録し、全体結果を FAIL にする。
