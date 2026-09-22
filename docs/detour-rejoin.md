# 明示的 Detour / Rejoin（Phase009）

所定経路を正として保持し、運転者が復帰地点と必要な経由地を選び、計算結果を確認してから迂回案内を開始する。逸脱検知からの自動再探索は行わない。

## 操作

1. 保存した所定経路を開き、案内を開始する。
2. 下部「迂回」、または所定経路逸脱時の「迂回を検討」を押す。
3. 前方の復帰候補を選ぶ。手動の場合は「地図で復帰地点を選択」で中央カーソルを所定経路に合わせ、「復帰地点に設定」する。
4. 必要に応じ、中央カーソルから経由地（VIA）・通過指定（SHAPING）を追加する。順序変更・削除が可能。
5. 「迂回経路を計算」で1本だけ計算する。成功時はプレビュー。元の案内経路は変わらない。
6. 距離・時間・復帰位置・地点数・保存車両条件を確認し、「この迂回経路を使用」を押す。
7. 元の所定経路の前方区間との安定した一致を確認すると、通信せず元の案内に復帰する。

FREEの「現在地からナビ」では迂回は無効。既存の明示的な「現在地から再計算」を使用する。

## 経路とsession identity

NavigationUiState.activeRoute は現在案内対象の navigation route。
prescribedRouteSnapshot は保存された元の ScheduledRoute、prescribedVehicleProfile は保存時の車両条件。
通常時は両routeが同じで、迂回ACTIVE時だけactiveRouteが迂回に置き換わる。
navigationMode は常に PRESCRIBED、activePrescribedRouteId も維持する。
prescribedSubmode は NORMAL / DETOUR。FREEでは所定経路snapshotと車両条件をnullにする。

open/clearごとに prescribedSessionToken が増える。DetourStateHolder はtoken・ID・route snapshot identity・profileを確認する。
ライブラリの名前変更は維持、route内容またはprofile変更の通知は元sessionを終了する。
案内中のライブラリ編集・削除は既存policyで禁止。迂回holderはrepositoryへ書き込まない。

## 責務

- domain/detour: DetourReason、RejoinTarget、DetourDraft、DetourCandidate、ActiveDetour、候補生成、復帰判定とconfig。
- DetourStateHolder: 計画、draft変更、世代管理、計算、preview、明示採用、編集lock。ViewModelで回転を保持。
- NavigationStateHolder: raw位置購読、現在案内のmatcher/逸脱/一般/高速案内、および元所定経路の復帰matcher。
- MapController / DetourOverlayController: 所定線に重ねる太い迂回線、候補番号、選択強調、経由地点を保持・再描画。

計画画面はLIGHT。裏では開始済みの所定/迂回案内処理を続ける。ACTIVEのナビ画面は既存の夜間・トンネルDARK policyを使用する。
地図は所定線→迂回線→marker→raw GPSの順。番号1/2/3は候補リストと対応し、選択は外周を強調する。
手動復帰はR、経由地はV、通過指定はS。線幅と説明文でも区別する。

## Anchor・START・車両

最後に MATCHED、ON_ROUTE、距離25m以内だった所定経路進捗を保持する。
OFF_ROUTEや低精度で消去しないが、履歴なし・5分超の履歴は開始を拒否する。0mへの暗黙fallbackはない。

STARTは計算ボタンを押した時の LocationState.point（raw GPS）。
投影位置やanchor座標を出発地にはしない。
権限、位置取得エラー、単調時計で10秒以内、精度50m以内を確認する。
保存recordのVehicleProfile snapshotをRoutingRequestへ渡す。
既存開発用固定profileへの置換や、元RoutePlanの変更はしない。

## 復帰候補

RouteDistanceIndexの距離軸を使い、geometry segment内を補間したpointを生成する。HTTPは0回。
候補を近い順に表示し、おすすめ・最短等のrankingは行わない。

| 項目 | 既定値 |
| --- | --- |
| 最小前方距離 | anchor + 500mより大きい |
| 希望offset | 1,000 / 3,000 / 5,000m |
| 最大前方距離 | 10,000m |
| 最小候補間隔 | 500m |
| 最大候補数 | 3 |
| 分岐・合流buffer | 前後200m |
| 目的地buffer | 300m |
| 手動cursorの経路距離上限 | 80m |

EXIT / RAMP / KEEP / MERGE / U_TURN / DESTINATIONおよびHighwayDecision位置を避ける。
不適切な候補を除外し、0件も許容する。不足分のためにbufferを解除しない。
手動選択にも同じ制約を適用し、所定経路へ投影した点へ正規化する。
経路が重なり複数progressを指す地点は拒否する。理由を表示し、勝手に別区間へ移さない。

## Request・preview・取消

START → draftのVIA/SHAPING順 → RejoinTargetをDESTINATION/breakとして送る。
候補表示/選択/手動設定0回、計算+1回、preview/採用+0回。
RoutingFailureを表示して旧案内を継続する。

target変更・地点追加/削除/順序変更・終了・mode/session変更・cancelはrevision/generationを更新し、キャンセルを無視する旧計算結果も破棄する。
PREVIEWはACTIVEと別state。明示採用まで元routeは案内対象のまま。
再計画中は旧active detourを保持し、再計画取消は旧迂回へ戻る。
「迂回案内を終了」は計算せず所定snapshotへ戻し、現位置のmatcher/逸脱判定を再開する。経路外なら位置不確実/逸脱警告を表示する。

## 復帰判定と案内再開

ACTIVEでは2系統を使用する。

- active detour matcher: 既存の一般案内・高速案内・逸脱検知。
- prescribed matcher: RouteMatchConstraint(minProgressMeters = anchor + 500m)を適用した復帰監視。

constraintは候補scoreの選択前に投影progressをfilterする。過去区間を選んだ後で判定する方式ではない。
該当候補なしはnull matchとし、無制約fallbackへ戻さない。
元経路のindex/calculatorは通常案内から保持して再利用し、迂回indexは経路切替時にDefault上で1回構築する。毎fixのindex再構築はしない。

| 復帰 evidence | 既定値 |
| --- | --- |
| 経路からの距離 | 25m以内 |
| accuracy | 30m以内 |
| quality | MATCHEDのみ |
| heading | 利用可能なら45度以内（既存matcherの速度2.5m/s以上で使用） |
| distinct fixes | 連続3回以上 |
| duration | 2秒以上 |
| freshness / 最大evidence間隔 | 10秒未満 |

AMBIGUOUS、UNRELIABLE、低精度、逆方向、遠距離、古いfixは証拠を積まない。
planned targetより手前でもfloor以上ならearly rejoin、target通過後でも復帰を認める。
CONFIRMEDを迂回逸脱表示より優先し、実際のprojection・matcher stateを元経路の案内へ引き継ぐ。
先頭進捗やplanned targetへ飛ばさない。復帰時のHTTPは0回。
「所定経路に復帰しました」を4秒表示する。

## 安全操作とlifecycle

既知speedが2.0m/sを超えた時にtarget選択・cursor設定・地点編集・計算・採用を禁止。
speed nullは自動的に走行扱いにしない。UIだけでなくholderでも検査する。既存案内の計算は継続する。
Activity recreationではdraft、選択、preview、active、復帰証拠、cameraをViewModelで保持する。
cursor readerはmapのDisposableEffectで解除し、Route Editor/FREE/Detourのlistenerを混在させない。

## 限界・Phase010

process death復元、交通情報取得、通行規制の自動回避、音声、配車指示、FREE detour、自動再探索は対象外。
道路が近接/重複するとmatcherが不確実になるため、復帰・逸脱を保守的に保留する。
candidate bufferはmaneuver metadataに依存し、現場の安全な復帰場所を保証するものではない。
通行止めfeedはないためSTART→REJOINだけでは同じ道路を使う場合がある。必要なら明示的なVIA/SHAPINGで道路を指定する。
Phase010はDetourReason / DetourDraft / RejoinTargetを入口にVICS・交通規制sourceを接続する。明示採用とno silent rerouteは維持する。

検証結果: [Review012](reviews/012-detour-rejoin.md)。

## Phase010 traffic context and candidate validation

「規制」または前方規制警告の「迂回を検討」は ROAD_CLOSURE / TRAFFIC_INCIDENT / ROADWORK と TrafficDetourContext を渡します。既知の規制終端 + 250m を automatic/manual target と実際の復帰 matcher の下限へ適用します。終端不明は推測しません。Valhalla は動的規制を知らないため、計算候補を TrafficDetourValidator で検証し、高信頼な閉鎖重複は UI と state holder の両方で適用を拒否します。曖昧・未接続は警告し、VIA/SHAPING を手動追加して再計算できます。受信・パネル・検討・選択は HTTP 0、計算だけ +1。詳細は [交通情報基盤](traffic-road-restrictions.md)。

## Phase010.5B navigation camera

Active DETOUR on the navigation screen shares the persisted orientation and heading resolver with normal PRESCRIBED navigation. Planning/edit/preview screens do not enable navigation rotation or compass. Rejoin preserves orientation and follow intent. See [navigation map orientation](navigation-map-orientation.md).
