# Phase 010.6C navigation runtime

## Starting away from a road

The current start gate is `NavigationStateHolder.startLocationProblem()`. It checks precise location permission, provider error, and a raw fix accepted by `RecentStartFixes` (`elapsedRealtime` age at most 15 seconds and reported accuracy at most 150 m). It does **not** require `RouteMatcher` to report `MATCHED`, or a point to be on a road. The text `現在地を更新中です` means the fix is missing or stale under that policy; proximity to a road alone cannot produce it. `GuidanceUiState`'s `位置が不確実です` is a later guidance reliability state. The older `FreeNavigationConfig` 50 m/10 s helper is retained for compatibility but is not the production FREE gate.

For FREE, the selected raw fix supplies `RoutingRequest` START. Valhalla `POST /route` snaps that input to a routable edge. The first point decoded from the first leg's `shape` is the routed departure point; `ScheduledRoute.start.position` remains the requested raw point. `freeNavigationStartPosition` compares those two positions with the shared great circle distance function. It records the raw and calculation positions separately. The current vehicle marker always uses the latest raw `LocationState.point`, including before and after preview/start.

The existing Valhalla 3.9 response fixture preserves the requested raw START while its first decoded shape point lies 100–300 m away; the parser test checks that this real response is accepted as a snapped departure. The preferred snap distance is 100 m and the hard limit is 300 m. Through 100 m the route proceeds without a notice. Over 100 m through 300 m the preview says `最寄りの道路から案内を開始します`. Over 300 m the result is rejected with `走行可能な道路を確認できません`. This is a straight line distance safety bound, not proof that the road is reachable from a parking lot. A Valhalla no-route response remains a routing failure. FREE still makes one `/route` request per calculation; no `/locate` request was added. Recalculation remains explicit.

PRESCRIBED session start uses the same location quality gate and does not require a route match. It does not snap to a prescribed segment or promote an off-route fix to `MATCHED`. Route matching, deviation and Phase 009 detour/rejoin run after start under their existing reliability thresholds; no automatic reroute was added.

## Following camera and marker

`NavigationMapFrame` takes one accepted fix and one camera state. Its target is the same raw point used by the marker; its bearing comes from `NavigationHeadingResolver` through `NavigationCameraState`. While following in HEADING_UP, the target is at 72% of the **visible map pane** height. The visible pane excludes separate guidance/operation panels, and subtracts the measured height of buttons overlaid on the map. MapLibre camera padding places the target without guessed screen pixels. NORTH_UP remains centered at bearing zero. Portrait and landscape use the same fraction of their own map pane.

An active navigation fix cancels older MapLibre transitions, calls `moveCamera`, then sets marker source and rotation in the same UI update. There is no independent position smoothing for either. Stale controller frames are ignored, and `NavigationStateHolder` already rejects duplicate/older raw fixes. Manual gestures still suspend follow. The existing `NavigationHeadingResolver` remains the navigation heading source: moving GPS course, reliable matched route fallback, low speed hold and circular turn handling. HEADING_UP arrow rotation is zero; NORTH_UP and manual camera rotation subtract camera bearing once.

## Stationary compass and screen

With a visible inactive map and resumed lifecycle, `DeviceHeadingSensor` registers `TYPE_ROTATION_VECTOR`. It remaps axes for display rotations 0/90/180/270, obtains magnetic azimuth, applies `GeomagneticField` declination when a position is available, and uses a 0.35 circular low pass. The non-navigation marker subtracts current map bearing. Pausing, leaving the map, and entering navigation unregister the listener. Navigation heading never uses device compass as its primary source. No deprecated `TYPE_ORIENTATION` sensor is used.

`NavigationRoute` sets the window's `FLAG_KEEP_SCREEN_ON` only while `navigationActive` (FREE, PRESCRIBED or active detour) and removes it when that state ends or the composable leaves. It does not hold a WakeLock or change brightness.

## Boundaries

The snap limit does not validate road access, lane, elevation, or private entrance connectivity. A fix whose accuracy or monotonic timestamp is unusable still blocks start even near a road. Manual on-road behavior and short screen timeout need device observation; synthetic location and orientation checks only establish software behavior. Phase 010.6D owns the non-navigation north-reset compass and scale controls.
