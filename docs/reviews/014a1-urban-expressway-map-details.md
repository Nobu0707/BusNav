# Review014a1 — Phase010.5A.1 urban expressway map details

## Baseline and scope

BASE_SHA: `ccd997f412b2b3f51f60392887eca7311b63789c`, read from the actual repository before edits. [Review014a](014a-japanese-road-map-style.md) is accepted as PASS/COMPLETE. Its road palette, national shields, traffic fixtures and route/detour behavior are retained. Phase010.5B navigation camera/compass/vehicle changes are excluded.

Pre-existing `.vscode/` and `gradle/gradle-daemon-jvm.properties` remain untouched/untracked. PBFs, MBTiles, generated glyphs, screenshots and local logs remain outside the tracked source. Final HEAD and commits are recorded by archive metadata and the delivery report.

## Official source and artwork

The [Shutoko official guidance page](https://www.shutoko.jp/driving/convenience/guidance/) was opened on 2026-09-22. Its route-mark table covers C1/C2; 1/2/3/4/5/6/7/9/10/11; B/Y; K1/K2/K3/K5/K6/K7; S1/S2/S5. Ring arrows indicate direction; the exit numbering section distinguishes radial/circular ordering and odd/even directional numbering. Exit ref is not a route ref.

The official route marks use a visual family distinct from nearby national E/C rectangles. Original BusNav vector paths reproduce the broad visual language, not official pixels: green with white borders/text, a curved-bottom urban shield and circular Shutoko C1/C2. At these small sizes direction arrows are omitted; audited directionless relation data does not justify a direction arrow. The national rounded rectangle remains. Nagoya uses the generic urban shield, including its source C1 ref; no Shutoko-specific ring is imposed on it.

## Actual PBF and tile audit

`audit-urban-pbf.py` reads the existing regional PBFs using a streaming stdlib decoder. It records explicit route network/operator/ref/name and sample point tags. `audit-road-properties.py` decodes regenerated MVT fields, including signed integer ranks.

| Region | Actual relation network | Actual refs | Operator |
| --- | --- | --- | --- |
| Kanto | 首都高速道路 (24 relations) | C1, C2, Y, B, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, S1, K1, K2, K3, K5, K6, S2, S5 | absent on audited relations |
| Chubu | 名古屋高速道路 (9 relations) | C1, 5, 2, 6;455, 16;449, 1, 11;515, 3, 4;456 | absent on audited relations |

Kanto has 1,320 motorway_junction nodes, 1,270 toll_booth nodes and 30,873 candidate named intersections; Chubu has 1,703 / 852 / 19,537 respectively. These are input counts, not rendered icon counts: motorway connectivity and display rules further restrict output. K7 is supported by normalization but was not found under the audited explicit Kanto network; no name-based substitute is added.

Generated tiles preserve `ref`, `network=road`, `route_1_network`, `route_1_ref`, and names. Both transportation layers add `route_network`, `route_ref`, `route_source_network`; `route_operator` is copied only if actually present. Tests verify OMT relation information survives enrichment. Kanto samples cover Shibuya, Miyakezaka, Tanimachi, Takebashi, Hakozaki, Ohashi, bayshore, Kanagawa, Tomei, Hachioji and Ken-o. Chubu samples include Nagoya, Gifu, Shizuoka and Kofu.

## Classification and canonical refs

`URBAN_EXPRESSWAY` is distinct from national `EXPRESSWAY`. Exact audited network membership identifies Shutoko/Nagoya. Numeric motorway refs and names containing 高速 are never sufficient. Bare C1/C2 without evidence remain neutral; explicit national `JP:E`/`JP:C` remains national. National C4/E1/E20 retain safe motorway fallback and the existing rectangular shield.

Urban membership takes precedence over a concurrent national relation. First usable candidate follows the deterministic relation ordering and first-token normalization (`6;455` → `6`). E refs attached to an urban relation do not become urban route marks. Unknown numeric motorways remain OTHER.

## Profile changes and regeneration

Both regions were regenerated from existing PBFs with the pinned Planetiler 0.10.2/OpenMapTiles 3.16.0 stack. Japan Valhalla was not rebuilt and no PBF was downloaded. Output remains under the external WSL basemap data directory:

- Kanto: 279,830,528 bytes.
- Chubu: 304,676,864 bytes.
- Both: SQLite quick_check PASS, PBF format, 15 layers, maximum zoom 14.

New point layers: `busnav_expressway_facilities` and `busnav_named_intersections`. The default OMT version and fields remain unchanged. Runtime style metadata `busnav:road-schema=2` marks the presentation contract.

## Facility data, icons and green labels

Actual source samples include 三宅坂ジャンクション/谷町JCT/大橋JCT, 霞が関 (`highway=motorway_junction`, ref 23), 渋谷出口 (ref 303), and 飯倉料金所 (`barrier=toll_booth` connected to motorway_link). Facilities also cover national expressways; no urban membership is fabricated for unqualified nodes.

Explicit source JCT/ジャンクション/入口/出口 designations identify variants. Otherwise a motorway_junction uses generic IC/access; toll_booth requires actual motorway/motorway_link connectivity. Mainline toll designation is preserved but currently shares generic toll artwork. Direction/ETC-only/operator are never guessed.

Original green/white access, branching-JCT and toll-gate icons have separate dynamic name labels. Stretchable background centers preserve crisp corners/borders; `icon-text-fit=both`, white text, viewport alignment and 2.6 em offset keep the name apart from the facility icon. Source names are preserved and no arbitrary IC suffix is appended. Collision handling can suppress individual labels/icons in crowded locations.

## Intersections and density

Only explicitly named traffic_signals or junction=yes nodes qualify. Bare named nodes and named pedestrian crossings are excluded. No intersecting road names are combined into an invented intersection name. Signalized nodes shared by >=2 primary/trunk ways receive display rank 0; others rank 1. Way splitting can affect this heuristic, which only influences display priority.

Intersection backgrounds are neutral light/dark with readable theme text, distinct from green motorway labels. Overlap and ignore-placement are false. Rank 0 begins below a 3 km span; normal names below 1.5 km, plus minzoom/collision constraints.

## Visible span, hysteresis and performance

`VisibleMapSpanCalculator` projects top-center/bottom-center of the unobscured map rectangle, then uses a great-circle distance. Route Editor bottom occlusion is included. Vertical span is primary in portrait and represents the shorter screen dimension in landscape; future general occlusion integration belongs to Phase010.5C.

All four shield categories are hidden by default and globally gated: show <=2,200 m, hide >2,600 m; 2,400 m retains the previous state. Invalid/empty projection hides shields. JCT <=5 km, access <=3 km, toll <=2 km. Per-category minimum zoom/spacing still apply. Camera/layout/occlusion events coalesce at 75 ms, with style property writes only on state changes.

## Z-order, reload and traffic

`busnav-shield-anchor` positions active, candidate and detour lines below shields. Facilities sit above shields; traffic areas/lines/critical symbols sit above facilities; route points and current position remain uppermost. Every style reload reinstalls all original vectors, clears the visibility cache, reapplies span and restores ordering. Fallback styles without the sentinel remain supported.

Light/Dark and Kanto/Chubu share the layer/filter/density contract. The original road-class palette, bridge/tunnel tint, active cyan route and critical traffic fixtures remain in place. Synthetic geometry places actual route lines under all shield families for direct visual/order verification.

## Quality, emulator and physical evidence

Build-local evidence: `build/phase0105a1/`. Unit tests cover national E1/E20/C4, urban C1/C2/3/B/K/S, same ref with different networks, unknown numeric motorway, source facility designations, explicit intersection extraction/rank, measured rectangle, 1.5/2.0/2.4/3.0 km hysteresis, route/traffic ordering, style parity and collision controls. Real profile tests cover original relation preservation and custom facilities.

Initial unit/build validation passed (368 tests before adding the final ordering assertion; final counts are in archive checks). Initial lint: 0 errors, 20 warnings, 1 hint. The pinned MapLibre validator passes all four live styles and actual source layers. Final test/lint/Debug/Release/test-APK and full connected results are authoritative in the archive checks.

Focused emulator presentation suite: **3 PASS, 0 failures, 0 skipped**, including portrait/landscape span and route-under-shield screenshots. Emulator: Pixel 8 / Android 16. Physical: SOG06 / Android 14, available for final full-suite validation. The new instrumentation covers real facility/green labels/intersections, C1/C2, synthetic urban/national shields, route/traffic ordering, Light/Dark/Kanto/Chubu reloads and portrait/landscape measured spans. Screenshots contain public map positions and synthetic traffic only; device identifiers are excluded from review artifacts.

## Limitations and archives

Incomplete network relations leave neutral gaps, including unaudited K7 segments. Only one canonical route ref is shown. Ring direction arrows are omitted. Facility names come from available source data, and unnamed toll gates show only icons. Connected motorway facilities are included nationally as well as in urban networks. Same-name OSM nodes may yield multiple labels if far enough apart. General top/side occlusion handling is deferred to Phase010.5C. No production live traffic provider is introduced.

Both review and full-review archives must be built with this BASE_SHA and the final HEAD after formal checks. Their metadata records commits, validation results and ZIP self-checks. Large generated data and local screenshots remain excluded. Completion status is confirmed in the delivery report only after those checks finish.
