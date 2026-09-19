# Review 006: Valhalla routing

## 1. Phase 004 概要

validated RoutePlan を大型車条件付き Valhalla `/route` へ送り、道路沿い geometry と距離・所要時間を candidate ScheduledRoute として確認後に active route へ反映する経路探索基盤を追加した。VICS、maneuver案内、逸脱、自動rerouteは対象外である。

## 2. BASE_SHA

`e5852a39e1cc65708eca31c2c1d55c4134b37b10`

全検査とarchiveはこのSHAを`-BaseRef`に指定する。

## 3. 最終HEAD

実装コミットと最終化コミットは本書末尾に記録する。最終化コミット自身のSHAは自己参照できないため、archiveの`meta/review-info.txt`を最終HEADの正とする。

## 4. 変更ファイル

- routing domain: `domain/routing/*`、`domain/routeplan/RoutingRequest.kt`
- Valhalla adapter: package `data.routing.valhalla` のmodels/client/decoder
- route: `RouteMetadata.kt`、route overlay
- state/UI: `ui/routing/*`、route plan/navigation stateとscreen
- Android/build: debug manifest、BuildConfig endpoint、OkHttp/serialization dependencies
- tests: VehicleProfile、polyline6、MockWebServer、state、Compose、navigation
- docs: README、architecture、domain/UI/routing文書、本書

## 5. RoutingEngine設計

`RoutingEngine`は`suspend calculateRoute(RoutingRequest): RoutingResult`だけを公開する純粋Kotlin境界である。Android、OkHttp、JSON、Valhalla typeをdomainへ漏らさない。実装はconstructor injection可能な`ValhallaRoutingEngine`である。

## 6. RoutingResult / failure model

SuccessはScheduledRouteとRoutingSummary、Failureは分類済みRoutingFailureを持つ。INVALID_REQUEST、SERVICE_UNAVAILABLE、NETWORK、TIMEOUT、NO_ROUTE、RATE_LIMITED、SERVER_ERROR、INVALID_RESPONSE、CONFIGURATIONをUIが区別できる。CancellationExceptionはFailureへ変換しない。

## 7. VehicleProfile

ID、名称、length/width/height/weight、任意axle loadを持つimmutable純粋Kotlin modelを追加し、全物理値をpositive validationする。開発用は12.0m x 2.5m x 3.5m、16.0t、軸重10.0tの仮値で、コード/UI/docsに「開発用車両条件」と実運行禁止を明記した。

## 8. truck costing採用理由

Phase 004は大型バスの高さ・幅・長さ・重量による物理通行可能性を優先する保守的初期方針として`truck`を採用した。

## 9. bus costingとの違い/制限

`bus`はbus/psv accessに適する一方、truckは大型車寸法重量制約を直接扱う。大型貨物車とバスのaccess規則は同一でないため、本来バスが通れる道路をtruckが過剰回避し得る。将来はbus accessとdimensional restrictionのhybrid strategyを検証する。

## 10. Valhalla endpoint設定

`busnavValhallaBaseUrl` Gradle propertyをBuildConfigへ渡す。debug既定値は`http://10.0.2.2:8002`、release既定値は空である。実機は`-PbusnavValhallaBaseUrl=http://<LAN-IP>:8002`で変更する。本番はHTTPSを要求する。

## 11. request JSON

`POST <baseUrl>/route`、JSON content typeでlocations、`costing=truck`、costing_options.truck、`units=kilometers`、`shape_format=polyline6`、`directions_type=none`を送る。unknownなoptionは送らない。

## 12. RoutePlanPoint mapping

入力順を保ち、START/DESTINATION=`break`、VIA=`via`、SHAPING=`through`へadapter内で変換する。ID/name/positionはScheduledRoute mapping用にdomain requestで保持する。

## 13. costing_options

height、width、length、weight、任意axle_loadをvehicle profileから設定する。高速バス向け初期値はuse_highways=0.8、use_living_streets=0.1、use_tracks=0.0、exclude_unpaved=true。ignore_restrictions/access/oneways/closuresは送らない。

## 14. polyline6 decoder

pure Kotlin decoderで緯度・経度deltaを6桁精度で復元する。負delta、複数点、invalid character、truncated、emptyを検出する。公式仕様どおり1e-6を使いpolyline5と混同しない。

## 15. response parser

必要な`trip.summary.length/time`と`trip.legs[].shape`だけをserialization model化し、unknown keyを無視する。全legをdecodeし、隣接legの同一境界座標を1点除去する。欠落、不正、最終2点未満はINVALID_RESPONSEとする。

## 16. ScheduledRoute mapping

route IDはplan IDと新規UUID、nameはplan nameまたは「計算ルート」。RoutePlanPointのID/name/position/typeをRoutePointへ保つ。geometryはValhalla shapeだけを使い、直線previewを流用しない。

## 17. distance/duration metadata

Valhalla length kmをmetersへ変換し、time secondsとともにRoutingSummaryおよびRouteMetadataへ保存する。routingSourceはoptional string `valhalla`である。

## 18. HTTP cancellation

OkHttp enqueueを`suspendCancellableCoroutine`へ橋渡しし、coroutine cancellation時に実Callをcancelする。CancellationExceptionは上位へ伝播する。

## 19. timeout

connect 10秒、read 60秒。長距離探索を考慮してreadを短くしすぎず、SocketTimeoutExceptionはTIMEOUTへ分類する。

## 20. RoutePlan revision/stale対策

plan内容変更でrevisionをincrementし、選択変更ではincrementしない。計算開始revisionをstateへ保存し、変更時は進行中job/HTTPをcancelする。旧Successはstale表示可能だがMap表示・適用は不可。旧Failureはclearする。

## 21. candidate route UI

validation ready時だけ探索buttonを有効化し、計算中indicator/文言とduplicate tap無効を実装した。成功時は距離km、推定h/m、道路沿いcandidate、適用buttonを表示し、candidate表示中は直線previewを隠す。

## 22. active route適用

current revisionのSuccessだけを`NavigationStateHolder.applyCalculatedRoute`でin-memory active routeへ反映してNavigationへ戻す。現在地follow状態は変更しない。repository永続化は対象外。

## 23. HTTP/MockWebServer tests

POST path/method/content type、location mapping/order/name、truckと全option、安全禁止flag不在、normal/multi-leg、不正shape/JSON、400 NoRoute、429、500、timeout、coroutine cancellationをlocal MockWebServerで検証した。public serviceには依存しない。

## 24. Unit tests

69件PASS。VehicleProfile正/負値、polyline6既知fixture/精度/不正、request/response/error/cancel、revision/state/retry、新規request cancel、既存Phase001-003 regressionを含む。

## 25. Compose tests

合計13件をAndroidTest APKへコンパイルした。invalid button、calculating、success summary/apply、failure/retryに加え、既存portrait/landscape/point操作をMapLibre/OpenGLなしで検証する。端末未接続のため実行はSKIP。

## 26. test結果

PASS。`gradlew test --console=plain`、69 tests、failures=0、errors=0。

## 27. lint結果

PASS。`gradlew lint --console=plain`。

## 28. assemble結果

PASS。`assembleDebug`と`assembleDebugAndroidTest`。

## 29. connected test

SKIP。`adb devices`は正常終了し、接続deviceは0台だった。

## 30. manual integration

SKIP。`http://localhost:8002/status`は2秒でtimeoutし、local Valhalla serverは利用できなかった。実サービスの代わりにMockWebServerでHTTP契約を検証した。巨大OSMデータの取得は行っていない。

## 31. 既知の制限

- server/tileの構築、health管理はアプリ外
- vehicle profileは仮値1件で設定UI/永続化なし
- candidate/active反映はin-memory
- maneuver/turn-by-turn、VICS、map matching、逸脱、自動rerouteなし
- error codeは既知NoRoute code/messageとHTTP statusによる保守的分類

## 32. safety limitations

truck costingはbus accessの完全代替ではない。OSM/tilesの制限値欠落や誤り、server version差、snap先の制約確認などにより安全を保証できない。開発用profileを実運行に使わない。cleartextはdebug local接続だけで、releaseは許可しない。server bodyはUIへ表示しない。

## 33. Phase 005への推奨

response modelへValhalla maneuversを段階追加し、道路名、次の右左折、距離、route進捗をdomainへ変換する。truck/bus hybrid costingをserver versionと日本の実路線fixtureで評価し、実車profile管理と運行承認フローを先に整備する。

## 34. commit SHA

- 実装コミット: 最終`git log`参照
- Review 006最終化コミット: archive metadata参照

## 35. review archive名

- lightweight alias: `busnav-review-latest.zip`
- full alias: `busnav-full-review-latest.zip`
- immutable名、HEAD、BASE_SHA、self-checkは各archiveの`meta/review-info.txt`と`meta/archive-self-check.txt`を正とする。
