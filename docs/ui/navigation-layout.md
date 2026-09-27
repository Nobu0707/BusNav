# ナビゲーション画面レイアウト

## Phase010.6F: 縦横共通の地図 overlay

NavigationScreen は縦横とも MapArea を主領域にする。運行情報 / PlaceholderPanel は表示しない。
警告・案内を地図外の別列や行へ重複表示しない。幅600dp以上かつ幅が高さを超える場合は横画面。
既存の状態識別子 LandscapeThreeColumn は互換性のため残すが、実際の構成は地図と補助操作の2領域。

縦画面: Column(MapArea weight=1, AuxiliaryControls 72dp)。運行情報64dpと間隔分を地図へ戻す。
横画面: Row(MapArea weight=1, AuxiliaryControls 88dp)。旧左列の24%割当を廃止。
補助操作は「ルート / 迂回 / 規制 / 音声 / 表示」の5個。横は88dpの親、80dpのボタン、
1行ラベル、48dp以上のtouch targetを維持する。

## 上部の警告と案内（Phase010.6G）

縦画面の案内カードは左右8dpを除く全幅。目安となる最低高さはMapArea高の22%（最大180dp）で、上部領域は30%を上限にスクロールする。記号40sp、主案内28sp、距離32sp、補足18spとする。
横画面の案内領域はMapArea幅の54%。右側には方位・ズーム・自車を確保し、上部領域は地図高の72%を上限にする。
逸脱警告をNavigationGuidanceCard内の先頭に統合する。重大警告は明暗に応じた鮮やかな赤、位置確認などは中性色。独立DeviationBannerはナビ上部に出さず、警告を二重表示しない。
到着、交通警告、迂回の一時操作は案内カードの外側に残す。高速道路の標識・アクセシビリティ情報を保持する。透明領域全体にclickableは付けない。

縦画面ではMapAreaが上部overlayの実測高さをLocalNavigationMapTopOverlayPxでMapScreenへ渡し、方位・ズーム・rulerをカード下へ配置する。横画面では右側に置き、左側カードの高さで下げない。
固定カード高は使わない。通常は縦積み。高さ260dp未満の操作領域では従来のcompact配置
（方位の横に縦並び＋/−/ruler）を保持し、さらに短い領域はその操作群だけスクロールする。
地図全体はスクロール領域に含めない。rulerは60dpの統合操作面に収め、MapLibre projectionから実距離を計算する。

## 地図下部の操作と自車位置

FREEは左下に案内終了・現在地から再計算、ARRIVEDでは再計算を隠す。
PRESCRIBEDにはFREE操作を表示しない。右下に経路全体・現在地/追従中。
左右とも共通のMapActionGroupを使う。地図幅560dp以上では左FREE群だけを横並びにし、右群は縦積みで地図右端に置く。右群とX=75%の車両が重ならない。
狭い画面は従来の縦並びを保持する。左右ボタンのtouch targetは48dp以上。

bottom occlusionは左右群の実測高さの最大値。双方とも32dpのattribution余白を含む。横画面の車両カメラanchorは右端群の高さを差し引かず、物理下端から56dp上に置く。
非表示のFREE群の古い高さは使わない。旧左列の高さは計算に入れない。
縦画面HEADING_UPは可視下端から外周半径25.9dp＋余白8dp、計33.9dp上に自車中心を配置する。横画面HEADING_UPは地図幅75%に投影し、Yは物理MapArea下端から56dp上の下部操作中心線に置く。右側の操作群は縦積みにして自車との重なりを避ける。
物理画面の下端と、下部操作を差し引いた可視下端は区別する。
短い領域でもvisibleHeight/2への強制フォールバックは行わない。

## 機能入口

下部「ルート」メニューから「現在地からナビ」「所定経路・一覧と保存」「経路編集」へ進む。
案内中のモード切替確認とキャンセル動作は維持する。運行情報パネル専用のcallbackは削除。
開発接続設定は既存の専用画面から利用できる。詳細操作は専用画面へ分離する。

PRESCRIBED案内中のみ「迂回」が有効。OFF_ROUTE時の「迂回を検討」、ACTIVE時の
再設定・終了は上部overlayに表示する。復帰候補、preview、明示採用、速度制限は変更しない。
[迂回操作](../detour-rejoin.md)、[RoutePlan editor](route-plan-editor.md)を参照。

## 検証

実端末のprojection.toScreenLocationで自車中心と実測bottom occlusionからの距離を比較する。
MapArea、Android MapView、Surface/Textureとbufferの寸法一致、100m南北/東西投影比を検証。
Portrait → Landscape → Portrait、警告出入り、縮小領域、FREE/PRESCRIBED/ARRIVEDを含む。
運行情報不存在、全幅カード、カード下の操作、compact補助操作の既存テストを維持する。
結果と制限は[Review015f](../reviews/015f-navigation-overlay-landscape.md)を参照。
