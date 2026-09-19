# 所定経路 domain

## モデル

`ScheduledRoute` は immutable な `id`、`name`、`RouteGeometry`、順序付き `RoutePoint`、最小限の `RouteMetadata` を持ちます。ID と名前は空文字を拒否し、point ID は route 内で一意、START と DESTINATION は各 1 点を要求します。

`RouteGeometry` は `List<GeoPoint>` を防御的にコピーし、最低 2 点を要求します。`first`、`last` と、最小/最大緯度経度からなる `RouteBounds` を純粋 Kotlin で提供します。日付変更線を跨ぐ補正は日本国内向けの Phase 002 では行いません。同一座標の連続は有効です。

`RoutePointType` は `START`、`DESTINATION`、`STOP`、`VIA`、`SHAPING`、`REJOIN` を定義します。Phase 002 の地図では START / DESTINATION / STOP を色分けし、残りは domain に保持できますが表示しません。道路区間制約は地点とは別概念なので含めません。

## repository と sample

UI/state は Android/MapLibre 非依存の `ScheduledRouteRepository.getActiveRoute()` だけに依存します。Phase 002 の `InMemoryScheduledRouteRepository` は架空の東京都内座標を使う「開発用サンプルルート」を返します。これは実在する営業路線ではありません。

`MainActivity` は `BuildConfig.DEBUG` のときだけ sample を注入します。release variant は `activeRoute = null` となり、画面は「所定経路：未選択」、経路全体ボタン無効の状態で正常動作します。

## MapLibre 変換境界

domain package は `android.*` と `org.maplibre.*` を import しません。`RouteOverlayController` が geometry を GeoJSON LineString、point を type/name property 付き FeatureCollection へ変換します。

使用する ID:

- `busnav-scheduled-route-source`
- `busnav-scheduled-route-points-source`
- `busnav-scheduled-route-casing-layer`
- `busnav-scheduled-route-line-layer`
- `busnav-scheduled-route-start-layer`
- `busnav-scheduled-route-stop-layer`
- `busnav-scheduled-route-destination-layer`

controller は最新 route model を保持します。style load 完了 listener と初回 callback は、source/layer の存在を調べてから追加し、既存 source の GeoJSON を更新します。このため style reload 後も自車 overlay と所定経路 overlay を復元できます。

## 今後の拡張

経由地編集は順序付き `RoutePoint` を更新する上位機能として追加します。迂回経路、走行済み区間、道路セグメント制約は所定経路 source と別 ID / model で表現します。REJOIN は復帰候補地点として利用できますが、自動復帰、逸脱判定、map matching は Phase 002 の範囲外です。
