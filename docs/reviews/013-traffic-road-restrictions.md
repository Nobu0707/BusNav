# Review013 — Phase010 Traffic / Road Restrictions Foundation

## 1. Scope

Provider-neutral traffic domain, NoOp/Debug providers, source freshness, conservative route impact,
traffic panel/overlay/alerts, explicit Phase009 detour handoff and candidate activation protection.
See [complete design and policy](../traffic-road-restrictions.md).

## 2. BASE_SHA and preflight

`BASE_SHA=9922b1097dc5d159ead7d8fba2ccbf1637dc8807`, read from the actual starting HEAD.
The requested status/log/rev-parse/diff and whitespace-insensitive diff preflight was executed.
Tracked tree was clean; pre-existing `.vscode/` and `gradle/gradle-daemon-jvm.properties` remain
untracked and untouched. They are excluded by existing review-archive policy.

## 3. Phase009 review and architecture audit

Starting HEAD and Review012 represent Phase009 COMPLETE, not Phase010. The audit covered
NavigationMode/StateHolder/UiState, RouteMatcher/DistanceIndex/DeviationDetector, PrescribedRouteRecord,
DetourStateHolder/Reason/Draft, RejoinCandidateGenerator, DetourOverlayController, MapController,
NavigationScreen and its placeholder 規制 button, Developer Connections and diagnostics.
Saved route/profile/identity and explicit routing boundaries were retained.

## 4–10. Sources, models and Debug provider

| Topic | Implementation / evidence |
| --- | --- |
| 4. Official-source caveat | No licensed VICS/JARTIC feed or credentials were supplied; no scraping, HTML parsing or undocumented endpoints were added |
| 5. Provider abstraction | Flow snapshots + explicit suspend refresh; future adapter handles its own authorized decoding/authentication |
| 6. Provider status | AVAILABLE / STALE / UNAVAILABLE / ERROR / NOT_CONFIGURED; empty available differs from disconnected |
| 7. Event model | 14 kinds, severity, direction, road name/ref, validity, source, optional native event/link IDs |
| 8. Geometry model | Domain GeoPoint point, polyline, polygon; segment projections and polygon intersections |
| 9. Freshness/validity | Configurable five-minute default; source-time/receipt fallback, future separate, expired excluded; local deadline timer |
| 10. Debug provider | Debug source set only; None, Clear, Closure, Entrance, Accident, Roadwork, Congestion, Future, Expired, Parallel, Mixed; fixed synthetic Kanto data |

Release defaults to NoOp. Compiled release DEX was checked for DebugFixtureTrafficInformationProvider,
TrafficFixtureScenario and fixture-selection symbols: none present. The source label is
「開発用交通情報」, never “VICS受信中”. Credentials and native payloads are outside the domain/UI boundary.

## 11–17. Matching and UI

| Topic | Policy / evidence |
| --- | --- |
| 11. Route impact | Immutable route distance index; event bounding filter; cached geometry matching; reliable active matched progress supplies ahead/current/behind |
| 12. Confidence | HIGH/MEDIUM/LOW/AMBIGUOUS; matching road identity or very close aligned geometry plus direction; conservative point handling |
| 13. Parallel-road test | Point/polyline closures 15, 22 and 30m from motorway do not become BLOCKING with unrelated/missing road identity |
| 14. Direction | Forward/reverse relative to geometry order, optional point bearing, BOTH and UNKNOWN; reverse road rejected and unknown never confirmed blocking |
| 15. Map overlay | Original ×/工/!/≋ symbols, dashed segment and polygon fill; style reload reinstalls sources/layers/images, vehicle stays above traffic |
| 16. Panel/button | Single-line 規制 enabled; source/status/age, route/other/forecast lists; event details include validity, update, attribution, impact and confidence |
| 17. Alerts | Forward first, then current/uncertain; uncertain progress has no distance claim, low confidence says 経路付近; icon/text/accessibility description |

Severity policy: confirmed closure/entry/exit/winter → BLOCKING; lane/speed/accident/work/obstacle/event
restriction → RESTRICTION; congestion → DELAY; weather/disaster/unknown → INFORMATION. A low-confidence
closure is INFORMATION. Same-level ordering is distance ascending; a farther highway closure cannot
displace a nearer closure. Road identities are compared within each route maneuver interval.

## 18–26. Navigation and detour integration

| Topic | Implementation / evidence |
| --- | --- |
| 18. PRESCRIBED | Forward/current blocking/restriction offers explicit 迂回を検討; no automatic detour |
| 19. Reason/context | ROAD_CLOSURE / TRAFFIC_INCIDENT / ROADWORK mapper; event/source IDs and affected progress range |
| 20. Rejoin after restriction | Known HIGH-confidence blocking end + 250m raises automatic/manual target floor and actual prescribed rejoin matcher floor; unknown point end is not invented |
| 21. Valhalla limitation | Dynamic fixture restrictions are not in the graph; no exclusion edges, automatic VIA or congestion cost injection |
| 22. Candidate validation | Every computed detour checked against active events; refreshed while previewing and checked again at activation |
| 23. Activation protection | HIGH closure overlap disables UI and holder rejects; ambiguous conflict warns; manual VIA/SHAPING + explicit recalculation can yield valid candidate |
| 24. FREE | Same overlay/alert, no traffic-driven recalculation, no prescribed detour entry; existing manual recalc unchanged |
| 25. Congestion | Display/delay information only, no fastest-route or avoidance guarantee; speed restriction does not alter duration |
| 26. Highway | Confident road/progress overlap at next highway decision adds restriction warning near guidance; direction instruction is not replaced |

Call counts verified: receipt/alert/panel/consider/target/VIA = 0; explicit calculate = +1;
preview/activation/rejected activation/rejoin = +0. Removal of an event neither activates nor cancels
a detour. Provider errors retain last-known events with status warnings instead of declaring clearance.
Raw GPS departure, stored bus profile, prescribed ID/snapshot, FREE isolation and speed lock remain intact.

## 27. Diagnostics / privacy / licensing

Only transition names are added: traffic.provider.*, traffic.event.route_blocking,
traffic.event.expired, traffic.detour.conflict. The app's release callback is silent.
No traffic credentials, raw VICS payload, personal GPS trace, device serial, runtime DB, local environment,
PBF/MBTiles/graph, APK/build/logcat or secrets are committed. Source attribution is represented explicitly.
No background service, polling network scheduler or traffic persistence was introduced.

## 28. Unit tests

**356 tests PASS, failures 0, skipped 0** (308 existing + 48 added). The last added test proves
that same-level highway priority cannot override ascending distance.

Includes status/freshness/future/expiry/replacement/failure retention, point/line/polygon, sparse geometry,
road identity, direction, parallel roads, repeated route occurrences, unreliable progress, impact policy,
distance ordering, rejoin floors, conflict activation, manual bypass, event removal and engine counts.

## 29. Instrumentation

Three new tests: two TrafficFlowTest scenarios and one TrafficOverlayTest. The full suite contains
68 tests (65 existing + 3 new). Clear → closure → panel/alert → explicit detour → after-end candidates →
conflicting preview denied → manually add VIA/SHAPING → recalculate → explicit activation → rejoin.
FREE has alerts but no traffic reroute. Rotation retains source/closure state. Kanto light/dark reload
retains traffic point/line/polygon sources, symbols and layer order.
Manual point injection in the new deterministic flow uses the same holder callbacks as the UI cursor;
the existing Phase009 tests continue to exercise actual cursor movement and location selection.

## 30. Emulator

Pixel 8 AVD / Android 16: **full suite 68 PASS, failures 0, skipped 0** on the implementation build.
Final-head traffic/overlay smoke is also recorded with the archive checks. The fixture selection survives
Activity recreation; final coverage additionally rotates the closure warning between landscape and portrait.

Screenshots after waiting for style/layer installation were visually inspected: Kanto basemap,
closure × / dashed segment, source-labelled alert, forward target and disabled conflicting activation
are visible and readable. The landscape operations panel reserves 80dp so its detail remains visible
beside a traffic warning. Screenshots remain local build artifacts, not archive source files.

## 31. Physical Android

SOG06 / Android 14: **full suite 68 PASS, failures 0, skipped 0**. The same suite is rerun by
`run-review-checks.ps1` against final HEAD; its authoritative result is in the archive checks.

State=device; device selection uses ANDROID_SERIAL without publishing its value. Tests use public
synthetic locations, not real driving or personal GPS history.

## 32. Live provider status

**VICS/JARTIC: NOT RUN / NOT CONFIGURED.** No contract/specifications/credentials were supplied.
This is an explicit scope exclusion, not a failed live test and not a claimed live VICS PASS.

## 33. Regressions / quality

test / lint / assembleDebug / assembleRelease / assembleDebugAndroidTest: **PASS**.
Lint: **0 errors, 20 warnings, 1 hint**; new advisories concern the existing debug Log convention and
KTX color conversion suggestions. `git diff --check` passes. Final-head formal check results are archived.

Existing Japan Valhalla, Kanto/Chubu basemap, general/highway guidance, matcher/deviation, theme,
route editor, prescribed library, FREE, detour/rejoin and Developer Connections tests are retained.
WSL sessions initially ended after commands and made servers unavailable; a temporary WSL keeper
and existing containers restored service. Tests use runtime loopback URLs and adb reverse; no firewall,
LAN config, data regeneration, new image pull or committed environment override was needed.

## 34. Limitations

No actual licensed feed, certified lane/edge matching, traffic travel-time integration, dynamic graph
exclusion, background reception or persistent history. Confidence is conservative geometric evidence,
not a legal or routing safety guarantee. A synthetic line over a basemap is not a real road restriction.
Unknown event extent requires driver confirmation. Missing/stale data cannot guarantee avoidance.

## 35. Phase011 handoff

Provider status/freshness/attribution and explicit planning boundaries are ready for SA/PA / operation
support extensions. Keep facility availability, rest candidates, large-bus stops, planned stops and
operation notes in suitable models. Licensed live facility information remains a separate prerequisite.

## 36. Commits

* `c1fb506` — feat: add traffic restriction sources overlays and explicit detour protection
* `98d236b` — fix: preserve distance ordering within traffic impact levels
* `b03098c` — test: finalize Phase010 traffic review and rotation coverage
* `b0f8c44` — fix: reserve landscape operations space beside traffic alerts
* Final review documentation commit — recorded by archive meta and final response.

Final HEAD is authoritative in each archive's `meta/review-info.txt` and final report; BASE_SHA above
is used throughout, never substituted with the prior phase's base.

## 37. Archives and completion

Run `run-review-checks.ps1`, `make-review-archive.ps1` and `make-full-review-archive.ps1` with
`BaseRef=9922b1097dc5d159ead7d8fba2ccbf1637dc8807`. Final HEAD checks and archive self-inspection must
pass. Existing exclusion policy also removes the pre-existing VS Code and Gradle JVM environment files.
Final counts/prohibited-entry checks are recorded in each archive's meta/checks and the final response.

Status: **PASS / COMPLETE**, conditional on the final-head formal checks and both archive self-checks
recorded in the delivered archives. No live VICS/JARTIC PASS is asserted.
