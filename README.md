# BusNav

BusNav は、高速バス・夜行バスの実運用を想定した業務用ナビゲーションアプリです。通常は登録済みの所定経路を案内し、通行止めや運行管理上の指示がある場合だけ安全な迂回と所定経路への復帰を行うことを将来目標としています。

このリポジトリの Phase 001 / MVP0 は、経路探索ではなく Android アプリ基盤を提供します。

## 現在の実装範囲

- Kotlin / Jetpack Compose の単一 Android アプリモジュール
- MapLibre Native Android による地図表示
- Android 標準 LocationManager を抽象化した現在地取得
- 実行時の Fine / Coarse 位置情報権限要求と拒否時フォールバック
- GeoJSON ソースと SymbolLayer による自車位置表示
- 初回現在地への移動、位置更新追従、手動地図操作による追従解除、現在地ボタンによる復帰
- 利用可能幅に応じた縦画面向け地図重視 UI と横画面向け 3 カラム UI
- 夜行運行向けダークテーマ
- 純粋ロジックの単体テストと Compose UI テスト

## 開発環境

- Android Studio 2026.1 系を想定
- JDK 17 以上（CI / ローカル実行は JDK 21 でも確認）
- Android SDK Platform 37 / Build Tools 36.0.0
- Gradle Wrapper 9.7.1
- Android Gradle Plugin 9.4.0
- minSdk 23 / targetSdk 37 / compileSdk 37

Android SDK の場所は、各環境の `local.properties` に `sdk.dir` として設定してください。このファイルは Git 管理しません。

## ビルドと検証

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

macOS / Linux / WSL:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

接続済み端末または起動済みエミュレータがある場合は、次も実行できます。

```bash
./gradlew connectedDebugAndroidTest
```

デバッグ APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

## MapLibre と位置情報

地図 SDK は `org.maplibre.gl:android-sdk:13.6.1` を使用し、Compose の `AndroidView` から `MapView` をホストします。開発用スタイルは MapLibre の公開デモスタイル `https://demotiles.maplibre.org/style.json` です。API キーは不要で、MapLibre ロゴと attribution は標準表示のままです。大量取得やオフライン保存は実装していません。

位置情報は Google Play services に依存せず、Android 標準 `LocationManager` を `LocationProvider` の背後に隔離しています。フォアグラウンドで GPS / Network provider を購読し、画面停止時に解除します。バックグラウンド位置情報権限は要求しません。

## 既知の制限

- 公開デモスタイルにはネットワーク接続が必要です。
- 実端末での長時間走行、トンネル、GPS ロスト時の評価は未実施です。
- 位置情報の権限を「今後表示しない」で拒否した場合の設定画面への直接リンクは未実装です。
- 高頻度ナビ更新向けの平滑化、センサ融合、進行方向上固定は未実装です。
- 経路探索、所定経路、VICS、音声案内、オフライン地図は対象外です。

## 次フェーズ候補

所定経路モデルと表示、Valhalla 接続境界、VICS などの交通情報入力、JCT 専用案内、運行管理指示、設定画面、昼間テーマを段階的に追加します。接続点は [アーキテクチャ文書](docs/architecture.md) を参照してください。
