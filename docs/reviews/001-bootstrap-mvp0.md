# Review 001: Bootstrap + MVP0

## 1. 実装概要

空の Git リポジトリから、アプリケーション ID `net.nobu0707.busnav` の Android アプリを構築した。Compose のレスポンシブなナビ画面、MapLibre 地図、フォアグラウンド位置情報、GeoJSON 自車表示、追従状態、ダークテーマ、単体/Compose テスト、Gradle Wrapper と設計文書を追加した。

位置情報が拒否・無効・未取得でも MapLibre 地図は独立して表示される。位置取得後は自車アイコンを更新し、追従 ON の間だけカメラを更新する。MapLibre の移動ジェスチャ開始で追従を解除し、現在地ボタンで追従と再センタを再開する。

## 2. 変更ファイル一覧

### ビルド / リポジトリ

- `.gitignore`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`, `gradlew.bat`
- `app/build.gradle.kts`, `app/proguard-rules.pro`

### アプリ

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/net/nobu0707/busnav/MainActivity.kt`
- `domain/model/GeoPoint.kt`
- `location/LocationProvider.kt`, `AndroidLocationProvider.kt`, `LocationState.kt`
- `map/MapScreen.kt`, `MapController.kt`
- `ui/navigation/NavigationScreen.kt`, `NavigationStateHolder.kt`, `NavigationUiState.kt`
- `ui/theme/Color.kt`, `Theme.kt`
- `app/src/main/res/values/strings.xml`, `styles.xml`
- `app/src/main/res/values-v27/styles.xml`
- `app/src/main/res/drawable/ic_busnav_launcher.xml`

### テスト / 文書

- `app/src/test/.../NavigationLayoutResolverTest.kt`
- `app/src/test/.../LocationStateTest.kt`
- `app/src/test/.../NavigationStateHolderTest.kt`
- `app/src/androidTest/.../NavigationScreenTest.kt`
- `README.md`
- `docs/architecture.md`
- `docs/ui/navigation-layout.md`
- `docs/reviews/001-bootstrap-mvp0.md`

`local.properties` と全ビルド生成物は Git 対象外。

## 3. 主要な設計判断

- 単一 app モジュールと手動依存注入を採用。MVP0 では Hilt を導入しない。
- Activity は MapLibre 初期化と Compose ルート設置だけに限定。
- 位置情報、地図 SDK、状態/UI を package 境界で分離。
- UI テストで MapLibre/OpenGL を起動しないよう、地図領域を Composable ラムダで置換可能にした。
- Orientation 固有値ではなく利用可能な dp 幅/高さで 2 レイアウトを判定。600dp 未満の横画面は狭い 3 カラムを避ける。
- 方位はモデルに保持し、自車アイコンは現在から bearing 回転に対応。カメラの進行方向上固定は未実装。
- AndroidX の標準 minSdk 方針と対象範囲を考慮し minSdk 23、ローカル導入済み安定 SDK に合わせ target/compileSdk 37 とした。

## 4. 採用ライブラリとバージョン

| 項目 | バージョン |
| --- | --- |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin Compose plugin | 2.3.21 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| AndroidX Core | 1.19.0 |
| Lifecycle | 2.11.0 |
| Kotlin Coroutines | 1.11.0 |
| MapLibre Native Android | 13.6.1 |
| JUnit | 4.13.2 |
| AndroidX Test JUnit / Espresso | 1.3.0 / 3.7.0 |

AGP 9.4.0 の組み込み Kotlin と整合する公式例の 2.3.21 を使用した。lint は Kotlin plugin 2.4.20 の存在を通知するが、組み込み Kotlin との版ずれを避けるため現時点では更新していない。

## 5. MapLibre 統合方法

`MapView` を Compose `AndroidView` でホストし、LifecycleEventObserver で start/resume/pause/stop、破棄時に destroy を転送する。`MapController` が `getMapAsync`、スタイル読込、カメラ、ジェスチャ、GeoJSON source / SymbolLayer を所有する。

スタイルは MapLibre 公式デモの `https://demotiles.maplibre.org/style.json`。秘密情報は不要。MapLibre 標準のロゴと attribution を変更していない。スタイル/地図読込失敗はユーザー向け短文へ変換する。

自車アイコンは実行時生成する青い三角形。位置 source の Point と icon rotation を更新するため、古い annotation API は使用していない。

## 6. 位置情報実装方式

Android 標準 `LocationManager` を採用した。理由は MVP0 で Play services 依存を追加せず、Fake 実装可能な `LocationProvider` 境界を先に確立するため。GPS / Network provider を 1 秒・1m の要求条件でフォアグラウンド購読し、最後の既知位置も初期値として送る。

Fine と Coarse を同時に要求し、どちらかが許可されれば開始する。画面 Lifecycle の stop または Composable 破棄で購読を停止する。バックグラウンド権限は Manifest に存在しない。

## 7. 縦横レスポンシブ UI の実装方式

`BoxWithConstraints` の `maxWidth/maxHeight` から純粋関数で layout mode を決める。縦/狭幅は案内、可変高地図、運行情報、横並び補助操作。幅 600dp 以上の横長は左 24%、地図 58%、右 18% の 3 カラム。safeDrawing inset を全体へ適用した。

## 8. テスト内容

ローカル Unit Test 7 件:

- 縦、横、小型横画面のレイアウト判定 3 件
- bearing 正規化と不正座標拒否 2 件
- 手動操作による追従解除、現在地要求による追従復帰 2 件

Compose instrumentation test 3 件:

- 縦画面の主要 4 領域と拒否時権限 UI
- 横画面の 3 カラム主要領域
- 現在地ボタン callback

Compose テストは作成・コンパイル対象に含めたが、接続端末/起動済み emulator がなかったため実行していない。

## 9. 実行したコマンド

```text
git status --short
git log --oneline -n 10
gradle 9.4.1 wrapper --gradle-version 9.4.1
.\gradlew.bat test --stacktrace
.\gradlew.bat lint
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest
adb devices
```

依存更新後、Wrapper URL は 9.7.1 へ更新して同版で最終 3 コマンドを実行した。

## 10. 各コマンドの結果

- 開始時 Git: master にコミットなし、作業ツリーに既存ファイル/変更なし。
- Wrapper 生成: 成功。
- 初回 `test --stacktrace`: 成功。非推奨 API 警告 3 件を確認し、その後修正。
- 初回 `lint`: API 23 で API 27 の theme item を使用していたため 1 error で失敗。`values-v27` に分離して解消。
- 最終 `test`: 成功、Unit Test 7 件成功。
- 最終 `lint`: 成功、0 errors / 1 warning。
- 最終 `assembleDebug`: 成功、debug APK 生成。
- `assembleDebugAndroidTest`: 成功、Compose instrumentation test 3 件をコンパイルして test APK を生成。
- `adb devices`: 接続端末なし。このため `connectedDebugAndroidTest` は未実行。

## 11. ビルド結果

`assembleDebug` は BUILD SUCCESSFUL。`app/build/outputs/apk/debug/app-debug.apk` を生成した。native library の strip 不可通知 (`libandroidx.graphics.path.so`, `libmaplibre.so`) は依存 AAR の debug native library に関する Gradle 通知で、packaging は成功している。

## 12. lint 結果

BUILD SUCCESSFUL、0 errors / 1 warning。残る warning は Kotlin Compose plugin 2.4.20 が利用可能という版更新通知のみ。AGP 9.4.0 の組み込み Kotlin と公式構成に合わせ 2.3.21 を意図的に固定した。baseline や suppress は使用していない。

## 13. 未解決事項

- 実端末/emulator の instrumentation test と目視確認。
- 「今後表示しない」拒否後にアプリ設定画面へ誘導する専用フロー。
- ネットワーク断からスタイル再読込する明示的な retry 操作。

## 14. 既知の制限

- 地図表示は公開デモスタイルとネットワークへ依存。
- Android 標準 LocationManager の生値で、平滑化や map matching はない。
- GPS 無効の変化は購読中 callback で表示するが、設定画面を直接開かない。
- 画面再生成時に MapView の内部カメラ状態は保存せず、既定位置または現在地から復元する。
- 自車方位は GPS bearing がある場合のみ更新。端末コンパスとの融合はない。

## 15. 次フェーズへの推奨事項

1. 実端末で権限各分岐、GPS 無効、回転、復帰、長時間更新を検証する。
2. 所定経路の domain model と MapLibre line source を追加する。
3. Valhalla client は routing interface の背後へ置き、所定経路復帰ロジックと分離する。
4. VICS/規制情報を traffic source として統合し、地図描画モデルへ変換する。
5. 走行中 UI のタップ領域、輝度、警告優先順位を実車相当画面で評価する。

## 16. 意図的に実装しなかった項目

Valhalla、大型バス経路探索、PostgreSQL/PostGIS、バックエンド API、VICS、所定経路保存/復帰、経由地編集、JCT 模式図、音声案内、SA/PA、運行管理指示、独自道路評価 DB、本格ルート描画、オフライン地図、ログイン、クラウド同期、バックグラウンド位置情報、昼夜テーマ切替 UI は Phase 001 の範囲外として実装していない。

## 17. コミットハッシュ

- 実装コミット: `PENDING_AFTER_VALIDATION`
- コミットメッセージ: `feat: bootstrap Android navigation app`
