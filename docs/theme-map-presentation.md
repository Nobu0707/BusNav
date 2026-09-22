# Phase008.5A — Theme / Map Presentation / Bottom Navigation

## Theme policy

基本LIGHT。OSのダーク設定はテーマ判定に使わない。
dark = navigationActive && (isNight || isTunnel)。位置未取得時はLIGHT。

| State | Result |
| --- | --- |
| inactive + night / tunnel | LIGHT |
| active + day + no tunnel | LIGHT |
| active + night | DARK |
| active + day + confirmed tunnel | DARK |
| active + day + confirmed tunnel exit | LIGHT |
| editor / Developer Connections | LIGHT |

Phase008.5D では「案内開始」/「ナビに使用」と「案内終了」が明示操作です。
NavigationRoute は isNavigationStarted と activeRoute を持つ案内画面を ACTIVE とします。
経路閲覧、編集、候補プレビュー、設定、library は inactive。maneuvers の有無から開始状態を推測しません。
逸脱や一時的な測位不確実性はナビセッションを終了させない。

Compose再描画とMapLibre style reloadで切り替える。Activity再起動は不要。
WindowInsetsControllerのstatus/navigation barアイコンも同期する。判定は最大1秒間隔。

## Local sunrise / sunset

SolarCalculatorはGeoPoint + LocalDate + ZoneIdから計算するpure Kotlin。
[NOAA General Solar Position / Sunrise-Sunset equations](https://gml.noaa.gov/grad/solcalc/solareqns.PDF)
のfractional year、均時差、太陽赤緯、天頂角90.833度、時角を使用する。
外部API、ネットワーク、端末の照度センサーには依存しない。夜はnow < sunrise || now >= sunset。
閏年の分母366、日付変更線とcivil date、ZoneId/DST、極昼/極夜を扱う。
地形・建物・気象による実際の日の出の差は対象外であり、天文台精度の計算ではない。

SolarDayCacheは日付/ZoneId変更または前回計算位置から5km以上の移動で再計算する。
夜判定はキャッシュせず毎tick評価。位置なしはfalse。
Android API23から動作するようにcore library desugaringを有効化した。
[AndroidのAPI desugaring](https://developer.android.com/studio/write/java8-support)を参照。

東京駅付近35.6812,139.7671、2026年春分/夏至/冬至の概算公開時刻に対し±15分、
sunrise直前/一致、sunset直前/一致、欠測、日付/位置/zone変更、閏日、DST、極地をテスト。

## Tunnel provider and audit

現行Planetiler/OpenMapTiles 3.16.0のKanto/Chubu TileJSONを監査し、
transportation.fields.brunnel = String（z4–14）を両方で確認した。
[OpenMapTiles transportation schema](https://github.com/openmaptiles/openmaptiles/blob/master/layers/transportation/transportation.yaml)
のbrunnel=tunnelを使用する。既存styleのroad-tunnelsも同属性を参照する。

TunnelStateProviderはTUNNEL / SURFACE / UNKNOWNを返す抽象。
TransportationTunnelProviderは現在のloaded VectorSourceからtransportationを照会し、
motorway/trunk/primary/secondary/tertiary/minor/serviceのLineString/MultiLineStringだけを対象とする。
現在位置から線分までの距離が15m以内の最寄り道路の明示的tunnel属性を確認する。
鉄道/地下鉄、GPS欠落、暗さ、速度低下から推測しない。異なるtunnel属性の道路が最寄り距離+5m
以内に並存するとUNKNOWN。水平位置だけで積層道路を識別できない限界を保守的に扱う。

style/source未load、fallback、近傍featureなし、照会失敗はUNKNOWN。
位置のaccuracy不明/30m超、単調時刻不明/10秒超もUNKNOWN。
brunnelが付かないloaded roadはschema上の通常路面として扱う。
現在cameraにloadされたタイルだけが利用可能なので、地図を遠くへパンすると検出できない。
サーバーなし、圏外、新規タイル未取得、トンネル内の長いGPS欠落では検出を保証しない。
実detectorは利用可能であり、実タイルのtunnel/surface featureを合成GPSで通過するinstrumentationで確認する。

TunnelHysteresisは単調時計で進入2秒、退出4秒の連続根拠を要求する。
UNKNOWNは根拠を中断し、最後の既知観測から最大8秒だけ既存判定を保持してからfalseへ戻す。
style切替に伴う短いtile欠測で点滅させず、欠測を無期限にtunnel扱いしない。
inactive時はreset。時計逆行もresetする。

## Basemap and overlay preservation

Dark template: tools/basemap/style/busnav.json。
Light template: tools/basemap/style/busnav-light.json。
両方で道路・水域・鉄道・建物・日本語labels・glyphsを維持する。
prepare-regions.pyからKanto/Chubu各2色を同一MBTilesに対して生成する。
URLは /styles/busnav-{kanto|chubu}[-light]/style.json。
旧busnav[-light]はChubu互換。既知BusNav以外のcustom URLは自動書換しない。

start-tileserver.shは生成後にcontainerを再作成し、新しいconfig/styleを確実に読み込む。
MBTilesやrouting graphの再生成は不要。fallbackにも明暗別assetを用意した。

MapControllerはMapViewとcamera、latestRoute、latestRoutePlan、latestLocationを保持し、
style load後にroute、START/DEST/VIA/SHAPING、vehicleのsource/layerを再登録・再描画する。
candidateは既存のactiveRoute描画引数で同じ復元経路を通る。
Phase008のdeviation overlayはnative layerではなくCompose DeviationBannerであり、
style reloadの外側に保持される。routing/matching/deviationの状態はstyle変更で再初期化しない。

## Colors / arrow / road width / bottom bar

全ComposableのColor.Black / 0xFF000000 / Color(...)と文字色指定を監査した。
hardcoded黒文字はなく、問題はMaterialTheme単体でLocalContentColorが黒のままになる
bare Textだった。BusNavThemeがlight/darkColorSchemeとLocalContentColor(onSurface)を提供する。
guidance/highwayのCard/Surface、deviationのonErrorContainer/onSurfaceVariant、
editor/settingsのonSurfaceVariant/error、bottom buttonのMaterial content colorを維持する。
パレット定義以外のComposableに固定文字色は追加していない。

vehicle SymbolLayerのiconSizeは1から2。72px bitmapの形状、center anchor、
map rotation alignment、iconRotate（heading）、位置sourceは変更しない。

road line-widthは共通zoom stops 4/8/12/16/18/20/22で単調増加。
z16のfill幅はservice=3、minor(residential)=4.5、tertiary=6.5、secondary=7.8、
primary=9、trunk=10.5、motorway=12。z18=1.8倍、z20=3倍、z22=4.5倍。
casingはfill+2。trunkをprimaryから独立させ階層を維持する。
route overlayはz4/12/16/20/22で3/5/8/12/16、casingは+4。

下部は「ルート / 迂回 / 規制 / 音声 / 表示」。意味が明確な短縮のみ採用し、
ルート編集のaccessibility説明と操作は保持する。
maxLines=1、softWrap=false、中央配置。固定ボタン幅を撤去し、
portraitはweight、landscapeは列幅に追従。320dp級portraitと狭いlandscape列、
文字倍率1.0/1.3で1行かつoverflowなしを検証する。
迂回等の将来ボタンは従来どおり未実装。Phase009 Detour/Rejoinは未着手。

## Validation and evidence

PresentationPolicyTest / RoadWidthModelTest / PresentationComposeTest /
BasemapHotReloadTest / ThemeRuntimeSmokeTestを追加・拡張。
live smokeは現在の実道路タイルに合成位置と注入Clockを使い、端末時計・実GPS traceを変更しない。
地域ごと両方向のstyle switchでroute、全地点種別、deviation、vehicle size/heading、cameraを確認する。
画面証跡・logs・APKはbuild配下のみでcommit/archive対象外。
最終結果は [Review011a](reviews/011a-ui-theme-map-refresh.md) を参照。

## Phase008.5D activation

navigationActive の source は isNavigationStarted と activeRoute の存在です。saved ID の null/non-null や maneuvers の有無から activation を推測しません。FREE preview・再計算 preview・editor・library は LIGHT、明示開始後の案内画面のみ夜間/トンネル条件で DARK に切り替わります。

## Phase010.5A — Road presentation update

The prior statement that no MBTiles rebuild is needed applies to the Phase008.5A theme-only
change. Phase010.5A enriches transportation layers and requires rebuilding Kanto/Chubu once.
Both themes now use safe route-network blue/red/green road colors, neutral unknown roads,
and original Japanese route shields with dynamic white numbers. Road width hierarchy and
casing remain unchanged. Route/traffic/vehicle overlays retain their existing ordering.
See [Japanese road visual language](japanese-road-visual-language.md) and
[Review014a](reviews/014a-japanese-road-map-style.md) for classification, density and evidence.


## Phase010.5A.1 road details

The profile schema is now 2: separate `urban_expressway`, preserved `route_source_network`, and additive `busnav_expressway_facilities` / `busnav_named_intersections` point layers. Both Kanto and Chubu must be regenerated from existing PBFs; Japan Valhalla remains unchanged. All four regional/theme styles use identical filtering and density rules; intersection background/text colors follow the theme. Android registers all vector backgrounds on every reload.

Shields are globally gated by projected visible map span (show <=2,200 m, hide >2,600 m), with routes inserted below shields and critical traffic above. The Route Editor passes its bottom occlusion into the same calculation. See [road details policy](map-road-facilities.md) and Review014a1. Phase010.5B camera/compass/vehicle work is not included.
