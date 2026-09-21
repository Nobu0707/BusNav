# Free Navigation — Phase008.5D

「ルート」→「現在地からナビ」→地図中央の十字を合わせ「目的地に設定」→「経路を計算」→プレビューを確認して「案内開始」。

## モードと状態

NavigationMode が用途を定義します。activePrescribedRouteId の null/non-null から用途を推測しません。
isNavigationStarted と activeRoute の存在が navigationActive を決めます。

| 状態 | mode | route | saved ID | started |
| --- | --- | --- | --- | --- |
| 保存経路を開く | PRESCRIBED | 保存済み geometry | UUID | false |
| 保存経路のナビに使用 | PRESCRIBED | 同じ geometry | 同じ UUID | true |
| FREE 選択 | FREE | なし | なし | false |
| FREE プレビュー | FREE | candidate | なし | false |
| FREE 案内 | FREE | 採用済み candidate | なし | true |
| FREE 再計算プレビュー | FREE | **旧案内経路を維持** | なし | true |
| 案内終了 | 最後の mode | なし | なし | false |

再計算 candidate は FreeNavigationUiState.previewRoute に分離し、「新しい経路を使用」でのみ置換します。
FREE の目的地は NavigationUiState.freePlan、到着状態は arrival に保持します。選択・計算・プレビューは FreeNavigationStateHolder / ViewModel が担当します。
FreeNavigationUiState が IDLE でも NavigationUiState が started なら案内中です。これは選択画面の終了を意味します。

## 現在地と大型車条件

FreeNavigationPlan は目的地と任意の名前のみを持ちます。START を保存せず、計算・再計算の度に共有 NavigationUiState.location.point を取得します。
RouteMatch の projection や旧保存経路上の点を START として使用しません。GPS 購読は NavigationStateHolder の1本だけです。

FreeNavigationConfig の初期値は取得後10秒以内、accuracy 50m以内。elapsedRealtimeMillis と SystemClock.elapsedRealtime() を用い、wall clock に依存しません。
未許可・未取得・位置情報無効・古い位置・低精度に対応するメッセージを表示し、計算を発行しません。null/負値/非有限 accuracy と未来・不明 timestamp も拒否します。
案内中の matcher は既存の40m accuracy 上限などの安全条件を維持します。

既存 VehicleProfile.DEVELOPMENT_LARGE_BUS（全長12m・幅2.5m・高さ3.5m・重量16t・軸重10t）を使用します。
state holder は profile 注入にも対応します。Valhalla の truck costing、既存 toRoutingRequest() と RoutingEngine を再利用します。
MainActivity が用意する baseUrlProvider がリクエスト時に Developer Connections の最新 Valhalla URL を読みます。

## 選択・計算・プレビュー

Route Editor と FREE 選択は別画面・別状態です。MapSelectionCursor を共用し、ROUTE_POINT / FREE_DESTINATION / NONE を明示します。
地図の pan/zoom と中央カーソルが主操作であり、FREE に長押し登録はありません。選択終了・画面破棄で cursor reader を解除します。
「安全な場所で操作してください」を表示します。下部5項目（ルート・迂回・規制・音声・表示）を維持します。

計算は RouteCalculationStateHolder を使用します。目的地変更・キャンセルで世代を更新し、遅延応答を適用しません。同じ revision で再要求した場合も request generation を検証します。
FREE の RoutePlan ID は free-UUID。保存ライブラリの UUID と別で、自動保存はありません。
結果は経路線・目的地 marker・raw 現在地・距離・時間・車両条件のプレビューです。既存 editor camera/padding ロジックで全体 fit します。横向きは地図と操作欄を左右に並べ、主操作は詳細のスクロール領域外に固定します。

成功だけで案内を開始しません。「案内開始」が必要です。案内情報のない route は「案内情報なし・経路線を表示します」を示し、経路線による案内を許可します。
既存 editor の候補適用は previewEditorCandidate を使い、プレビューとして表示・保存できます。「ルート」→「案内開始」で明示開始できます。
applyCalculatedRoute は旧テスト等の明示適用用互換入口で、内部では preview と start を分離しています。FREE の計算完了処理はこれを呼びません。

## 案内・テーマ・逸脱

一般道 NavigationProgress、HighwayGuidance、RouteMatcher / Deviation は共通です。経路置換で index と matcher/deviation/highway/arrival をリセットします。
raw marker を経路に吸着させません。lane count の推定等も追加しません。

プレビュー・editor・library は LIGHT。案内画面で明示開始済み、かつ現在地がある場合だけ夜間/トンネルに DARK が適用されます。
「所定経路」と「案内経路」を operations、全体表示の contentDescription、逸脱の疑い・逸脱・復帰確認文言で使い分けます。

## 再計算と到着

OFF_ROUTE だけでは RoutingEngine 呼出しは **0増加**。FREE の「現在地から再計算」操作で **+1**。
最新 raw GPS と同じ目的地・車両条件で計算し、成功後も旧経路を案内状態として維持します。
プレビュー・採用確定で追加呼出しは0。失敗・キャンセルでも旧経路を維持します。
再計算プレビュー画面のテーマは LIGHT ですが、旧 session の started は維持します。

ArrivalDetector は純粋な状態遷移です。初期設定:
- approach: 信頼できる残距離250m以内
- arrival: 残距離50m以内 AND raw GPS と目的地の距離75m以内 AND accuracy 50m以内 AND 信頼できる route progress
- distinct monotonic fixes が2回連続、fix間隔・鮮度10秒以内

単発 spike、重複/古い fix、不確かな matching、低精度、長い fix 間隔から到着を確定しません。
ARRIVED は「目的地周辺です」と表示し、driver が「案内終了」を押すまで route/map を残します。
終了は route、FREE plan、目的地 marker、matcher/deviation/highway/arrival をクリアします。前の保存経路を自動復元しません。

## ライブラリ分離・切替・再生成

FREE state holder は PrescribedRouteRepository を持ちません。計算・開始・再計算・終了で保存レコードを追加・更新・削除しません。
案内中に別の設定・library・editor に入る場合は終了確認を出します。キャンセルで旧案内を維持します。
state holder も active session の無確認置換を拒否します。library は案内終了後に操作する方針です。

選択、目的地、プレビュー、active route、camera は ViewModel に保持し、Activity recreation で保持します。
View/Activity/MapLibre listener を ViewModel に保持しません。process death 後の FREE session 復元は対象外です。

## 検証・境界

単体テスト: mode、位置品質・raw START、profile、preview/start、theme、文言、呼出し回数、遅延応答、手動再計算、到着、終了、切替。
端末テスト: 中央カーソル、選択/目的地/preview/active の Activity recreation、一般道/高速、逸脱、再計算失敗/採用、到着、終了、Room レコード不変、保存経路への復帰。
live 関東試験は東京駅付近→上野駅付近の公共地点を使用します。未稼働サービスは既存 Assumption utility で formal skip します。

Phase009 の detour/rejoin・speed lock は未実装です。「迂回」は placeholder のままです。
FREE 自動 reroute、VIA 編集、FREE の自動保存、process-death 自動復元は対象外です。
将来の FREE 自動 reroute は専用 lifecycle・頻度制限・品質/応答世代検証と利用者への通知を設計し、PRESCRIBED detour policy と分離します。
