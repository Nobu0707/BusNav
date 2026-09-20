# Review010 — Phase008 Route-constrained Map Matching / 所定経路逸脱検知

## 1. 概要とBASE_SHA

開始時に実repoのHEADを取得した。BASE_SHAは
`292804d7545130d9e86c492d8f9ae59f159af1d3`。
Phase008は所定経路へのローカルmatching、持続する逸脱の警告、元経路への復帰確認を実装する。
自動reroute、detour/rejoin route生成は追加していない。

初期の未追跡 `.vscode/` と `gradle/gradle-daemon-jvm.properties` は保持し、commit対象から除外。
標準sandboxのWindowsプロセス起動がACL設定エラーになったため、読み書き・Gradle・adbは
許可された実行経路でWindows側から実施。ソース、OSM、graph、basemapの外部uploadは行っていない。

## 2. Phase006 / Phase004.5.1 / Phase007

Phase006の入力査読結果はPASS/COMPLETEとして受領。25/37/38 merge enum、
highway decision、距離軸、案内抑制、模式図、近接分岐繰上げ、no-auto-rerouteを維持。
既存Phase005/006のunit・live instrumentationを新pipelineで回帰検証した。

Phase004.5.1のJapan Valhalla / Kanto・Chubu basemapもPASS/COMPLETEとして受領。
既存Valhalla 3.9.0、日本全域graph、TileServer GL、地域style、Developer Connectionsを利用した。
Japan / Kanto / Chubu PBF、graph、MBTilesのdownload・再生成、旧Chubu rollback資材削除はない。

Phase007の詳細地図基盤はPhase004.5/004.5.1で前倒し済み。style/tilesの重複実装はしない。
今回の変更に地図dataやserver configは含まれない。

## 3. 既存実装監査

| 対象 | 所見 / Phase008の扱い |
| --- | --- |
| RouteProjector | 全segment最近傍、hintはtie-break。純粋projection APIとして維持、共通projectSegmentを抽出 |
| RouteDistanceIndex | route geometryと同一instanceの唯一の累積距離軸。matcherとmaneuver/highwayで共有 |
| NavigationProgress | matched projectionを受け取る既存APIを再利用しreliabilityを上書き |
| NavigationProgressTracker | legacy APIは維持。productionでは使わず二重jitter補正を回避 |
| NavigationStateHolder | 以前のjob cancel + equality guardに世代guardと純粋状態遷移を追加 |
| HighwayGuidance | 非RELIABLE時のsign/方向/距離/模式図抑制を再利用 |
| LocationState / AndroidLocationProvider | 既存timestampはwall-clock。elapsedRealtimeMillisを追加 |
| ScheduledRoute | geometry/guidance一体のsnapshotを維持 |
| MapController / overlay | raw GPS markerと所定経路lineを維持。matchingから地図への吸着なし |
| GuidanceCard / HighwayGuidanceCard | 既存formatterを使い、bannerは縦上部/横左列へ追加 |
| Developer Connections | 保存・地域切替・hot reload・release safetyを維持 |

## 4. Matcher architecture / candidate index

Android/Compose非依存のRouteMatcher、RouteMatchIndex、RouteDeviationDetector。
immutable previous stateから次状態を返す。毎fixのネットワーク依存はない。
index構築と候補評価はDispatchers.Default、UIへのcommitはMain側scope。
RouteMatchはprojection、quality、heuristic confidence、heading difference、candidate gap、評価候補数を保持。

0.01度gridがsegment bounding regionを索引化。端点・bearing・開始距離・長さをroute適用時にcache。
350m近傍セルと前後12segmentを評価し、空候補/遠方では全体検索。
256セルを超える長いsegmentは共通候補へ退避。longitude wrap対応。
previous window以外の近傍branchも検索し、lost後の再捕捉を局所windowで妨げない。
通常の4,001点経路で候補数600未満をassert。
病的に重なる/長いsegment群ではO(N) fallbackの可能性がある。

## 5. Scoring / heading / continuity / speed

scoreはdistance × 1 + heading × 4 + continuity × 0.25 + jump × 4。
distanceはcross-track / max(accuracy,8m)。
headingは最小角差/180、speed>=2.5m/sかつfinite bearing時だけ有効。
359/1度=2度、350/10度=20度、NaN/null/低速でheading無効をunit固定。

進行差の絶対値から15m jitter許容量を引き、移動可能距離との比でcontinuity/jumpを評価。
allowance=speed × monotonic dt × 1.5 +45m、speed不明なら45m/s。
単発jumpの結果はUNRELIABLEでanchor/progressを更新しない。
小さな後退はmatcherだけが保持し、実際の後退は永遠に固定しない。
near reliable anchorを更新できないfixが3回続くと次回は旧anchor制約を解除して再捕捉する。

confidenceは相対heuristicであり、確率ではない。accuracyも真のσと仮定しない。
独立候補のgap<0.35はAMBIGUOUS。同じprogress付近20m以内の隣接投影は同一仮説とする。
accuracy不明/非finite/負/40m超、移動不可能、cross-track300m超はUNRELIABLE。
MATCHEDは候補対応のqualityで、ON_ROUTEとは別の概念。

## 6. 単調時刻 / stale / out-of-order / concurrency

Android Location.elapsedRealtimeNanosをms化。production時計はSystemClock.elapsedRealtime。
wall-clock timestampは継続時間や速度計算に使わない。
欠損/負/未来/10秒以上古いfixは採用しない。古い/重複fixはraw markerも証拠回数も巻き戻さない。
新fixが来なくてもwatchdogが10秒で案内を抑制する。
証拠の間隔が10秒以上空いた場合も逸脱連続回数・時間をリセットする。

job cancelだけに依存せず、計算世代・route準備世代・route identity・location identity・完了時鮮度を確認。
計算workerは共有trackerを直接変更しない。
queued dispatcherで古い計算が後から完了するケース、route差替え、同じrouteの準備中再適用をunit検証。
同じrouteの再適用で準備世代だけを進める不具合を最終差分監査で発見・修正し、専用回帰テストを追加した。

## 7. Deviation state machine / thresholds / accuracy

| 設定 | 既定値 |
| --- | --- |
| ON_ROUTE距離 | <=25m |
| SUSPECTED距離 | >=35m |
| 強い逸脱証拠 | cross-track - accuracy >=50m |
| 確定 | 3 fix以上 + 3秒以上 |
| 復帰 | 信頼できる25m以内3 fix以上 + 2秒以上 |
| 連続証拠の最大間隔 | 10秒未満 |

UNKNOWN / ON_ROUTE / SUSPECTED_OFF_ROUTE / OFF_ROUTE / RECOVERINGを実装。
単発100m spikeはSUSPECTED、次の良好fixでON_ROUTE。
持続70m差・5m精度でSUSPECTED→OFF_ROUTE、復帰後RECOVERING→ON_ROUTE。
OFF_ROUTE後の精度不良は確定済み警告を保持しつつUNRELIABLE、証拠回数0。
確定済み逸脱と、単なる精度低下からの復帰待ちを別フラグで管理。
未確定の復帰中断だけではOFF_ROUTEにしない。精度不良やAMBIGUOUSは新たな逸脱確定を作らない。

## 8. Hard casesとtrace replay

| ケース | 検証結果 |
| --- | --- |
| 直線 + small noise / stationary jitter | 距離軸とON_ROUTE保持、小後退だけ補正 |
| curve | segmentをまたいで同じ累積距離軸上を進行 |
| self-crossing | headingまたは履歴でbranch選択、情報不足の同点はAMBIGUOUS |
| parallel road | 同方向の持続70m差でOFF_ROUTE。15m差は誤差と識別不能な限界としてON_ROUTE維持 |
| opposite carriageway | 距離だけなら近い逆方向候補より、headingとcontinuityを優先するfixtureを確認 |
| GPS spike | 単発100m横ずれで確定なし、数km縦jumpでanchor不変 |
| sustained deviation / recovery | state sequenceとfix番号、quality、segment、progressをtable-driven assert |
| poor accuracy | null/NaN/負/infinite/80/100mでUNRELIABLE、確定禁止 |
| stale gap / no new fix | evidence resetとwatchdog抑制 |
| out-of-order / wall-clock変更 | 状態巻戻しなし、単調時刻の結果不変 |
| zero-length / dateline | crashなし、bearing欠損とwrapped projectionを確認 |
| route replacement | 全cache/履歴reset、新routeの古いsegment再利用なし |

## 9. General / highway / map / no reroute

MATCHEDかつON_ROUTEだけ強い案内を表示する。それ以外は一般道の方向・距離・次案内、
高速のsign・方向・距離・模式図を抑制。復帰確認完了で再表示。
OFF_ROUTE bannerは「所定経路から外れている可能性があります」。
SUSPECTED/RECOVERING/UNKNOWNは確認中表示。色と文字を併用しpolite live regionを設定。
縦画面上部と横画面左列で表示し、操作を要求しない。

raw GPS markerのroute吸着なし、OFF_ROUTEでもroute lineを保持。
実機画像で側道側のsynthetic markerと高速本線のroute lineが離れて見えることを確認。
route geometryから作ったsynthetic fixesだけを使い、実走行も個人GPS採取もしていない。

unitとlive UIでRoutingEngine call countを検証。
逸脱・精度悪化・復帰・回転で増加せず、明示的Route Editor再計算のみ増える。
matcher/detectorにRoutingEngine/HTTP/Valhalla依存はない。

## 10. Diagnostics / privacy / performance

debug ViewModelのみ、quality/state変更時に固定のevent名を記録。
座標、segment番号、score、GPS履歴、device serialはログに含めない。
releaseのevent出力は無効。debug injectionはandroidTestだけでproduction GPSへhackしない。
matched point overlayや運転画面への内部diagnostic panelは今回は追加していない。

Windows JVM、4,001点route、1,000 updateのサンプル平均 **約0.22ms/update**。
計測はRouteMatcherTestのsystem-outに記録。絶対性能保証ではない。
実道路route、Android負荷、病的geometryでは変動する。

## 11. Unit / instrumentation / build

JUnit XMLから集計。推測値ではない。

| 対象 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit全体 | 211 | 0 | 0 | 0 |
| 新規unit（31 domain + 8 holder） | 39 | 0 | 0 | 0 |
| Emulator Android16 全suite | 39 | 0 | 0 | 0 |
| Physical SOG06 / Android14 全suite | 39 | 0 | 0 | 0 |
| 高速区間へ調整後のphysical matcher focused smoke | 2 | 0 | 0 | 0 |

元unit172件も維持。新規instrumentationはMatchingRuntimeSmokeTestの2件。
既存Guidance/Highway runtime smokeは毎秒のsynthetic fixを継続し、撮影中のstaleを防ぐ形へ更新。
端末が複数接続されていたためWindows adb.exeで検出し、ANDROID_SERIALで毎回対象を限定。
実機serialやLAN endpointは本書・sourceへ記録していない。

Windows Gradleのtest、lint、assembleDebug、assembleRelease、assembleDebugAndroidTestはPASS。
lintはerror 0、warning 11（依存更新提案、既存画面サイズAPI、LogNotTimber、UseTomlInstead）。
Java/protobuf Unsafe deprecation warningは失敗ではない。依存バージョン変更は本タスク外。

## 12. Emulator / physical / live environment regression

既存Japan ValhallaとTileServer GLをそのまま使用。Windows/WSLの8002 status、8080 Kanto styleがHTTP200。
実機は保存済みDeveloper接続設定を読み取り、Gradle reinstallに備えローカルbuild配下へ一時保持し、
test runner runtime引数で同じ接続先を使用した。実LAN IPのhardcodeなし。

関東の公共道路（東京→埼玉）をValhallaで計算。
新smokeは高速入口を通過した後のgeometry上の点と100m synthetic offsetを選び、
KANTO styleでON_ROUTE→SUSPECTED→OFF_ROUTE→RECOVERING→ON_ROUTEを確認。
同じfixtureで一般/高速抑制、Activity recreation、縦横、endpoint設定維持、no-rerouteをassert。
synthetic general-roadの独立fixtureでも同じ状態sequenceを確認。

| 回帰対象 | 結果 |
| --- | --- |
| Japan Valhalla / Phase004.1 | 既存実routeを繰り返し計算、parse・maneuver index・長距離PASS |
| Kanto local / Tokyo-Saitama | UI route candidate PASS |
| Kanto→Chubu | 100km超のcross-region candidate PASS |
| Kanto / Chubu basemap | 両方向hot reload、route/全point overlay/camera維持PASS |
| Developer Connections | 保存・connection check・region選択・再生成後保持PASS |
| Phase005 / Phase006 | 一般・高速card、模式図、符号・距離、再計算、回転PASS |
| release safety | release basemap fallback、開発接続/診断入口の既存DEBUG条件維持 |

portrait、landscapeの警告と、復帰後の高速模式図・案内を画像目視確認した。
最初の横画面撮影が回転中に入ったため、style完了+animation待ちを入れて再撮影。
画像はlocal build配下だけでreview archive対象外。

## 13. Known limitations / Phase009 handoff

route-constrained matchingは道路ネットワーク全体の識別ではない。
15m程度の同方向並行道路、重なる高架/側道、GPSの持続bias、複雑な折返しを完全には区別できない。
極端な遠方（300m超）は候補対応をUNRELIABLEとして新たな逸脱確定を避ける。
thresholdはheuristicであり、実運行・トンネル・長時間走行評価と調整は今後必要。
process death persistence、sensor fusion、lane推定、dead reckoning、音声は対象外。

Phase009へRouteMatch / RouteDeviationSnapshotを渡せる。
明示的detour/rejoin、復帰点候補、alternate route、driver/operations confirmationはPhase009で扱う。

## 14. Commits / archives / 最終gate

- `641d3bfb3d7bdffd51fe76c5fc21bb5b10766fca`: feat: add route map matching and deviation detection
- 本Review010を含むdocs commit後のHEADを最終対象とする。自己参照SHAを本文へ埋めず、archive metaに完全SHAを記録する。
- 変更対象は実装・テスト・説明文書の27ファイル。詳細一覧はarchiveのphase-changed-files.txt。

最終HEADで次を実行し、check summaryのHEAD/BaseRef一致とPASSを必須にする。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-review-checks.ps1 -BaseRef 292804d7545130d9e86c492d8f9ae59f159af1d3
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef 292804d7545130d9e86c492d8f9ae59f159af1d3 -SkipChecks
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1 -BaseRef 292804d7545130d9e86c492d8f9ae59f159af1d3 -SkipChecks
```

出力: `busnav-review-latest.zip` / `busnav-full-review-latest.zip` とHEAD入りtimestamp版。
archive self-checkのentry/path/HEAD/BaseRef検証を納品gateとする。
local.properties、実.env、GPS trace/raw history、physical serial、PBF/MBTiles/PMTiles/graph、
APK/build/logcat、secretを含めない。事前に存在した未追跡の開発設定は未変更であり、commit対象外。
最終check結果とarchive self-checkはzip内checks/metaおよび最終報告を正本とする。
