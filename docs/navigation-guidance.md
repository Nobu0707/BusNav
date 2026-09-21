# Navigation guidance (Phase005 / Phase008 integration)

## Route snapshot and parsing

ScheduledRoute owns geometry and optional RouteGuidance together. Applying a candidate replaces that complete snapshot. NavigationViewModel retains the applied snapshot across Activity recreation; saved prescribed routes persist in Room and can be reopened after process death; automatic restoration of the last selected route remains outside this phase. Legacy sample routes without maneuvers display an explicit no-guidance state.

The adapter requests directions_type=maneuvers, units=kilometers, shape_format=polyline6 and the existing truck costing/options. The effective URL still comes from Developer Connection Settings at request time. The [Valhalla API reference](https://valhalla.github.io/valhalla/api/route/api-reference/) distinguishes maneuvers from narrative instructions: this mode may omit instruction/verbal strings. These optional fields are read when present; BusNav formats domain types in Japanese even when narrative strings are absent. Unknown JSON fields are ignored; unknown integer types map to UNKNOWN, never reach UI as integers, and do not invalidate the route.

RouteManeuver stores begin/end geometry indices, optional provider segment distance/time, street names and verbal instructions. HighwaySign preserves EXIT_NUMBER, EXIT_BRANCH, EXIT_TOWARD and EXIT_NAME with text and optional consecutive count. Stable deduplication keeps the first occurrence of each type/text pair. The formatter displays number, branch, toward, name, then falls back to street names when no sign is available.

Each leg is decoded separately. Its global start offset is the current merged point count, minus one only when the previous last point equals this leg's first point. Local index zero then refers to that shared boundary. A-B-C + C-D-E becomes A-B-C-D-E: local 0/1/2 maps to global 2/3/4, including end indices. Negative, reversed, out-of-range or out-of-order indices fail with MANEUVER_INDEX / INVALID_RESPONSE; they are never clamped. Optional negative/non-finite segment summaries fail at MANEUVERS. Malformed serialized fields fail at JSON_DECODE. Missing maneuver lists remain readable for existing shape-only fixtures and emit navigation.guidance.empty through the existing opt-in diagnostics.

## Distance axis and legacy projection API

RouteDistanceIndex caches Haversine segment lengths and cumulative meters once per applied route. Its array accessor returns a defensive copy. Index access rejects invalid indices and reversed intervals. NavigationProgressCalculator caches maneuver begin distances on the same axis.

Live distance is max(0, cumulativeMeters[next.beginGeometryIndex] - progressMeters). Valhalla maneuver.length describes a segment and is metadata only; it is never subtracted from live progress. The regression fixture fixes cumulative distances 0/100/250/400 m, progress 175 m, next begin index 3, provider length 50 m, and expected distance 225 m.

RouteProjector projects onto each segment in a local equirectangular plane, clamps its fraction to [0,1], compares Haversine cross-track distances and interpolates progress on RouteDistanceIndex. It handles zero-length segments and wrapped longitude. The hint initializes the best candidate and resolves ties within 1 mm; the full scan still checks all segments to preserve nearest-distance correctness. Complexity is O(geometry points + maneuvers) per fix, O(points + maneuvers) cached memory. Geometry distance rebuilding and JSON decoding never occur on location updates.

This is simple geometric projection, **not map matching**. Crossings, opposite carriageways, elevated roads, JCTs and parallel roads can project to the wrong segment even at a small cross-track distance. This describes the standalone projector API. Production now uses the Phase008 route matcher with heading, speed and continuity; road topology outside the scheduled route is still unavailable.

## Progress and uncertainty

The next maneuver is the first whose begin distance plus 15 m is not behind the current progress. The following list entry is next-next. A passed maneuver stays visible with zero remaining distance for the 15 m tolerance. A reliable backward change up to 15 m keeps the last progress; larger reverse movement is allowed. Pure calculation and the optional stateful NavigationProgressTracker are separate; The Phase008 production pipeline owns matching state and backward smoothing; it invokes the calculator with a matched projection on a background dispatcher.

NavigationProgressConfig centralizes provisional thresholds: <=30 m RELIABLE, >30 to <=80 m UNCERTAIN, >80 m UNRELIABLE. These are legacy calculator-only distance thresholds. In production Phase008 overrides reliability using match quality and deviation state (40 m maximum usable accuracy, 25 m on-route threshold). These thresholds are heuristics, not a probability of correct road matching. Neither uncertainty nor reverse movement calls Valhalla, modifies the candidate, replaces the applied route, or reroutes. Phase008 separately detects sustained route deviation.

NavigationUiState carries a compact GuidanceUiState rather than the full projection. No route: choose a route. No maneuvers: no guidance. Missing/revoked/disabled/error location: wait for location, without pretending to be at the start. Uncertain projection: display 経路付近の位置を確認中 and suppress turn, distance and next-next. A future destination says 目的地へ; near the endpoint it says 目的地です.

## UI and threading

Portrait uses a compact top card; landscape uses the existing left guide column. The map remains the largest region. Primary text and distance use large text, with a text/symbol direction, road/sign and a secondary next-next line. Long secondary text is ellipsized; the card can scroll on constrained layouts. No new touch controls are introduced. Distance formatting uses meters below 1 km, one decimal below 10 km, rounded kilometers thereafter, with Locale.JAPAN.

Existing IO/CPU routing dispatchers remain unchanged. Guidance distance-index construction and projection run on Dispatchers.Default (injectable in tests). State publication is on the owning UI scope. New fixes cancel outdated guidance jobs; applying another route cancels old preparation and resets previous progress. No coordinates or full routes are added to logs. The application LocationProvider uses applicationContext and the ViewModel does not retain an Activity.

## Verification and later phases

Real 3.9.0-a3a5631c4 local/general-road and highway fixtures, synthetic sign parsing, domain distance/projection/index tests, state-holder/formatter tests, deterministic portrait/landscape Compose tests, Activity recreation and optional live routing/UI smoke cover the boundary. Live tests reuse LocalValhallaAssumptions and skip if the configured endpoint is unavailable. Test position injection exists only in androidTest; manual emulator testing uses emulator mock GPS.

Phase 006 reuses the typed sign model for IC/JCT emphasis, route badges and schematic junction guidance. Lane guidance and SA/PA displays remain future work. Phase008 implements local route matching and heuristic deviation evidence, not calibrated statistical confidence. Voice, automatic rerouting, detour/rejoin, traffic restrictions and production backend integration are not implemented here.

## Phase006 高速案内

従来の一般道案内に加え、近接 ramp / exit / keep / merge は専用カードに切り替えます。距離軸、projection reliability、accuracy、ViewModel 保持は共通です。pin 済み Valhalla の 25 / 37 / 38 は公式 enum に定義された merge で、未対応値は UNKNOWN を維持します。[高速案内の仕様](highway-guidance.md)。

## Phase008による位置判定の更新

productionでは最近傍投影を直接案内に使わず、RouteMatcherの候補評価を通したprojectionをcalculatorへ入力します。MATCHEDかつON_ROUTEのみ通常案内。それ以外は距離・方向・その次の指示を抑制し、位置確認と逸脱bannerを表示します。復帰には3 fix/2秒を要求します。raw GPS markerは保持し、自動rerouteしません。旧projector/calculator単体APIは後方互換の幾何計算として残します。[仕様と制限](map-matching-deviation.md)。

## Phase008.5C identity and restored snapshots

`activePrescribedRouteId` retains library identity independently of the calculation route ID. `NavigationMode.PRESCRIBED` is the current mode; FREE remains a future implementation.
Opening a saved route restores all geometry, maneuvers and signs with zero RoutingEngine calls and rebuilds existing matcher/progress/highway caches. Selection does not start the navigation theme; “ナビに使用” explicitly starts it. Library and editor remain LIGHT. Active library deletion is blocked until use is ended.
See [prescribed route library](prescribed-route-library.md).