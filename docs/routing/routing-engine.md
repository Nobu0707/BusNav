# RoutingEngine

## 境界

`RoutingEngine.calculateRoute(RoutingRequest)` は Android、HTTP、Valhalla に依存しない suspend 境界である。成功は道路沿い geometry を持つ `ScheduledRoute` と `RoutingSummary`、失敗は UI が分類できる `RoutingFailure` を返す。coroutine の `CancellationException` は failure に変換せず再送出する。

`RoutingFailure` は `INVALID_REQUEST`、`SERVICE_UNAVAILABLE`、`NETWORK`、`TIMEOUT`、`NO_ROUTE`、`RATE_LIMITED`、`SERVER_ERROR`、`INVALID_RESPONSE`、`CONFIGURATION` を定義する。サーバーの本文や技術詳細は UI 文言へ渡さない。

## データフロー

```text
RoutePlan -> validation -> RoutingRequest -> RoutingEngine
  -> RoutingResult.Success(ScheduledRoute, RoutingSummary)
  -> candidate preview -> user confirmation -> active route
```

`RoutingRequest` は plan ID/name、順序付き地点、`VehicleProfile` だけを保持する。URL、JSON、Valhalla の location type/costing 名は adapter 内に閉じる。

## lifecycle

`RouteCalculationStateHolder` は `Idle`、`Calculating`、`Success`、`Failure` を管理する。新規探索は既存 job を cancel し、plan revision が変われば進行中探索を cancel する。完了結果の revision が現在値と異なる場合は candidate として適用できない。editor の破棄時も job を cancel する。

HTTP call は `suspendCancellableCoroutine` と OkHttp `Call.cancel()` を接続している。接続 timeout は10秒、読込 timeout は60秒とし、長距離探索を極端に短い制限で失敗させない。
