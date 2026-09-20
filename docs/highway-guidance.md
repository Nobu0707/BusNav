# Phase 006: 高速道路・分岐案内

`HighwayDecisionExtractor` は `RouteGuidance` の ramp / exit / keep / merge と、空でない標識を持つ continue を抽出します。通常の右左折、未対応 maneuver は対象外です。一般道にも存在し得るランプを、高速入口と断定しません。trip 全体の highway flag は UI の切替に使用しません。

## モデルと標識

`HighwayDecision` は maneuver index、種類、施設分類、geometry 軸上の位置、`HighwaySignDisplay`、street names を保持します。生 JSON、Android、Compose には依存しません。

施設分類は sign の明示表記 JCT / Junction / ジャンクション、IC / Interchange / インターチェンジを分類時のみNFKCで全角表記をそろえ、大文字小文字を区別せず検査します。英単語内の一致や ICカードを除外し、両種類が混在すれば UNKNOWN にします。明示名がなければ EXIT / RAMP / UNKNOWN です。keep だから JCT、exit だから IC とは推測しません。

標識は出口番号、branch、toward、施設名の順に空文字を除き、trim 後の重複を順序を保って除きます。既存の `HighwaySign.consecutiveCount` を保持し、意味を推測した並べ替えはしません。branch は E1 / E20 / C4 / 国道1号を含め、そのまま角丸 badge に表示します。未知の形式も同じ扱いで、公式標識の再現や路線番号の推測はしません。

## 距離と状態

route 適用時に `NavigationProgressCalculator` が `RouteDistanceIndex` と highway calculator を構築します。抽出、正規表現、標識整形は GPS 更新のたびには実行しません。

decision の位置は `distanceAtGeometryIndex(maneuver.beginGeometryIndex)`、残距離はその位置から progress を引いた値です。Valhalla `maneuver.length` は使いません。next の距離も現在地からの距離です。

| 残距離 | phase / 表示 |
|---|---|
| > 5000 m | HIGHWAY_CRUISE。道路種別を確定できないため通常案内を維持 |
| <= 5000 m | APPROACHING_DECISION、高速カード |
| <= 2000 m | 同 phase、距離を強調 |
| <= 700 m | IMMINENT_DECISION |
| <= 120 m | TRANSITION |
| 通過 30 m 超 | 次 decision と next-next を即時繰り上げ |

current / next を常に同じリストから選びます。1000 m と1600 m の近接分岐も空白を挟まず切り替えます。負の距離は表示せず、正確な地点と通過許容範囲では 0 m です。高速表示から通常表示への戻りには 100 m の hysteresis を設けます。ランプ／出口の種類と接近 phase は独立して保持し、入口／出口を別の距離 phase に重複させません。

## 信頼性と状態保持

Phase005 の RELIABLE / UNCERTAIN / UNRELIABLE と GPS accuracy 判定を継続します。不確実時は直前の decision を保持しつつ、方向、距離、標識、次案内、模式図を抑制して「経路上の位置を確認中」と表示します。正常復帰時に現在の geometry progress で再選択します。

位置更新が止まっても時計や速度から progress を進めません。Disabled / error / 権限なしでは案内を待機表示に戻します。route と calculator、直前 snapshot は Activity ではなく既存 ViewModel の state holder が保持します。route 置換では全 cache を再構築します。process death 永続化は対象外です。

## UI・模式図

操作方向、残距離、施設／出口名、方面、路線 badge、その次を順に表示します。縦画面は画面高さの最大35%に制限し、横画面は既存3カラムの左列を使います。小画面・大フォントではカード内部をスクロールでき、地図領域を確保します。長い表示は省略できますが、カードの読み上げ説明には完全な標識・方向・距離・次案内を含めます。

Canvas は選択方向の太線と矢印、控えめな非選択方向を描きます。文字も併用し色だけに依存しません。`JunctionSchematicModel` は左／右／直進／合流と出口の topology のみで、車線数、車線境界、実際の交差形状、合流側を捏造しません。MERGE は左右を断定しない一般的な合流表示です。

## 検証・境界

unit tests は数値 enum、実 highway fixture、抽出、施設名、標識、距離軸、各 threshold の前／一致／後、通過、近接分岐、信頼性、UI formatter、状態保持を検証します。Compose tests は縦横の視認・semantics・不確実表示を検証します。live smoke は既存 `LocalValhallaAssumptions` / `LocalBasemapAssumptions` を使い、Developer Connections の保存値で接続します。サーバー停止時は JUnit assumption による SKIP です。

自動 reroute、route 自動変更、dead reckoning、音声、車線案内はありません。projector 単体は従来の full scan APIを維持します。productionはPhase008の候補index付きRouteMatcherを使用します。高架／近接並行道路を完全には識別できません。Phase007 の地図 style 全面改修は行いません。開発 endpoint / DataStore / release safety / basemap / NoOp diagnostics は維持します。

## Phase008による信頼性gate

RouteMatcherのAMBIGUOUS/UNRELIABLE、または逸脱状態がON_ROUTE以外ならHighwayGuidanceCalculatorへ非RELIABLEを渡します。sign、距離、方向、模式図の確信表示を抑制し、逸脱/位置確認bannerを優先します。元routeとdecision cacheは維持し、復帰確認後に通常の案内を再開します。route差替え時だけcacheを再構築。lane推定、auto rerouteは追加していません。[matching設計](map-matching-deviation.md)。
