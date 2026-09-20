# BusNav

BusNav は、高速バス・夜行バスの実運用を想定した業務用ナビゲーションアプリです。通常は登録済みの所定経路を案内し、通行止めや運行管理上の指示がある場合だけ安全な迂回と所定経路への復帰を行うことを将来目標としています。

このリポジトリの Phase 004.5 は、RoutePlan を Valhalla へ送る Phase 004.1 に加え、同じ Chubu OSM PBF から作るローカル vector basemap を表示します。

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
- Android/MapLibre 非依存の ScheduledRoute / RouteGeometry / RoutePoint domain model
- debug build の架空 sample route と repository 境界
- GeoJSON LineString、casing/main line、START / STOP / DESTINATION marker による所定経路表示
- style reload 時の overlay 復元と、位置追従を解除する「経路全体」bounds fit
- Android/MapLibre/Valhalla 非依存の RoutePlan、validation、pure editing operations、RoutingRequest 境界
- START / DESTINATION / VIA / SHAPING の追加、削除、上・下並べ替え、VIA/SHAPING 切替
- Navigation と分離した縦横対応 RoutePlan editor、地図長押し、in-memory/回転保持
- ScheduledRoute と共存する細い半透明の経路探索前 preview と 4 種 marker
- Android/Valhalla 非依存の RoutingEngine、RoutingResult、RoutingFailure、VehicleProfile
- OkHttp と kotlinx.serialization による Valhalla `POST /route` adapter、polyline6 decode、複数 leg 結合
- truck costing と寸法・重量・軸重、舗装路/道路種別の保守的 option
- 探索中/失敗/成功 summary、candidate route preview、revision stale 防止、「このルートを使用」
- Gradle property による endpoint 上書き、debug 限定 local cleartext、HTTP coroutine cancellation
- OpenMapTiles 互換 MBTiles と TileServer GL による debug 用詳細 vector basemap
- 高速道路から service road までの道路階層、道路名・地名・IC/JCT・水域・鉄道の dark style
- 詳細地図の LOADING / AVAILABLE / UNAVAILABLE、埋め込み fallback、OSM/OpenMapTiles attribution

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
.\gradlew.bat assembleDebugAndroidTest
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

## ローカル詳細地図

WSL2 上で既存 Chubu PBF を再利用し、MBTiles を生成して TileServer GL を起動します。PBF、MBTiles、glyph、Planetiler cache は Git 管理外です。

```bash
cd /mnt/c/projects/BusNav
tools/basemap/generate-chubu-tiles.sh
tools/basemap/start-tileserver.sh
```

Host の style URL は `http://localhost:8080/styles/busnav/style.json`、Android Emulator からは `http://10.0.2.2:8080/styles/busnav/style.json` です。詳細手順とトラブルシュートは [basemap 開発手順](docs/development/basemap.md) を参照してください。

## MapLibre と位置情報

地図 SDK は `org.maplibre.gl:android-sdk:13.6.1` を使用し、Compose の `AndroidView` から `MapView` をホストします。debug build はローカル TileServer GL、release build は localhost を含まない埋め込み dark fallback を使います。API キーは不要です。画面上に `© OpenMapTiles © OpenStreetMap contributors` を常時表示します。

位置情報は Google Play services に依存せず、Android 標準 `LocationManager` を `LocationProvider` の背後に隔離しています。フォアグラウンドで GPS / Network provider を購読し、画面停止時に解除します。バックグラウンド位置情報権限は要求しません。

## 既知の制限

- debug の詳細地図には WSL/Docker の TileServer GL が必要です。未起動時も fallback と route editor は利用できます。
- 本番 tile server/CDN、PMTiles offline、route-corridor cache は未実装です。
- 実端末での長時間走行、トンネル、GPS ロスト時の評価は未実施です。
- 位置情報の権限を「今後表示しない」で拒否した場合の設定画面への直接リンクは未実装です。
- 高頻度ナビ更新向けの平滑化、センサ融合、進行方向上固定は未実装です。
- truck costing は物理寸法を優先するため、bus/psv access と完全には一致せず、本来バスが通れる道路を過剰回避する可能性があります。
- 開発用車両条件は仮値であり、実車の業務運行に使用できません。
- RoutePlan/vehicle profile の永続保存、逸脱判定、自動reroute、VICS、音声案内、オフライン地図は対象外です。

## 次フェーズ候補

Phase 005 では Valhalla maneuver を使う案内、route 上の進捗、次の右左折と道路名を追加する予定です。その後、VICS、所定経路復帰、JCT 案内、運行管理指示を段階的に追加します。接続点は [アーキテクチャ文書](docs/architecture.md)、[RoutingEngine](docs/routing/routing-engine.md)、[Valhalla 接続](docs/routing/valhalla.md) を参照してください。
