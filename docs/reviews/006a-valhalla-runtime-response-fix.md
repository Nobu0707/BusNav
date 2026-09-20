# Review 006A: Valhalla runtime response fix

## 1. 症状

Phase 004 の Android Emulator 実行で、短い経路が一度成功した後、別経路の `POST /route` が HTTP 200 を返したにもかかわらず `INVALID_RESPONSE`（「経路探索結果を読み取れませんでした」）になった。Android Studio Network Inspector の body は `Not available`、当時の Logcat は空だった。

## 2. BASE_SHA

`c91e9c4db54fcf1f753f94e092a6255f7ae68dc7`

これは初回Phase 004.1 reviewのbaseである。follow-up finalizationのbaseは
`087b7c8f5f88c313a038f3239a1511dd85d5bba9` とし、最終review checkとarchiveは後者を `-BaseRef` に指定する。

## 3. 実 Valhalla 環境

- WSL2 Ubuntu / Docker
- `ghcr.io/valhalla/valhalla-scripted`
- Valhalla `3.9.0-a3a5631c4`
- Chubu Geofabrik extract
- host: `http://localhost:8002`
- Emulator: `http://10.0.2.2:8002`

`/status` は HTTP 200 で version と route action を返した。public service、OSM 再download、tile再構築は行っていない。

## 4. failing route summary

- START: `35.52755965924169, 138.79653353327427`
- DESTINATION: `35.609542457517534, 138.29084069799353`
- costing: truck
- height/width/length/weight/axle load: `3.5 / 2.5 / 12.0 / 16.0 / 10.0`
- `use_highways=0.8`
- `use_living_streets=0.1`
- `use_tracks=0.0`
- `exclude_unpaved=true`
- `shape_format=polyline6`
- `directions_type=none`

## 5. curl で server 正常確認

同一requestを localhost Valhallaへ10回連続送信し、全回で以下を確認した。

- HTTP 200
- body: 12,972 bytes
- length: 88.881 km
- time: 7,188.89 sec
- legs: 1
- encoded shape: 12,208 characters

## 6. Android HTTP 200 確認

Android instrumentation smokeで同一requestを3回連続実行した。全回で status 200、bodyLength/bodyByteLength/contentLength/headerContentLength はすべて 12,972、SHA-256 は `7e2e27848c163740abe20444969045f2b5b08367be565f4d2ad9ffc1586adcd0` だった。

Network Inspector の `Not available` は body empty の根拠にせず、engine自身の値を正とした。

## 7. コードレビュー結果

変更前engineはResponseBodyを1回だけ `string()` 化しており、二重readはなかった。CancellationException、timeout、IOExceptionの分類も妥当だった。

一方、JSON decode、field欠落、polyline、geometry、summary、RoutePoint/ScheduledRoute invariant、その他unexpected exceptionのすべてが、stage・class・message・stack traceを残さず `INVALID_RESPONSE` に合流していた。

## 8. 再現方法と結果

`/tmp/busnav-current.json` に残っていた実response（12,972 bytes）を、変更前相当のengineへMockWebServer経由で直接渡した。このbodyは `RoutingResult.Success` になり、88,881 m、7,188.89 sec、3,075 geometry pointsを生成した。

したがって保存済みbodyからは元の `INVALID_RESPONSE` を再現できず、長いJSON、12,208文字polyline、GeoPoint、RouteGeometry、ScheduledRouteのいずれにも、このfixture固有の失敗は確認されなかった。

## 9. Root cause

確認できたコードレベルの根本欠陥は、`ValhallaRoutingEngine.calculateRoute` の無診断catch-allと、`parseSuccess` 内の多数のearly returnが、異なる失敗原因を同一の `INVALID_RESPONSE` へ不可逆に潰していたことである。これにより当時の末端例外は保存されず、後から特定できない状態になった。

重要な限定: 保存済み実responseは変更前相当コード、分離後parser、Android runtimeのすべてで成功した。このため、元の一過性事象を「polyline decoder不良」「12KB body処理不良」などと断定する証拠はない。当時の末端triggerは既存artifactから復元不能であり、推測をroot causeとして記録しない。

## 10. Existing tests が見逃した理由

既存success testは数十文字のsynthetic polylineだけだった。実Valhalla 3.9 response、12KB body、12,208文字shape、同一engineの連続呼び出し、短長交互response、stage別diagnosticsを検査していなかった。またcatch-allの戻り値だけをassertしており、例外情報が失われる設計自体を検出できなかった。

## 11. 修正内容

- HTTP処理から `ValhallaRouteResponseParser` を分離
- response bodyは従来どおり1回だけString化し、同じStringをparserへ渡す
- `ValhallaResponseStage` と `ValhallaResponseException` を追加
- expected invalid responseとunexpected exceptionを別eventで記録
- CancellationException rethrow、TIMEOUT、NETWORK、HTTP分類を維持
- UI公開failureは互換性のため `INVALID_RESPONSE` のまま
- Phase 005 maneuver機能は追加せず、`directions_type=none` を維持

実response自体にparser defectを確認できなかったため、証拠のないpolyline/JSON変換変更は行っていない。

## 12. Diagnostics 設計

`RoutingDiagnostics` をconstructor injectionし、`NoOpRoutingDiagnostics` と `AndroidLogRoutingDiagnostics` を追加した。MainActivityはDebugだけ `BusNavValhalla` tagへ出し、ReleaseはNoOpである。

記録対象:

- request: plan id、point count/ids/types、duplicate数、START/DESTINATION数、endpoint host/port
- response: status、文字数、UTF-8 byte数、content type、body/header content length、SHA-256
- parse: JSON、trip、summary、legs、shape長、decoded point数、geometry point数、route構築
- error: stage、exception class/message、stack trace

raw JSON、encoded full shape、全route coordinatesはログしない。

## 13. Parser 分離

`String body + RoutingRequest -> RoutingResult.Success` をpure JVMで直接呼べるようにした。失敗時は EMPTY_BODY、JSON_DECODE、TRIP、SUMMARY、LEGS、SHAPE、POLYLINE、GEOMETRY、ROUTE_CONSTRUCTION のstage付き例外を返す。

既存ファイルのdirectory/package不一致は、Phase 004.1で不要な大量moveを避けるため今回整理していない。

## 14. Long response fixture

`app/src/test/resources/valhalla/route-88km-valhalla-3.9.0.json` にlocalhostのfull live responseを保存した。minimizeはしていない。サイズは12,972 bytesで、credential、API key、Authorization、token、secretは含まれない。

parser test結果:

- distance: 88,881 m
- duration: 7,188.89 sec
- geometry: 3,075 points
- START: 1
- DESTINATION: 1
- 全座標valid

## 15. Long polyline test

実fixtureの12,208文字shapeを2回decodeし、両回3,075点で完全一致した。全緯度経度がdomain範囲内であることを確認した。

## 16. Repeated request test

同一MockWebServer・同一ValhallaRoutingEngineでlong responseを12回連続処理し、全回Success。short/long/short/long/short/longの交互6回も全回Success。ResponseBody lifecycle、keep-alive経路、engine state leakの回帰を固定した。

localhost Valhallaへのdirect smokeも10/10回同一結果だった。

## 17. UI state regression

追加したstate test:

- Success -> same plan Calculate again -> Success
- short Success -> long Success -> same long Success
- 既存 Failure -> Retry -> Success
- 既存 cancellation/revision stale guard

すべてPASS。

## 18. Emulator manual / runtime test

接続device: `[device-id-redacted]`（Pixel_8 AVD / Android 16）。

`ValhallaRuntimeSmokeTest` をGradle connected testとして単独実行しPASS。同一engine、同一失敗routeで3/3回Success、各回3,075点を構築した。

UIでmap tapから同一座標を再入力してcandidate表示まで確認する手動操作は自動化できず未確認。engine/stateのcandidate生成経路はJVM/Android testで確認済みだが、これはremaining limitationとする。

## 19. Logcat 確認

3回すべてで次のevent列を確認した。

`request.start -> response.received -> parse.json -> parse.trip -> parse.summary -> parse.legs -> parse.shape -> parse.polyline -> parse.geometry -> parse.route`

各回、status 200、body 12,972、shape 12,208、decoded/geometry 3,075、routePointCount 2。raw body/coordinatesは出力されなかった。

## 20. Test 結果

`gradlew test --console=plain`: PASS、81 tests、failure 0。

対象には実fixture、long polyline、12回連続、短長交互、empty/malformed/missing field、route invariant、cancellation、timeout、HTTP error、retry、revision guardを含む。

## 21. lint 結果

`gradlew lint --rerun-tasks --console=plain`: PASS。

local.propertiesのWindows drive colonをproperties準拠の `C\:/...` に修正した。このファイルはignoredでcommit/archive対象外。

## 22. build 結果

- `assembleDebug`: PASS
- `assembleDebugAndroidTest`: PASS

## 23. connected test

- ValhallaRuntimeSmokeTest単独: PASS（1 test内で3連続route）
- 全connected suite: PASS、14 tests、failure 0

初回全suiteでは、端末viewportより大きいsynthetic adaptive layoutの画面外nodeに `assertIsDisplayed` を要求した既存3箇所が失敗した。該当nodeはadaptive branchの存在検査へ変更し、viewport内の主要領域は従来どおり可視性をassertした。production UIは変更していない。

## 24. line ending / working tree

開始時のtracked差分は0で、未追跡の `busnav-logcat.txt` と `gradle/gradle-daemon-jvm.properties` があった。前者は削除せず `busnav-logcat*.txt` をignoreした。後者も変更・commitしていない。

repo全体の正規化、`git add -A`、reset/checkout/cleanは行っていない。対象ファイルのみ明示的にstageし、`git diff --cached --ignore-space-at-eol` と `git diff --cached --check` を確認した。

## 25. 既知の制限・commit・archive

- 原事象の末端例外は旧catch-allにより失われ、保存済み実bodyでは再現しない
- candidate routeのUI手動表示は未確認
- package path整理は見送り
- Phase 005 maneuverは未実装

commit:

- `c0fb0403587bfeed793bedfd4679e07771aee7d4` — `fix: harden Valhalla runtime response handling`
- Review006A最終化commit: archive metadata参照

archive:

- lightweight alias: `busnav-review-latest.zip`
- full alias: `busnav-full-review-latest.zip`
- immutable名、final HEAD、BASE_SHA、self-checkは各archiveの `meta/review-info.txt` と `meta/archive-self-check.txt` を正とする

## 26. ChatGPT follow-up review findings

follow-up reviewでは、初回hardeningで未完了だった次の3点を確認した。

- `NoOpRoutingDiagnostics` でも呼び出し前にSHA-256、UTF-8 byte配列、詳細文字列、point ID/type集約が生成されていた
- `await()` 後のbody read、fingerprint、JSON/polyline decode、domain mappingが呼び出し元dispatcherへ戻り、Main threadで実行される余地があった
- request生成/HTTP前の例外にも `response.unexpected` が使われ、event名だけでは失敗stageを判別できなかった

加えて、元操作に近い短距離/長距離切替のActivity UI連続探索が未確認だったため、実Emulator上の回帰を追加した。

## 27. Original-trigger status

- reproduced: **no**
- exact root cause of the original transient `INVALID_RESPONSE`: **still unknown**

観測性改善後、短距離/長距離を切り替える実Activity UI経路で7回連続成功し、元の「経路探索結果を読み取れませんでした」は0回だった。したがって、今回の結果からpolyline、OkHttp、state raceなどを元triggerとして断定しない。

## 28. Confirmed defect

確認済みの欠陥は、unexpected parser/domain exceptionが詳細なしで `RoutingFailure.INVALID_RESPONSE` に潰され、当時の末端原因を追跡不能にしていたこと、およびRelease NoOpでも高コストdebug detailsがeager評価され得たこと、重いresponse処理のdispatcher境界が明示されていなかったことである。

初回hardeningでparser分離とstage diagnosticsを導入し、follow-up commit `cdb9ccf` でdiagnostics負荷とdispatcher/event境界を完成させた。

## 29. Release diagnostics optimization

`RoutingDiagnostics` に `isDebugEnabled` / `isErrorEnabled` とlazy details lambdaを導入した。

- `NoOpRoutingDiagnostics` はsupplierを評価しない
- Android loggerは対象levelが有効な場合だけsupplierを評価する
- SHA-256、UTF-8 byte化、request pointの`map`/`joinToString`、debug details生成はlambda内に移した
- raw JSON、full encoded shape、全geometry coordinateは引き続き記録しない

JVM testでNoOp時のsupplier非評価とRecording時の評価・保存を固定した。

## 30. Dispatcher/threading fix

inject可能な `RoutingDispatchers` を追加した。

- request JSON encode: computation dispatcher
- `ResponseBody.string()`: IO dispatcher
- response fingerprint、HTTP error JSON decode、Valhalla JSON decode、polyline decode、GeoPoint生成、RouteGeometry/ScheduledRoute構築: computation dispatcher
- `CancellationException`: rethrow
- OkHttp call cancellation: 従来どおり `invokeOnCancellation { cancel() }`

dispatcher testでは、長い実fixtureの全 `parse.*` eventが注入した `routing-computation-test` threadで実行された。実Emulator Logcatでも `request.start` はmain thread、`response.received` 以降はworker threadであった。

## 31. Diagnostics event naming cleanup

engine eventをstageに合わせて整理した。

- `request.start`
- `request.encode.failed`
- `http.request.cancelled`
- `http.request.failed`
- `response.received`
- `response.read.failed`
- `response.parse.failed`
- `route.construction.failed`
- `route.success`
- `unexpected`（detailsにstageを付与）

malformed JSON/polyline/empty bodyは `response.parse.failed` とparser stage、route invariantは `route.construction.failed` と `ROUTE_CONSTRUCTION` を記録するtestを追加した。

## 32. UI state regression tests

既存の次の回帰testを維持し、全件PASSした。

- Success -> same plan calculate again -> Success
- short Success -> long Success -> same long Success
- Failure -> Retry -> Success
- plan revision変更後のstale candidate拒否
- 新requestによるprevious job cancellation

engine側の12回long連続、short/long交互6回、実fixture、12,208文字polylineも維持し、全件PASSした。

## 33. Compose test assertion review

commit `087b7c8` の `assertIsDisplayed()` から `fetchSemanticsNode()` への変更4箇所を再査読した。対象はsynthetic `requiredSize` が実端末viewportを超えると画面外になるadaptive branch/containerであり、存在検査が目的である。画面内のmap、screen、complete、preview等は引き続き `assertIsDisplayed()` を使う。

したがって今回これらを不安定な可視性assertへ戻していない。新しいActivity UI smokeではcalculate button、result card、summary、candidate labelに `assertIsDisplayed()` を使用し、実viewport上の検証強度を追加した。

## 34. Emulator Activity UI smoke

- Emulator: `Pixel_8` AVD / Android 16 / API 36 / `[device-id-redacted]`
- APK: debug
- endpoint: `http://10.0.2.2:8002`
- Valhalla: `3.9.0-a3a5631c4`
- 実行時刻: 2026-09-20 14:00 JST
- sequence: short -> short -> long -> long -> long -> short -> long
- success: 7/7
- failure: 0/7
- original `INVALID_RESPONSE`: 0

short:

- START `35.24010, 138.61081`
- DESTINATION `35.25416, 138.83650`
- UI summary `29.6 km / 35分`

long:

- START `35.52755965924169, 138.79653353327427`
- DESTINATION `35.609542457517534, 138.29084069799353`
- UI summary `88.9 km / 1時間59分`

`ValhallaUiRuntimeSmokeTest` は実 `MainActivity` と同じ `RoutePlanEditorViewModel` に正確な座標を設定し、画面上のcalculate buttonを7回操作した。各回、result card、summary、candidate map label「探索結果（道路沿いルート）」の実表示をassertした。Map pixel long-pressの座標丸めを避けるため、地点座標の投入だけはViewModel経由である。探索、再探索、state遷移、candidate routeのMap連携、summary表示はproduction Activity UI経路を通る。

Windows computer-use runtimeは環境ACLエラーで起動不能だったため、ADBで実端末画面のroute editor起動とUI hierarchyも確認した。これはroot triggerに関する証拠を誇張せず、再現可能なinstrumentation UI smokeを正とする。

## 35. Logcat result

7回smoke直前にLogcatをclearし、`BusNavValhalla` DEBUGを有効化した。結果:

- `request.start`: 7
- `response.received`: 7
- `parse.json success=true`: 7
- `parse.polyline`: 7
- `route.success`: 7
- error/unexpected event: 0
- long response: 4回すべて body 12,972、shape 12,208、geometry 3,075
- short response: 3回すべて body 3,683、shape 2,924、geometry 694

full connected suiteではruntime engine smoke 3回も含め、request 10、route success 10、BusNavValhalla error event 0だった。ログファイルはrepoへ保存・commitしていない。

## 36. Final verification and remaining limitations

- `gradlew test --console=plain`: PASS、85 tests、failure 0
- `gradlew lint --console=plain`: PASS
- `gradlew assembleDebug --console=plain`: PASS
- `gradlew assembleDebugAndroidTest --console=plain`: PASS
- `gradlew connectedDebugAndroidTest --console=plain`: PASS、15 tests、failure 0
- long Valhalla 3.9 fixture / 12k+ polyline / repeated / alternating: PASS
- lazy diagnostics disabled/enabled: PASS
- injected computation dispatcher: PASS

remaining limitations:

- 原事象発生時の末端例外は旧catch-allで失われており、exact original triggerは不明のまま
- UI smokeの正確な座標投入はMap pixel long-pressではなくActivityのViewModel経由
- route lineはproduction MapControllerへ渡る実経路を通したが、pixel image comparisonは行っていない
- package path整理はPhase 004.1のscope外
- Phase 005 maneuver/navigation guidanceは未実装
- 未追跡の自動生成 `gradle/gradle-daemon-jvm.properties` は本修正に不要なため、削除もcommitもしていない

## 37. Follow-up: live smoke isolation

### ChatGPT final review finding

最終査読で、外部のlocal Valhallaへ依存する `ValhallaUiRuntimeSmokeTest` が通常の `connectedDebugAndroidTest` に無条件で含まれ、Valhalla停止中でもBusNav本体と無関係に失敗し得る点と、実OSM由来の距離・時間を完全一致で契約にしていた点が指摘された。

### Availability guard

androidTest共通utility `LocalValhallaAssumptions` を追加した。`http://10.0.2.2:8002/status` をcall/connect/read各2秒timeoutでGETし、2xx以外または通信例外の場合はJUnit4 `Assume.assumeTrue` でtest本体をskipする。既存 `ValhallaRuntimeSmokeTest` の独自probeも同utilityへ統合し、HTTP availability判定を重複させていない。

通常の `connectedDebugAndroidTest` では次の方針とする。

- Valhallaあり: engine/UI runtime smokeを実行する
- Valhallaなし: engine/UI runtime smokeをassumption skipし、suiteはPASS可能
- Unit/fixture tests: external Valhalla不要かつdeterministicで、parser/regressionの主検証を担う
- Live runtime smoke: environment integrationを確認し、exact OSM resultを契約にしない

### Live summary assertion stabilization

UI smokeの表示完全一致 `29.6 km / 35分`、`88.9 km / 1時間59分` を廃止し、画面上のsummary文字列を解析して次を検証する形へ変更した。

- short route: 10 km以上60 km以下
- long route: 50 km以上150 km以下
- duration: 0分より大きい
- short distance < long distance
- result card、非空summary、candidate label「探索結果（道路沿いルート）」を表示
- failure text「経路探索結果を読み取れませんでした」を表示しない
- sequence `short -> short -> long -> long -> long -> short -> long` とretry/repeated routeを維持

engine live smokeもdistanceを50–150 km、durationを正値として検証する。一方、実Valhalla 3.9 response fixtureのunit testは `88,881 m`、`7,188.89 sec` の厳密assertを維持する。

### Valhalla OFF verification

- Emulator: `[device-id-redacted]`、Android 16
- `busnav-valhalla` containerを停止し、endpoint unreachableを確認
- `gradlew connectedDebugAndroidTest --console=plain`: **BUILD SUCCESSFUL**
- 通常UI instrumentation: 13/13 PASS
- live runtime smoke: 2/2 assumption skip
- AndroidJUnitRunner生出力: `INSTRUMENTATION_STATUS_CODE: -4` が2件、最終結果 `OK (2 tests)`

AndroidJUnitRunnerではstatus `-4` がassumption skipを表す。現行AGP/UTPの生成XML/HTMLはこの `-4` を `failure` 欄へ変換する表示上の制約があり `skipped=0` と出るが、Gradle taskは正しく成功する。skip判定と件数はrunner生出力で確認した。

### Valhalla ON verification

- `busnav-valhalla` を再起動
- `/status`: HTTP success、Valhalla `3.9.0-a3a5631c4`
- `gradlew connectedDebugAndroidTest --console=plain`: **BUILD SUCCESSFUL**
- suite: 15/15 PASS、failure 0
- `ValhallaRuntimeSmokeTest`: 1/1 PASS（同一engineでroute 3/3成功）
- `ValhallaUiRuntimeSmokeTest`: 1/1 PASS（画面上のroute操作 7/7成功）

### Regression and build verification

- `gradlew test --console=plain`: PASS、85 tests、failure 0
- `gradlew lint --console=plain`: PASS
- `gradlew assembleDebug --console=plain`: PASS
- `gradlew assembleDebugAndroidTest --console=plain`: PASS
- long fixture、12,208文字polyline、repeated same engine、short/long alternating、malformed JSON/polyline、route construction diagnostics、lazy diagnostics、dispatcher、cancellation、timeout/network classification、retry、stale revision guardを維持

### Remaining limitations

- 原事象のexact transient triggerは、旧catch-allで末端例外が失われていたため不明のまま
- UI smokeの地点投入はMap pixel long-pressではなく、実Activityが共有するViewModel経由
- route lineはproduction MapController連携まで確認したが、OpenGL pixel image comparisonは未実施
- AGP/UTPのHTML/XMLではAndroidJUnitRunner assumption status `-4` がfailure欄に表示される
- 未追跡の自動生成 `gradle/gradle-daemon-jvm.properties` は削除もcommitもしていない
- Phase 005 maneuver/navigation guidanceは未着手

## 38. Final conclusion

confirmed defectの修正、live smokeの外部依存隔離、実OSM値への過度な依存除去、Valhalla OFF/ON双方のconnected suite、全regression/build gateを確認した。

**Phase 004.1: COMPLETE**
