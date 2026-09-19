# BusNav Phase 004 アーキテクチャ

## 方針

Phase 004 は単一 `app` モジュールを維持し、編集入力 RoutePlan、routing domain、Valhalla adapter、計算済み ScheduledRoute、UI、地図 SDK、位置情報 API の境界を明確にします。不要な DI フレームワークや機械的な多層化は導入せず、依存は `MainActivity` と Compose のルートで手動生成できる規模に保っています。

## パッケージ構成

```text
net.nobu0707.busnav
├── MainActivity.kt
├── domain
│   ├── model/GeoPoint.kt
│   ├── routeplan
│   │   ├── RoutePlan.kt / RoutePlanPoint.kt / RoutePlanPointType.kt
│   │   ├── RoutePlanOperations.kt / RoutePlanValidation.kt / RoutePlanBounds.kt
│   │   └── RoutingRequest.kt
│   └── route
│       ├── ScheduledRoute.kt
│       ├── RouteGeometry.kt / RouteBounds.kt
│       ├── RoutePoint.kt / RoutePointType.kt
│       └── ScheduledRouteRepository.kt
├── data/route
│   └── InMemoryScheduledRouteRepository.kt
├── location
│   ├── LocationProvider.kt
│   ├── AndroidLocationProvider.kt
│   └── LocationState.kt
├── map
│   ├── MapScreen.kt
│   ├── MapController.kt
│   ├── RouteOverlayController.kt
│   └── RoutePlanOverlayController.kt
└── ui
    ├── navigation
    │   ├── NavigationScreen.kt
    │   ├── NavigationStateHolder.kt
    │   └── NavigationUiState.kt
    ├── routeplan
    │   ├── RoutePlanEditorScreen.kt
    │   ├── RoutePlanEditorStateHolder.kt / RoutePlanUiState.kt
    │   └── RoutePlanEditorViewModel.kt
    └── theme
```

## 責務

### Activity

`MainActivity` は MapLibre のプロセス初期化、位置情報実装と所定経路 repository の生成、Compose ルートの設置だけを担当します。debug build だけに架空 sample route を注入し、release build は経路未選択で正常動作します。権限、地図、画面状態のロジックは持ちません。

### domain / repository

`ScheduledRoute`、`RouteGeometry`、`RoutePoint` は Android SDK と MapLibre に依存しない純粋 Kotlin model です。geometry は最低 2 点を要求し、日本国内運用を前提とした単純な緯度経度 bounds を計算します。取得元は `ScheduledRouteRepository` で抽象化し、Phase 002 は `InMemoryScheduledRouteRepository` だけを実装しています。詳細は [所定経路 domain](domain/scheduled-route.md) を参照してください。

`RoutePlan` は経路探索入力、`ScheduledRoute` は探索後の走行 geometry です。`RoutePlanOperations` と validation は純粋 Kotlin で、不完全な編集中状態を許容しながら routing 可能性を別判定します。validation 済み plan だけを `RoutingRequest` へ変換します。詳細は [RoutePlan domain](domain/route-plan.md) を参照してください。

### UI / 状態

`NavigationRoute` は Android の実行時権限と Lifecycle を Compose 状態へ橋渡しします。`NavigationStateHolder` は `StateFlow<NavigationUiState>` を所有し、位置更新の購読、所定経路読込、追従 ON/OFF、再センタ要求、経路全体表示要求、エラーを集約します。経路読込失敗は `routeError` に隔離し、位置情報と地図を停止しません。

`NavigationScreen` 以下は状態を受け取る表示層です。地図領域は Composable ラムダとして差し替え可能なので、UI テストは MapLibre/OpenGL を起動せずレイアウトと操作を検証できます。

RoutePlan は専用 `RoutePlanEditorStateHolder` と `RoutePlanUiState` が所有します。小さな ViewModel は回転時の in-memory 保持だけを担当し、Navigation state へ編集項目を混在させません。Navigation と editor は同時表示せず、走行画面に複雑編集を置きません。

### map

`MapScreen` は Compose と `MapView` の境界です。Lifecycle の start/resume/pause/stop/destroy を MapView へ転送します。

`MapController` は MapLibre 固有 API を隔離します。スタイル読込、GeoJSON の自車ソース、SymbolLayer、カメラ追従、経路 bounds fit、MapLibre の移動/長押しジェスチャ検知を担当します。MapLibre `LatLng` はこの境界内で `GeoPoint` へ変換します。`RouteOverlayController` は所定経路、`RoutePlanOverlayController` は直線 preview と 4 種 point layer を別 ID で管理します。最新 route/plan は controller 側に保持し、style load 完了ごとに同じ ID の source/layer を重複させず双方を復元します。

### location

`LocationProvider` が UI/状態層から見える抽象です。`AndroidLocationProvider` は Android 標準 `LocationManager` を使い、GPS と Network provider の更新を `Flow<LocationUpdate>` へ変換します。購読終了時には callback を解除します。

`LocationState` は座標、精度、方位、速度、時刻を保持します。方位の正規化は純粋ロジックで、将来の自車アイコン回転にも使用できます。テストでは Fake `LocationProvider` を注入できます。

## データフロー

```text
AndroidLocationProvider
  -> LocationUpdate
  -> NavigationStateHolder
  -> NavigationUiState
  -> NavigationScreen / MapScreen
  -> MapController
```

```text
ScheduledRouteRepository
  -> NavigationStateHolder
  -> NavigationUiState.activeRoute
  -> MapScreen / MapController
  -> RouteOverlayController
  -> MapLibre GeoJSON source + style layers
```

```text
RoutePlanEditorScreen long press
  -> GeoPoint
  -> RoutePlanEditorStateHolder
  -> RoutePlan + validation
  -> RoutingRequest (ready 時のみ)
  -> RoutingEngine
  -> Valhalla adapter -> POST /route
  -> candidate ScheduledRoute
  -> user confirmation
  -> NavigationUiState.activeRoute
```

## routing / Valhalla

`RoutingEngine`、`RoutingResult`、`RoutingFailure`、`RoutingSummary`、`VehicleProfile` は純粋 Kotlin domain である。Valhalla 固有の `truck`、location type、costing option、JSON/HTTP、polyline6 は `data.routing.valhalla` package に隔離する。`MainActivity` が BuildConfig endpoint から adapter を生成して Compose root へ注入する。

`RouteCalculationStateHolder` は editor state holder と分離し、network job、retry、cancellation、revision 整合性だけを担う。Map は current revision の candidate を active route より優先表示する。利用者が適用するまで Navigation 側 active route は変わらない。

権限拒否や provider 無効は `NavigationUiState.locationError` へ変換され、地図表示自体を止めません。手動地図操作は MapController から StateHolder へ通知され、追従状態だけを OFF にします。

## 将来の接続点

- Valhalla maneuvers: Phase 005 で必要 field を response model へ追加し、既存 HTTP/JSON 境界を保ったまま案内 domain へ変換します。
- VICS / 規制情報: `traffic` のデータソースを追加し、所定経路との照合結果を状態層へ統合します。MapController には描画用モデルのみ渡します。
- 所定経路復帰: 現在地と所定経路の偏差判定を domain サービスとし、LocationProvider や MapLibre から分離します。
- JCT 表示: NavigationUiState に案内モードと接近情報を追加し、レイアウトの中央地図領域へ一時的な専用表示を重ねます。
- DI: 実装数と環境別構成が増えるまでは手動注入を維持します。Hilt は複数スコープや多数の実装切替が実際に必要になった時点で再評価します。

## 意図的な非採用

- Google Play services Fused Location: MVP0 では Play services 非搭載端末も含めた依存の小ささを優先しました。
- MapLibre LocationComponent: 位置取得と描画を同一コンポーネントへ閉じ込めず、将来のナビ向け更新・Fake 注入・自車表現を独立させるため使用していません。
- Hilt / repository/use-case の全面導入: 現段階では抽象の数に対して複雑さが過大になるため見送りました。
