# Review009 — Phase006 高速道路 / JCT / IC 案内

## 概要と基準

- BASE_SHA: `c76e1a05bd7c30620741143fa19bea0ea37dc6ed`（開始時に実 HEAD から取得）
- Phase005: COMPLETE として継続。既存141 unit tests、geometry距離軸、信頼性、画面保持、詳細地図を再利用。
- 開始時 tracked diff は空。既存 `.vscode/` と `gradle/gradle-daemon-jvm.properties` は保持し stage 対象外。
- 実装・検証は Windows PowerShell / SDK adb.exe。通常 sandbox の ACL 起動障害のため、対象限定の昇格コマンドを使用。

## Maneuver enum audit

Valhalla 3.9.0 の pin `a3a5631c4d243eee9a09241f4ffe6680a67dd55a` にある [公式 directions.proto](https://github.com/valhalla/valhalla/blob/a3a5631c4d243eee9a09241f4ffe6680a67dd55a/proto/descriptors/directions.proto) の `DirectionsLeg.Maneuver.Type` を直接確認した。

- 25 = kMerge、37 = kMergeRight、38 = kMergeLeft はすべて定義済み。既存 MERGE mapping を維持。
- domain は左右 merge を区別せず「合流」に集約。側を推測しない。
- 未定義値、未対応 transit / indoor 等は UNKNOWN。0 / -1 / 30 / 36 / 39 / 45 / 46 / 999 を回帰テスト。
- `directions_type=maneuvers` を維持。新しい整数値の推測マッピングなし。

## Domain / extraction / facility / sign

`HighwayDecision` は maneuverIndex、type、facilityType、geometry距離、構造化sign、street namesを保持。`HighwayGuidanceSnapshot` は phase、current、next、両距離、信頼性、接近強調を保持。Android / Compose / Valhalla JSONに依存しない。

Extractor は ramp、exit、keep、merge、標識付きcontinueを抽出。一般道の通常右左折、未知type、空標識だけのcontinueは除外。全routeのhighwayフラグで高速UIにはしない。

施設分類は JCT / Junction / ジャンクション、IC / Interchange / インターチェンジがsignに明示される場合のみ。分類時だけ NFKC で全角文字も認識する（実fixtureの新富士ＩＣ）。ICカード、英単語内の一致、JCT/IC混在は保守的に除外。未確定なら EXIT / RAMP / UNKNOWN。UIの原文は保持する。

出口番号→branch→toward→施設名の順で空文字・重複を除去し順序保持。consecutive_countは既存domainで維持し、推測した優先度には使わない。branchは原文の角丸badgeで表示し、E1等も未知形式も過剰な正規化をしない。

## State / threshold / distance / close decisions

| 残距離 | 状態 |
|---|---|
| >5000 m | HIGHWAY_CRUISE。確定した高速走行中情報がないため通常カード |
| <=5000 m | APPROACHING_DECISION、高速カード |
| <=2000 m | 距離表示を強調 |
| <=700 m | IMMINENT_DECISION |
| <=120 m | TRANSITION |
| decision通過30 m超 | 次decisionへ即時繰り上げ |

高速カードから通常カードに戻る境界には100 mのhysteresis。ランプ／出口のmaneuver種別と距離phaseを分離し、ENTERING/EXITINGという独立phaseは設けない。入口と断定できないrampは「ランプへ」。

位置は `beginGeometryIndex -> RouteDistanceIndex.distanceAtGeometryIndex()`。残距離はその位置とprogressの差で、maneuver.lengthと混用しない。nextも現在地からの距離。1000 m / 1600 m / 2000 mの近接decisionでcurrent/nextが空白を挟まず繰り上がることを確認。

## Reliability / no reroute / performance / state

UNCERTAIN / UNRELIABLE / accuracy悪化では直前decisionを保持するが、方向・距離・sign・next・模式図は表示しない。「経路上の位置を確認中」を表示。位置取得停止時は時間でprogressを進めず、Disabled/error/権限なしでは待機。正常fix復帰で再評価。

route適用時にdistance index、decision list、標識分類を構築しGPS毎の正規表現解析を避ける。既存projectorのfull scanは変更しない。ViewModel配下でroute/calculator/previous snapshotを保持し、Activity再生成・回転でも維持。route置換ではcacheを再構築。

highway calculator / formatter / UIからRoutingEngineへの呼出しはない。実smokeでは位置精度悪化・off-routeでも探索回数が増えず、明示的な再計算操作でのみ増えることを確認。

## UI / schematic / accessibility

- 縦: 上部カードを最大画面高35%に制限。地図領域を維持。
- 横: 既存左guide / 中央map / 右controlsの3列を維持。
- 優先順位: 方向、距離、施設／出口名、方面、route badge、その次。
- Canvas: 選択分岐を太線と矢印、非選択側を控えめに表示。左／右／直進／合流、出口をpure schematic modelに分離。
- 車線数・車線境界・正確なJCT形状は描かない。「右車線」「左2車線」等の案内なし。
- 方向は文字でも表示。カードのcontentDescriptionに距離・方向・標識・次案内を含め、省略された長文も読み上げ可能。小画面・大文字設定は内部スクロールで対応。
- 実機スクリーンショットで新富士ＩＣ、出口7、富士宮／新富士、139、次の分岐と地図を確認。画像はbuild配下のQA用途のみでarchiveに含めない。

## Tests / live / devices

| 検証 | 結果 |
|---|---|
| unit | 170件、新規29件、failure/error/skip 0 |
| deterministic coverage | 抽出、施設、重複、geometry軸、各境界の前/一致/後、近接分岐、信頼性、formatter、模式図、accuracy/route置換 |
| pinned highway fixture | 16 maneuvers / 4 sign-bearingを再利用、finite decisionとrollover PASS |
| lint | PASS |
| assembleDebug / assembleRelease | PASS / PASS |
| assembleDebugAndroidTest | PASS |
| emulator | Android16。高速カード4件と実route smoke成功、最終全suite receiptはarchive checks参照 |
| physical | Windows adb.exeでstate=device確認。SOG06 / Android14 |
| physical full instrumentation | 32件、failure/error/skip 0。LAN経由のValhalla・basemapを使用 |
| physical connectedDebugAndroidTest | 最終確認中 |
| live Valhalla | version 3.9.0-a3a5631c4。高速route計算、apply、sign、geometry index、current/next、再計算成功 |
| basemap regression | style/vector tile/日本語glyph、hot reload、overlay成功 |
| Developer Connections | 縦横保存/reset/接続結果、DataStore再生成によるpersist成功 |

unit新規29件の内訳: highway domain22、formatter5、state holder1、既存fixture suiteへの追加1。件数はJUnit XMLから集計。

手動smoke相当のUI操作をandroidTestで実行: START/DESTを開発用固定地点へ配置→実探索→候補apply→模擬位置→高速案内／sign→Activity再生成→横画面→accuracy悪化／off-route抑制→通過→再計算。全thresholdはpure境界テストで保証。実GPS走行はしていない。大きいrouteの表示を分岐へ拡大するQA操作はtest内のMapLibre camera操作で補助。

エミュレータと実機は明示的に対象選択。ANDROID_SERIALは実行後に解除。端末識別子・MAC・実GPS履歴は本書にもarchiveにも記録しない。Gradle connected test後はAPKと実機の元のLAN接続設定を復元する。LAN設定は既存Developer Connections画面経由、production codeにIP追加なし。server bind / firewall / .env変更なし。

Local assumptionsはアプリのDeveloper Connections保存値を読む。サーバー停止時はJUnit assumption SKIP、接続可能時のプロトコルエラーやUI不具合はFAIL。未実行を成功扱いしない。

## Limitations / Phase007・008 handoff

simple projectionは高架・並行道路の誤対応を完全には判別できない。Map Matching / deviation detectionはPhase008。lane source、車線数案内、音声、dead reckoning、rerouteは対象外。遠方decisionだけを根拠に現在高速走行中と断定しないため、5km圏外は一般案内。巨大標識の完全表示は画面幅に制約がある。process death永続化は未実装。Phase007の地図style全面変更なし。

## Commits / archives

実装は `feat: add highway junction guidance`、検証確定は後続docs commit。正確な最終HEADとcommit列はarchiveのmetaに記録する。BASE_SHAは上記の開始時HEADを固定して使う。

最終HEADに `scripts/run-review-checks.ps1 -BaseRef <BASE_SHA>` を実行後、`make-review-archive.ps1` と `make-full-review-archive.ps1` を同じBaseRefで実行する。配布先はrepo rootの `busnav-review-latest.zip` / `busnav-full-review-latest.zip`。local.properties、.env、PBF/MBTiles/PMTiles、APK/build/logcat、GPS履歴、端末識別子、秘密情報を除外し、slash entriesとHEAD/BaseRef整合をself-checkする。

最終状態: 検証・archive作成中。
