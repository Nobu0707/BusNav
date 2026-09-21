# RoutePlan editor

Phase008.5B の編集画面は、地図に下部の draggable sheet を重ねる構成です。
Navigation の「ルート」から開きます。戻る／Android back は進行中探索をキャンセルし、
「編集完了」は in-memory plan の dirty 状態を確定して Navigation へ戻ります。
「このルートを使用」は現在 revision の探索成功結果だけを active route へ採用します。

## 地点登録

地図中央の固定十字に道路を合わせて登録ボタンを押します。種類を先に選びます。
ボタンは「経由地を登録」など選択した種類を表示し、シート縮小中も登録種類を確認できます。
表示は出発地・目的地・経由地・通過指定です。通過指定の説明は
「この付近を通るよう経路を調整」です。内部 enum の VIA/SHAPING は画面に表示しません。

座標はボタン押下時に MapLibre projection で MapView の中心 pixel から取得し、丸めずに
StateHolder へ渡します。表示用の緯度経度だけを小数6桁に整えます。snap-to-road、住所検索、
reverse geocode は行いません。長押しは補助操作として残します。
出発地・目的地の再登録は置き換え、中間地点は追加です。

## シートと一覧

- PEEK: ハンドルと固定フッターを残します。完全には消えません。
- PARTIAL: 地図操作と地点編集を行う通常状態です。
- EXPANDED: 一覧を広く表示します。

ハンドルの上下ドラッグで隣の状態へ、タップで展開／縮小します。一覧の端の未消費スクロールも
NestedScroll でシートへ渡します。地図領域にはシートのジェスチャーを設定しません。

種類ボタン・カーソル説明・探索状態／結果・地点一覧は一つの LazyColumn でスクロールします。
「経路探索」「編集完了」はスクロール領域外の固定フッターです。
20地点でも末尾まで移動でき、種類／名称／補助的な座標表示の下に48dp以上の操作ボタンを置きます。
中間地点は上下移動・経由地／通過指定切り替え・削除が可能です。端点の削除は確認します。

## カメラ

編集開始時だけ、active route geometry → candidate geometry → plan points の優先順で fit 要求を発行します。
1地点（同一点の重複も含む）は zoom15 で中心へ。対象なしは既存カメラをそのまま引き継ぎます。
fit は native style と View のレイアウト、シート寸法がそろってから実行します。
表示中シートの高さ・地点登録ボタン・余白を非対称 bounds padding に反映します。

地図操作、地点追加、一覧スクロール、シート展開／縮小は fit を発行しません。
「プラン全体」は明示的な points fit、探索成功は candidate bounds fit です。
fit完了時は見えている中心を保ってMapLibreの持続paddingを解除し、
カーソル・pan・復元時の中心を一致させます。探索ボタンはシートをPARTIALに戻し、
その寸法の通知を待ってcandidate fitを行います。要求IDを実行後に消費するため、再描画／style切り替えで再実行しません。
カメラは中心・zoom・bearing・tiltを保存し、MapView再生成時に復元します。

## 状態と既存機能

RoutePlanEditorViewModel は plan・選択種類・シート状態・カメラと未処理の fit intent を保持します。
RouteCalculationViewModel は計算と candidate を Activity再生成をまたいで保持します。
未保存 draft の process death 復元は対象外です。Phase008.5C で保存済み経路の Room 永続化を追加しました。

ScheduledRoute／candidate と RoutePlan preview は既存の別source/layerを使います。
探索前の直線previewは「仮ルート（経路探索前プレビュー）」と表示し、探索成功時は道路沿いcandidateへ
切り替えます。route matching／guidance／deviation／テーマ切り替えのロジックは変更しません。
Phase009 の Detour/Rejoin や自動再探索は未実装です。

## Phase008.5C 保存・編集

下部「ルート」の直接入口と中央cursor / PEEK・PARTIAL・EXPANDED / 固定footer は維持します。地点一覧に所定経路一覧への入口を追加しました。
保存済み経路の編集は source record ID を保持する draft。保存まで DB 不変、戻る・Back はキャンセルです。地点変更後は明示的な経路探索→候補適用が必要です。編集完了で保存ダイアログを開き、上書きまたは別名保存を選びます。saved VehicleProfile を再計算へ渡します。
[所定経路ライブラリ](../prescribed-route-library.md)を参照してください。
## Phase008.5D との分離

editor の START/DEST/VIA/SHAPING と FREE の目的地選択は別状態です。中央カーソル描画のみ MapSelectionCursor を共用し、ROUTE_POINT と FREE_DESTINATION を区別します。
「ルート」→「経路編集」から入ります。候補の適用はプレビューになり、保存操作を継続できます。案内は「ルート」→「案内開始」で明示開始します。計算中に同じ revision へ再要求しても、古い世代の結果は採用しません。
