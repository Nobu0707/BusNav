# Traffic / Road Restrictions Foundation — Phase010

BusNav normalizes traffic information, shows its source and age, analyzes route impact, and lets the
driver explicitly consider a prescribed-route detour. **VICS/JARTIC live feed is NOT CONFIGURED.**
There is no licensed feed adapter, scraping, HTML parsing, undocumented endpoint, credential, or
claim of VICS reception. Debug data is labelled **開発用交通情報** and is entirely synthetic.

## Architecture and adapter boundary

`TrafficInformationProvider` → `TrafficSnapshot` → `TrafficStateHolder` →
`TrafficRouteImpactAnalyzer` → panel / alert / `TrafficOverlayController`.
The traffic domain imports neither Android nor a network library. The provider contract uses Flow
and an explicit suspend refresh operation. A future licensed adapter owns authentication, source
decoding, allowed refresh frequency and normalization; consumers only see the domain contract.
Source-native event and road-link identifiers are opaque metadata, never a routing identity shortcut.
`TrafficSourceInfo` includes provider ID, display name, live flag, and attribution text.

Production selects `NoOpTrafficInformationProvider`: no network calls and NOT_CONFIGURED.
`src/debug` alone contains `DebugFixtureTrafficInformationProvider`, scenario data and developer
controls. The release source-set factory returns NoOp and its developer composable is empty.
The provider instance is held by a ViewModel across rotation, but its snapshots are session memory,
not stored in the prescribed-route Room database. There is no service, notification or background poll.

Observation and local clock deadlines run only during the foreground lifecycle. There is no automatic
provider refresh. A future adapter must obey its contract's polling limits (not a one-second poll).
The local timer wakes at validity / staleness deadlines, or at most every 30 seconds for age display;
this timer performs no network operation. A new snapshot resets the deadlines.

## Source status, freshness and validity

| Status | Meaning / UI |
| --- | --- |
| AVAILABLE | Source delivered a usable snapshot; an empty list can be reported as no active received events |
| STALE | 交通情報が古くなっています |
| UNAVAILABLE | Retrieval unavailable; prior restrictions are not confirmed cleared |
| ERROR | Retrieval error; prior restrictions are not confirmed cleared |
| NOT_CONFIGURED | 交通情報サービス未接続; never “規制なし” |

Snapshot timestamps are received time and optional source update time; age prefers source update
time and falls back to receipt. Default staleness threshold is five minutes and is configurable.
Missing snapshot timestamps cannot produce fresh AVAILABLE information. Event update time falls
back to source snapshot time in details. `validFrom > now` is a separate forecast list;
`validUntil <= now` is excluded from current impacts, alerts, candidate conflicts and overlay.
No history persistence is required. Equal source/event IDs replace earlier values. A successful
snapshot removes omitted events. Error/unavailable/stale empty snapshots retain the previous
source's events and original timestamps; switching sources does not merge their data.

## Event and geometry model

Kinds: road / entrance / exit / winter closure, lane / speed restriction, accident, roadwork,
obstacle, congestion, event restriction, weather hazard, disaster, unknown. Severity is
INFO / CAUTION / WARNING / CRITICAL. Severity alone neither selects a route nor turns weather into
a closure. The event contains title, description, road name/reference, time validity and source.
Geometry is a point, polyline or polygon using domain `GeoPoint`.

FORWARD follows a polyline's vertex order; REVERSE opposes it. BOTH explicitly applies both ways.
A point or non-directional area may supply `bearingDegrees`. Without a usable bearing or BOTH,
point direction cannot be asserted. UNKNOWN never yields a confirmed blocking match.
Adapters must normalize source direction semantics before publishing the event.

## Route impact and conservative matching

`TrafficRouteImpactAnalyzer` prepares against one immutable route and its `RouteDistanceIndex`.
Geometry impacts are cached in the state holder until events or active route change. Matcher progress
only repositions that result (AHEAD / CURRENT / BEHIND / OFF_ROUTE / AMBIGUOUS). The UI receives
reliable matched progress in both FREE and PRESCRIBED, including the active detour route. Stale or
unreliable positioning suppresses distance-ahead claims instead of treating the car as at progress zero.

* Points project onto route segments. Distance alone cannot confirm a closure.
* Polylines compare segment direction and bounded overlap. Both overlap endpoints must remain in
  the corridor; long sparse segments work without relying on route vertex density.
* Polygon edges intersect each route segment; interior subintervals supply the affected progress range,
  including crossings where neither route endpoint is inside the polygon.
* Road name/reference evidence is local to the maneuver interval containing the matched segment.
  Known contradictory identities and multiple separated route occurrences remain ambiguous.
* HIGH requires consistent direction plus matching road identity or very close aligned line geometry.
  Points need road identity, or a usable bearing within five metres. Polygon road closures need identity.
  Line/area overlap also needs at least 30 metres. Close line evidence defaults to eight metres;
  the broader candidate corridor defaults to 35 metres. These are heuristics, not probabilities.
* A motorway and local road 15–30 metres apart, without matching identity, do **not** become a
  confirmed motorway closure. Tests cover both point and polyline representations.

| Event | Confirmed impact policy |
| --- | --- |
| Road, entrance, exit, winter closure | BLOCKING only at HIGH confidence; otherwise INFORMATION |
| Lane, speed, accident, work, obstacle, event restriction | RESTRICTION |
| Congestion | DELAY |
| Weather, disaster, unknown | INFORMATION, not an inferred closure |

The list sorts BLOCKING, RESTRICTION, DELAY, INFORMATION, then applicable highway decision and distance.
Primary alerts select forward events, then current or uncertain nearby information; behind events
remain in the panel. LOW/AMBIGUOUS wording says “経路付近”. Event severity is shown through its kind
and impact policy, not used to invent a recommended route. Exact road-link / OSM edge mapping is absent.

## UI and map

The single-line bottom **規制** button opens a scrollable traffic panel with provider status, receipt
time, age, route impacts, nearby/other information and forecasts. Tap an event for kind, road, title,
description, validity, update time, attribution, impact and confidence. Raw provider payloads never
appear. The traffic warning uses a symbol, text and content description; it does not rely on color.
For a confidently identified event at the next highway decision, the warning beside guidance names
the affected branch/entrance/exit; the original guidance direction is unchanged.

`TrafficOverlayController` draws dashed restriction/congestion segments, original BusNav × / 工 / ! / ≋
symbols and translucent event polygons. It retains active events and recreates sources, layers and
symbol bitmaps on light/dark or regional style reload. Layers sit above route/detour geometry and below
editable points and the raw vehicle marker. These are not copied official VICS designs.

Debug developer tools are in the traffic panel: **None / Debug fixture**, followed by CLEAR,
CLOSURE, ENTRANCE, ACCIDENT, ROADWORK, CONGESTION, FUTURE, EXPIRED, PARALLEL or MIXED.
They use a fixed synthetic east–west Kanto route near 35.68, 139.76, independent of the user's GPS.
The test fixture highway is synthetic, not an assertion that a real highway follows those coordinates.

## Explicit detour integration

PRESCRIBED forward/current BLOCKING or RESTRICTION offers **迂回を検討**. It starts the existing planner
with ROAD_CLOSURE, TRAFFIC_INCIDENT or ROADWORK reason and `TrafficDetourContext` (event/source IDs,
affected progress, optional minimum safe rejoin). Traffic reception itself does not begin a detour.
When an affected blocking interval has a reliable known end, automatic **and manual** targets must
be beyond `max(normal forward floor, affected end + 250 m)`. The same floor constrains the actual
prescribed rejoin matcher: an early rejoin inside the restriction cannot complete the detour.
The normal maximum range, destination and maneuver buffers remain in force.

Point-only or uncertain extents have no invented end. Normal candidates remain available with an
explicit unknown-end warning; the driver chooses manual target / VIA / SHAPING as needed.
No algorithm inserts a VIA automatically. There is no dynamic edge exclusion.

**Valhalla does not know these dynamic restrictions.** `TrafficDetourValidator` analyzes the computed
candidate against currently active events. HIGH-confidence closure overlap is BLOCKING_CONFLICT:
the preview's activation button is disabled, and the state holder independently rejects activation.
The driver must edit VIA / SHAPING and explicitly recalculate. Ambiguous or low-confidence conflicts
warn as POTENTIAL_CONFLICT, but do not prohibit activation. Missing/unavailable/stale information
without a known conflict produces UNKNOWN and warns that avoidance cannot be guaranteed.
A retained stale closure can still block a known conflicting candidate until it expires or is replaced.

Preview validation updates when traffic state changes; the action boundary revalidates the latest
snapshot and current time. Clearing an event never automatically activates or cancels a detour.
Existing raw-GPS departure, saved vehicle profile, prescribed snapshot/ID, editing speed lock, explicit
activation and zero-request rejoin remain intact. During an active detour the ordinary explicit replan
button remains available; traffic-context planning is offered on the original prescribed route only.

| Operation | RoutingEngine calls |
| --- | --- |
| Event receipt, alert, panel, consider detour, target / VIA selection | 0 |
| Explicit detour calculate | +1 |
| Preview, activation (including refusal), rejoin | +0 |

FREE receives the same overlays and alerts, with no automatic reroute or prescribed detour entry.
Its existing manual recalculation policy is unchanged. Congestion and speed restrictions do not
inject cost/travel time or change route duration. BusNav makes no congestion-aware fastest-route or
restriction-avoidance guarantee.

## Diagnostics, privacy and licensing

Only status / event transitions emit diagnostic names: `traffic.provider.*`,
`traffic.event.route_blocking`, `traffic.event.expired`, `traffic.detour.conflict`.
The app enables these callbacks only in Debug. No coordinates, road-link IDs, raw feeds, credentials
or device identifiers are added to release logs, documentation, commits or review archives.
Any future VICS/JARTIC or third-party adapter requires an authorized contract, specifications and
secure runtime configuration, including source attribution and retention terms. The current foundation
does not emulate a licensed provider and does not contact official websites or unofficial mirrors.

## Verification and limitations

Unit coverage includes source states, missing/old timestamps, replacement/expiry/future promotion,
point/line/polygon matching, direction, identity, parallel roads, repeated occurrences, uncertain progress,
impact policies, sort order, known/unknown rejoin floor, late candidate conflicts, manual bypass, FREE
isolation and engine call counts. Instrumentation exercises clear → closure → panel/alert → detour →
blocked candidate → manual VIA/SHAPING → explicit activation → forward rejoin, rotation, FREE and
Kanto light/dark overlay reinstatement. See [Review013](reviews/013-traffic-road-restrictions.md) for actual results.

Matching is conservative geographic evidence, not a certified lane or network-edge matcher. There is
no live provider, real-time travel-time integration, road-link exclusion, background reception, or
persistent traffic history. A synthetic fixture over a real basemap is still synthetic information.

## Phase011 handoff

SA/PA availability, rest candidates, large-vehicle stopping facilities, planned stops and operation
notes can reuse provider status/freshness/attribution concepts. Keep facility models separate from road
closure semantics. Do not claim live SA/PA availability without the corresponding licensed adapter.
The present interface is the extension boundary; real feed specifications and licensing remain prerequisites.
