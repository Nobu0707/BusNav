# RoutePlan domain

## 役割

`RoutePlan` は、利用者または運行管理者が「どこを、どの順で通りたいか」を表す編集入力です。経路探索済みの道路形状を表す `ScheduledRoute` とは別の model です。

```text
RoutePlan
  -> validation
  -> RoutingRequest
  -> RoutingEngine / Valhalla
  -> ScheduledRoute
```

RoutePlan の点列を直線で結ぶ表示は経路探索前の preview に限り、`ScheduledRoute.geometry` へ変換しません。Phase 004 は validation 後に RoutingEngine へ渡し、Valhalla response の geometry だけを候補 ScheduledRoute に使います。domain は Android SDK、MapLibre、Valhalla 固有 JSON のいずれにも依存しません。

## model

`RoutePlan` は `id`、任意の `name`、順序付き `points` を持つ immutable model です。points は構築時にコピーされます。編集途中を表せるため、空、START のみ、DESTINATION のみ、VIA/SHAPING のみも生成できます。

`RoutePlanPoint` は `id`、`type`、`GeoPoint position`、任意の `name` を持ちます。

| type | 意味 |
| --- | --- |
| START | 出発地。完成プランに 1 点 |
| DESTINATION | 到着地。完成プランに 1 点 |
| VIA | 必ず通る中間地点 |
| SHAPING | ルート形状を誘導する中間地点 |

STOP は運行地点であり `ScheduledRoute` 側、REJOIN は将来の迂回計画側の関心として RoutePlan には含めません。

## validation

`validateForRouting()` は model の生成可否ではなく routing request を作れるかを判定します。START/DESTINATION が各 1 点、START が先、全中間点が両端の間、point ID が一意であるとき `isRoutingReady=true` です。`GeoPoint` 自体が緯度経度の範囲と NaN を拒否します。

不完全・重複ID・順序不正は `RoutePlanValidationError` として返し、編集画面をクラッシュさせません。

## pure operations

`RoutePlanOperations` は元の plan を変更せず新しい plan を返します。

- START/DESTINATION の設定と置換
- VIA/SHAPING の追加
- point 削除
- 中間点内の位置を指定する並べ替え
- VIA/SHAPING 相互切替
- 中間点全消去、plan 全消去

START は先頭、DESTINATION は末尾へ正規化され、両端点の move と type change は無視されます。ID generator を注入でき、テストは決定的な ID を使用します。存在しない point 操作は no-op です。

## RoutingRequest 境界

`toRoutingRequest()` は validation 成功時だけ plan ID/name、元の順序とID/nameを保つ全地点、domain `VehicleProfile` を持つ MapLibre/Valhalla 非依存の `RoutingRequest` を返します。失敗時は validation result を返し、HTTP を呼びません。Valhalla の type 文字列、costing、JSON、URL は request に含めません。

`RoutePlanUiState.revision` は point の追加、削除、並べ替え、type切替、端点置換、clearで増加し、選択だけでは変化しません。探索結果は開始時 revision と一致するときだけ適用できます。
