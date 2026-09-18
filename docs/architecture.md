# BusNav Phase 001 アーキテクチャ

## 方針

Phase 001 は単一 `app` モジュールです。UI、地図 SDK、位置情報 API の境界だけを明確にし、個人開発の初期段階で不要な DI フレームワークや機械的な多層化は導入していません。依存は `MainActivity` と Compose のルートで手動生成できる規模に保っています。

## パッケージ構成

```text
net.nobu0707.busnav
├── MainActivity.kt
├── domain/model
│   └── GeoPoint.kt
├── location
│   ├── LocationProvider.kt
│   ├── AndroidLocationProvider.kt
│   └── LocationState.kt
├── map
│   ├── MapScreen.kt
│   └── MapController.kt
└── ui
    ├── navigation
    │   ├── NavigationScreen.kt
    │   ├── NavigationStateHolder.kt
    │   └── NavigationUiState.kt
    └── theme
```

## 責務

### Activity

`MainActivity` は MapLibre のプロセス初期化、位置情報実装の生成、Compose ルートの設置だけを担当します。権限、地図、画面状態のロジックは持ちません。

### UI / 状態

`NavigationRoute` は Android の実行時権限と Lifecycle を Compose 状態へ橋渡しします。`NavigationStateHolder` は `StateFlow<NavigationUiState>` を所有し、位置更新の購読、追従 ON/OFF、再センタ要求、エラーを集約します。

`NavigationScreen` 以下は状態を受け取る表示層です。地図領域は Composable ラムダとして差し替え可能なので、UI テストは MapLibre/OpenGL を起動せずレイアウトと操作を検証できます。

### map

`MapScreen` は Compose と `MapView` の境界です。Lifecycle の start/resume/pause/stop/destroy を MapView へ転送します。

`MapController` は MapLibre 固有 API を隔離します。スタイル読込、GeoJSON の自車ソース、SymbolLayer、カメラ追従、MapLibre の移動ジェスチャ検知を担当します。位置情報は `LocationState` として受け取るだけで、Android Location API には依存しません。

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

権限拒否や provider 無効は `NavigationUiState.locationError` へ変換され、地図表示自体を止めません。手動地図操作は MapController から StateHolder へ通知され、追従状態だけを OFF にします。

## 将来の接続点

- Valhalla: `domain` に所定経路・車両制約・経路候補モデルを追加し、外部クライアントは新しい `routing` パッケージの interface 背後へ置きます。UI から HTTP クライアントを直接呼びません。
- VICS / 規制情報: `traffic` のデータソースを追加し、所定経路との照合結果を状態層へ統合します。MapController には描画用モデルのみ渡します。
- 所定経路復帰: 現在地と所定経路の偏差判定を domain サービスとし、LocationProvider や MapLibre から分離します。
- JCT 表示: NavigationUiState に案内モードと接近情報を追加し、レイアウトの中央地図領域へ一時的な専用表示を重ねます。
- DI: 実装数と環境別構成が増えるまでは手動注入を維持します。Hilt は複数スコープや多数の実装切替が実際に必要になった時点で再評価します。

## 意図的な非採用

- Google Play services Fused Location: MVP0 では Play services 非搭載端末も含めた依存の小ささを優先しました。
- MapLibre LocationComponent: 位置取得と描画を同一コンポーネントへ閉じ込めず、将来のナビ向け更新・Fake 注入・自車表現を独立させるため使用していません。
- Hilt / repository/use-case の全面導入: 現段階では抽象の数に対して複雑さが過大になるため見送りました。
