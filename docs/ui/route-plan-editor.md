# RoutePlan editor

## 画面分離と安全性

走行中の Navigation 画面には「ルート編集」入口と「編集プランあり」の要約だけを置き、複雑な地点操作は専用 `RoutePlanEditorScreen` で行います。Phase 003 に速度連動ロックはありませんが、将来入口を無効化できる責務境界です。Android back と画面内の「戻る」は Navigation へ戻り、「編集完了」は in-memory plan の dirty 状態を確定します。ScheduledRoute の再計算は行いません。

`RoutePlanEditorViewModel` が純粋Kotlinの `RoutePlanEditorStateHolder` を保持するため、通常の Activity 再生成を伴う画面回転でもプロセスが生きている間は編集内容を維持します。process death とDB永続化は対象外です。

## レイアウト

縦画面は header、地図、point editor/list の順です。横画面は左38%を editor/list、右62%を地図とし、21:9で地図面積を確保します。どちらも `BoxWithConstraints` の実寸で切り替えます。

MapLibre/OpenGL は `mapContent` lambdaへ分離しており、Compose testではBoxに差し替えます。

## 地図長押しと追加モード

START、DESTINATION、VIA、SHAPING の追加モードを先に選び、地図長押し座標を `GeoPoint` に変換してStateHolderへ渡します。頻繁に追加する中間点で毎回4択dialogを出さず、現在モードが画面に見える方式です。START/DESTINATIONの再設定は既存点を置換します。住所検索、逆geocode、snap-to-roadは行いません。

## point list

type と、nameがなければ緯度経度を表示します。VIA/SHAPINGには上・下、切替、削除を表示します。並べ替えは外部ライブラリを増やさず、確実に操作できる上・下ボタン方式です。START/DESTINATIONは固定し、削除前に確認dialogを表示します。

VIAは「必ず通る地点」、SHAPINGは「ルート形状の誘導点」と画面上に説明します。ボタンはtypeの文字labelとcontent descriptionを持ち、色だけに依存しません。

## preview overlay

編集画面は確定済みScheduledRouteを残したまま、RoutePlanを別source/layerで重ねます。

- ScheduledRoute: 水色の太い実線とcasing
- RoutePlan: オレンジの細い半透明線
- RoutePlan marker: START/VIA/SHAPING/DESTINATIONで色と半径を変更

画面には「仮ルート（経路探索前プレビュー）」と明記します。preview は点を直線で結ぶだけで道路geometryではありません。source/layer IDはScheduledRouteと分離し、style reload時は双方を存在確認付きで復元します。

「プラン全体」は1点ならcenter、2点以上ならpadding付きbounds fit、0点なら無効です。点追加ごとの強制camera移動は行いません。
