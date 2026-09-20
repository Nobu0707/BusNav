# Review 006A: Valhalla runtime response fix

## 1. 症状

Phase 004 の Android Emulator 実行で、短い経路が一度成功した後、別経路の `POST /route` が HTTP 200 を返したにもかかわらず `INVALID_RESPONSE`（「経路探索結果を読み取れませんでした」）になった。Android Studio Network Inspector の body は `Not available`、当時の Logcat は空だった。

## 2. BASE_SHA

`c91e9c4db54fcf1f753f94e092a6255f7ae68dc7`

全 review check と archive はこの SHA を `-BaseRef` に指定する。

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

接続device: `emulator-5554`（Pixel_8 AVD / Android 16）。

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
