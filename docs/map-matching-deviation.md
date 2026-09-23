# Phase008: Route-constrained Map Matching / 所定経路逸脱検知

## Phase009の前方制約

RouteMatcher.matchへ任意のRouteMatchConstraintを追加した。通常呼出しは無制約で既存挙動を維持する。
迂回中の所定経路照合だけ、anchor + 500m以上のprojectionをscore候補から選ぶ。
候補なしはnull matchで、不適格な過去区間へfallbackしない。
Detour用の逸脱判定と所定経路復帰判定は独立し、復帰確定を優先する。
復帰はMATCHED・25m/精度30m・連続3fix/2秒、方向利用可能時45度以内。
[Detour/Rejoin](detour-rejoin.md)に閾値・early rejoin・保持indexの詳細を記載。

## 境界

Projection は geometry への幾何学的投影。RouteProjector は従来どおり全 segment の最近傍を返し、
hint は距離同点の選択だけに使う。共通の projectSegment を抽出した。

RouteMatcher は **所定経路上** の候補を距離・heading・履歴で評価する。
道路ネットワーク全体を探索する road-network matching、Valhalla trace API、道路種別推定ではない。
高架と側道、同じ方向の並行道路、上下階、走行車線を完全に識別する機能ではない。
毎 fix の HTTP、再探索、route 差替え、detour/rejoin 生成は行わない。

## 構成と距離軸

`LocationState -> RouteMatchIndex / RouteMatcher -> RouteDeviationDetector -> NavigationProgressCalculator -> HighwayGuidanceCalculator -> UI`

Android/Compose 非依存の純粋 Kotlin。既存 LocationState を再利用し、
RouteDistanceIndex を唯一の累積距離軸として projector、matcher、maneuver、高速 decision が共有する。
NavigationProgressTracker は既存APIとして残すが、production の matching 経路では呼ばない。
15m以下の後退 jitter を保持する責務は RouteMatcher の heldProgressMeters だけが持つ。
実際の後退が15mを超え、時間・速度上可能なら progress は戻れる。

RouteMatch の MATCHED は「この route 候補との対応が使える」という分類であり、
ON_ROUTE と同義ではない。例えば100m離れた単純な直線 route は候補が一意なら MATCHED でも逸脱証拠になる。
強案内には MATCHED **かつ** ON_ROUTE が必要。AMBIGUOUS は UNCERTAIN、
UNRELIABLE は UNRELIABLE として progress に渡す。

## 候補インデックス

route 適用時に Default dispatcher 上で距離 index、maneuver/highway cache、RouteMatchIndex を構築する。
segment の端点・距離軸上の開始距離・長さ・bearing・bounding region を保持。
0.01度 grid に segment の bounding region を登録し、longitude を wrap する。
350m近傍にかかるセルと前回 segment の前後12本を候補にする。近接する別 branch もセルから拾う。
長い segment の登録が256セルを超えると共通候補リストへ移し、巨大な grid 展開を避ける。
候補がない場合、または局所候補が350mより遠い場合は global fallback。
極域の広い検索でも全候補へ fallback。単純な window だけに閉じ込めない。

通常の密な経路では全 segment を投影しない。非常に長い・大きく重なる segment の多数ある
病的な geometry では O(N) fallback が残る。geometry の full parse や index 再構築は fix ごとに行わない。

## スコア

低い値を優先する。すべて RouteMatcherConfig に集約。

| 項目 | 既定値 / 計算 |
| --- | --- |
| 距離 | cross-track / max(accuracy, 8m)、weight 1 |
| heading | 最小角差 / 180度、weight 4 |
| heading 有効条件 | finite bearing と finite speed >= 2.5m/s |
| continuity | max(abs(progress差)-15m, 0) / max(50m, allowance)、weight 0.25 |
| allowance | speed × dt × 1.5 + 45m、speed 不明時45m/s |
| jump | max((移動距離-allowance)/45m, 0)、weight 4 |
| candidate ambiguity | 別仮説との score gap < 0.35 |
| 同一仮説 | progress差20m以下の隣接投影。頂点での偽 ambiguity を回避 |
| 低品質 | accuracy が null / NaN / infinite / negative / 40m超、jump不可能、距離300m超 |
| 再捕捉 | near reliable anchor を更新できない fix が3回続いた後、次回は旧anchor制約を解除 |

heading の359度/1度は2度、350度/10度は20度。停止中や低速、欠損時は heading 項を使わない。
zero-length segment は bearing=null、投影距離は有効。
距離項の accuracy 正規化は heuristic で、accuracy を真の正規分布のσとは扱わない。
confidence は `1/(1+bestScore)` に candidate gap の相対係数を掛けた **heuristic / relative confidence**。
統計的に校正した確率ではなく、UIの確率表示にも使わない。

不確実・遠方の結果は reliable anchor / held progress を進めない。
その間も時刻と lostFixes は進める。再捕捉後の強案内は deviation 側の復帰確認にも従う。
単発の数km jump で progress を飛ばさず、長い fix 間隔や持続する移動後には再捕捉できる。

## 単調時刻・鮮度・並行計算

AndroidLocationProvider は `Location.elapsedRealtimeNanos / 1_000_000` を渡す。
timestampMillis は wall-clock metadata のまま保持し、速度・継続時間・鮮度には使わない。
production ViewModel は `SystemClock.elapsedRealtime()` を注入。
JVMテストでは仮想単調時計を明示注入する。旧/外部 provider が単調時刻を渡さなければ安全側へ抑制する。

10秒以上古い fix、未来/負/欠損の単調時刻は採用しない。
古い/同時刻の fix は marker・案内・逸脱連続回数を巻き戻さず無視。
freshness job は新しい位置が来なくても10秒で強案内を止める。dead reckoning は行わない。
連続証拠の間隔も10秒以上空けば回数・継続時間をリセットする。

matcher/index計算は Default、状態commitは holder のUI scope。
matcherとdetectorは immutable previous state を受け取る純粋遷移で、キャンセルされた計算はtrackerを汚さない。
job cancel に加え、fix計算世代、route準備世代、route identity、location identity と完了時鮮度を確認する。
route 変更時は matcher、deviation、held progress、高速履歴を破棄し UNKNOWN。
ViewModel が回転・Activity recreation をまたいで保持。process death の永続化は対象外。

## 逸脱と復帰

| 設定 | 既定値 |
| --- | --- |
| on-route | cross-track <= 25m |
| suspected | cross-track >= 35m |
| 強い逸脱証拠 | cross-track - accuracy >= 50m |
| OFF_ROUTE確定 | 強い証拠3 fix以上 **かつ** その継続時間3秒以上 |
| 復帰 | 25m以内の信頼できる3 fix以上 **かつ** 2秒以上 |
| 証拠の最大間隔 | 10秒未満 |

`UNKNOWN -> ON_ROUTE -> SUSPECTED_OFF_ROUTE -> OFF_ROUTE -> RECOVERING -> ON_ROUTE`

- 単発100m spikeはSUSPECTEDだけ。次の良好fixでON_ROUTEへ戻せる。
- 同方向の並行道路でも70m差・5m精度が持続すればOFF_ROUTE。
- 精度不良やAMBIGUOUSでOFF_ROUTEを新たに確定しない。連続証拠をリセット。
- 既に確定したOFF_ROUTEは精度不良時も警告を保持するが quality=UNRELIABLE、証拠回数0。
- ON_ROUTE後の欠測・精度不良からはRECOVERINGを経て案内復帰。
- 「不確実から復帰中」と「既に逸脱確定」を内部で区別する。
  不確実からの復帰が中断されただけでOFF_ROUTEに昇格させない。
- 25–35mはhysteresis band。直前の状態を保ち、新たな逸脱証拠にしない。
- 初回fixは候補・精度・時刻を検証。単に最寄りだからMATCHEDとはしない。

15m程度の並行道路は、同方向でGPS誤差とも整合する場合、所定経路のgeometryとGPSだけでは識別できない。
本実装はここを強制的にOFF_ROUTEにしない。このケースと持続70m差を別のテストで固定している。
この限界を解消するには別の観測やroad-network matchingが必要。

## 案内・地図・UI

ON_ROUTE/MATCHEDのみ通常の一般道指示、高速方面/sign/距離/模式図を表示する。
SUSPECTED、OFF_ROUTE、RECOVERING、AMBIGUOUS、UNRELIABLEは強案内を抑制。
高速cardは確認表示になり、方向・sign・模式図を出さない。復帰確認完了で通常表示へ戻る。

縦画面はguide card上、横画面は左guide column先頭にbannerを配置。
OFF_ROUTEはerrorContainerで明瞭、SUSPECTED/RECOVERINGは控えめなsurfaceVariant。
色だけに頼らず「可能性があります」「確認中」の日本語とTalkBackのpolite live regionで伝える。
確認・dismiss操作は不要。既存の位置情報権限UIは維持する。

production map は採用した raw GPS位置で自車を描く。routeへ吸着させない。
OFF_ROUTEでもroute lineを保持。debug matched-point overlayは今回追加していない。
内部scoreやsegment番号は運転画面に出さない。

## 診断・privacy・テスト

debug ViewModelだけがmatch quality / deviation state変更時にイベント名を記録。
座標、accuracy履歴、route名、device serial、raw traceをイベントに含めない。
releaseからは診断ログを出さない。raw GPS履歴の永続化機能もない。

unit trace replayは合成座標のみ。各fixのquality、segment、progress、stateとfailure indexを検証。
self-crossing、curve、stationary jitter、上下線、jump、parallel road、精度・時刻・datelineを含む。
state holder testsでno-auto-reroute、一般/高速抑制、復帰、欠測、route差替え、遅い計算の破棄を検証する。
端末testsはfake LocationProviderの合成fixのみ。関東live smokeは公共道路のValhalla routeから
100m offsetを生成し、Kanto basemap上で縦横・回転・逸脱・復帰を確認する。
既存live smokeはテスト限定flowで毎秒synthetic fixを更新し、画面撮影中にも鮮度を維持する。
実車走行や運転中の画面操作を要求しない。写真はlocal build配下だけ、review archiveには入れない。

## Phase009への引き継ぎ

RouteMatch / RouteDeviationSnapshotを明示的detour/rejoin判断の入力にできる。
Phase008は元routeへ戻った観測を確認するだけで、新しいrejoin routeを作らない。
代替経路、復帰点候補、運行管理/運転者の承認操作はPhase009で設計する。
実運行・長時間GPS・トンネル・高架/側道の走行評価とthreshold調整は今後必要。
全国graphやKanto/Chubu basemapの再生成はこのPhaseで行わない。

## FREE integration (Phase008.5D)

FREE でも既存 matcher と raw GPS marker policy を維持します。NavigationMode に応じて「所定経路」/「案内経路」の逸脱文言を表示します。preview では案内を開始しません。
OFF_ROUTE 単独の RoutingEngine 呼出し増分は0。手動操作の1要求→preview→採用確認でのみ置換し、そのとき matcher/deviation/highway/arrival を reset します。詳細は [Free Navigation](free-navigation.md)。Phase009 detour/rejoin は未実装です。
# Phase 010.6B start boundary

Navigation start now uses the separate [location quality policy](location-quality-policy.md). The matcher retains its 40 m and 10 s reliability gates, ambiguity checks, continuity safeguards, and deviation hysteresis. A degraded start does not create `MATCHED` or `OFF_ROUTE` evidence.
