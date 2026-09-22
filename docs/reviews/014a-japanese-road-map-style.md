# Review014a — Phase010.5A Japanese road map style

## Scope and baseline

`BASE_SHA=c0e1f4d819e75cec31a92e42047d63fbba8468e8` (actual starting HEAD).
Preflight status/log/diff and both reference images were checked. Pre-existing
`.vscode/` and `gradle/gradle-daemon-jvm.properties` remain untouched and untracked.
No Phase011, Valhalla graph, runtime PBF/MBTiles or copied reference runtime asset is included.

Reference images: `images/reference_route_number_signs.png` and
`images/reference_navigation_heading_up.jpg`. Both are reference-only Git files.
The signs image informed the original vector backgrounds; the page layout was not copied.

## Real tile audit and regeneration

The audit reads real MBTiles protobuf attributes at multiple Kanto sites, without
network classification from highway class. `audit-road-properties.py` reproduces it.

| Evidence | Original attributes / conclusion |
| --- | --- |
| Shibuya Route 246, z14/14549/6452 | `class=trunk`, `ref=246`, `network=road`, `route_1_network=JP:national`, `route_1_ref=246` |
| Shibuya urban expressway | `class=motorway`, `ref=3` or `C2`, relation network `首都高速道路` |
| Tomei, z14/14546/6454 | `ref=E1`; some segments have `route_1_network=JP:E`, some have no relation |
| Hachioji, z14/14532/6452 | `ref=E20`; prefectural `32`, `506`, `521` with `JP:prefectural[:tokyo]` |
| Hachioji concurrent routes | `ref=16;20`, separate national route relations |
| Ken-o, z14/14529/6453 (post-generation audit) | ref=C4;468, JP:E/C4 and JP:national/468; canonical expressway C4 |
| Local roads | minor/service with no usable route network; numeric refs alone also occur on non-national roads |
| `transportation` | class/subclass/brunnel etc., **no ref/network/route attributes** before regeneration |
| `transportation_name` | ref/network/route_N_network/ref; no `network_type` field in either audited layer |

Therefore **both Kanto and Chubu MBTiles were regenerated** from existing regional PBFs.
Generated files stay in the external WSL basemap data directory. Japan Valhalla was not rebuilt.
The final Kanto MBTiles is 277,340,160 bytes; Chubu is 302,845,952 bytes.
Both pass SQLite quick_check and layer/format/bounds validation. Additional Chubu audits cover Nagoya, Gifu, Shizuoka and Kofu; Kofu national 52/358/411 and prefectural 5 are explicitly classified, while unsupported bare refs remain neutral. Actual post-generation
tiles have `route_network` and `route_ref` in both required layers and preserve OMT fields.

Planetiler remains 0.10.2, git `0e5588c4a6e8c29a270a33afe8df62027d889604`, image digest
`cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89`;
OpenMapTiles 3.16.0; TileServer GL 5.6.0 at the existing pinned digest.
The first trial revealed that adding a second relation-info object hides the original;
the final extension keeps the original info and adds a separate lookup. Its regression
test explicitly checks that `route_1_network=JP:national` survives.

## Classification and presentation

See [complete policy](../japanese-road-visual-language.md). Relation/explicit network first,
trusted provider second, exact E/C motorway fallback third, otherwise neutral.
Concurrent routes use category priority then the first usable ref from ascending relation
IDs; semicolon refs use the first token. No generated E number from a road name.

Original green rectangle / blue onigiri / blue hexagon, white 13px dynamic text,
four vector backgrounds total. Text and icon share one feature and collision box policy.
Expressway/national/prefectural spacing is 420/550/680px, minimum zoom 7/8/13;
only one-/two-digit national refs at zoom 8–9. Road names are retained below shields.
Road colors are blue/red/green with theme-specific brightness; shields keep their colors.
Road casing and neutral fallback remain distinct. Tunnel/bridge strokes use a lighter category tint: identical stroke/fill colors would make the structural cue disappear. A static regression requires the stroke tint to differ from the base fill.
Navigation route casing and the existing OverlayLayerOrder are preserved.

## Tests and evidence

Build-local evidence directory: `build/phase0105a/` (not committed or source archived).

* Unit/static: **362 PASS, 0 failures, 0 skipped** (356 existing + 6 added), covering classifier, normalization, unknown/multi-ref, shield policy and colors/parity.
* Profile integration: actual OMT generation preserves standard fields and enriches both layers.
* Style validation: official MapLibre validator in pinned TileServer; four styles and actual layer schema.
* Build: test, lint, assembleDebug, assembleRelease, assembleDebugAndroidTest: **PASS**. Lint: **0 errors, 20 warnings, 1 hint**.
* Android: JapaneseRoadPresentationTest and all prior Phase010 regression tests. Physical SOG06 / Android 14: **70 PASS, 0 failures, 0 skipped** on the implementation run. Pixel 8 AVD / Android 16: **70 PASS, 0 failures, 0 skipped**. Both full suites retain all Phase010 traffic/overlay regressions.
* Live TileServer: Kanto/Chubu Light/Dark style, TileJSON, PBF and Japanese glyph smoke.

The final-head formal workflow reruns all Gradle checks and the complete physical-device suite. The emulator focused presentation run after adding explicit rendered assertions for all four traffic symbol kinds also passed: **2 PASS, 0 failures, 0 skipped**. Authoritative final-head checks are included in both review archives.

## Visual smoke

Live Tokyo/Kanto screenshots cover zooms 8/10/12/14/16, light/dark; suburban Hachioji and
Chubu are also captured. Route/traffic views in both themes verify the cyan route with its casing and visible closure, accident, roadwork and congestion symbols. The synthetic source checks centered `1`, `12`, `246`, `E1`,
`E20`, `C4`, and prefectural `12`, `34`, `300`. Its line-center placement isolates shape
alignment; production uses repeated line placement. Screenshots are local evidence only.
An initial cold run queried before tile rendering had completed; the final test waits for
the native fully-rendered frame callback before querying and capturing.

## Limitations and archives

Incomplete relations yield neutral gaps; unknown networks stay neutral. Only one canonical
ref is shown. No prefecture text is inferred. Generic web previews need their own registration
of the application shield images; Android installs them on each reload. No licensed live traffic
feed was introduced. Traffic regression uses the existing explicitly labelled debug fixtures.

Implementation commit: `a5fec1b` — feat: add Japanese road shields and route-class map styling.
Final HEAD/commit list and both archive results are recorded by archive metadata and the final
report. Both review archives must use the BASE_SHA above, with final-head checks and ZIP self-checks.

Status: **PASS / COMPLETE**, conditional on the final-head formal checks and both archive self-checks recorded in the delivered archives. The final response confirms their actual outcomes.
