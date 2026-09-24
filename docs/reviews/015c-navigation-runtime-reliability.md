# Review015c — Phase 010.6C navigation runtime reliability

## Baseline and scope

`BASE_SHA`: `3bc5175517bc341fd352664d6ace436aefb92679`. Existing untracked `.vscode/` and `gradle/gradle-daemon-jvm.properties` were left alone. No server, tile, or unrelated map presentation files were changed. Phase 010.6D owns the inactive north-reset compass and scale controls.

## Road-off report and actual start gate

The current production gate is `NavigationStateHolder.startLocationProblem()`, shared by FREE calculation/start and PRESCRIBED start. It requires precise permission and no provider error, then a `RecentStartFixes.best()` raw fix whose monotonic timestamp is within 15 seconds and accuracy within 150 m. Its `現在地を更新中です` text is returned for a stale or missing eligible fix; route matching is not consulted. `RouteMatcher` and deviation detection start only after session activation and may suppress strong guidance. The source review therefore did not find a direct “must already be on road / MATCHED” start condition. The field symptom cannot be attributed to road distance alone without the device fix age, accuracy and provider status at that moment. These thresholds were not loosened by guesswork.

## FREE snap and PRESCRIBED session

FREE sends one raw START to Valhalla `POST /route`. The parser decodes the first leg's shape into `route.geometry.first`; `ScheduledRoute.start.position` retains the raw requested START. `NavigationStartPosition` keeps the two coordinates, source and great circle distance separate. Through 100 m the result previews normally; over 100 m through 300 m it warns that guidance begins at the nearest road; over 300 m it blocks with `走行可能な道路を確認できません`. No `/locate` was added. No-route remains a routing failure. Raw marker rendering still reads only the latest location fix. A prescribed session may start away from its route, but no projection is promoted to MATCHED, no forced prescribed snap or automatic reroute occurs. Phase 009 detour remains explicit.

## Camera, compass and screen

The map pane excludes guidance and operation panels. The bottom map controls report their measured height. `NavigationMapFrame` uses that visible rectangle and a 0.72 HEADING_UP anchor fraction; NORTH_UP remains centered. Active follow cancels an older transition, moves the camera immediately and renders the raw marker in the same UI update. Stale controller frames are ignored. `NavigationHeadingResolver` remains the only navigation heading policy, preserving GPS course, reliable route fallback, low speed hold and circular turn handling. VIEWPORT aligned marker rotation is zero for HEADING_UP and heading minus camera bearing otherwise.

An inactive resumed map registers `TYPE_ROTATION_VECTOR`. Display axes are remapped for 0/90/180/270; `GeomagneticField` adds declination when a position is available; circular low pass handles 359→1. The sensor stops on pause, map disposal and navigation activation. A map bearing is subtracted exactly once. The active navigation screen sets the Activity window's `FLAG_KEEP_SCREEN_ON`; preview/editor/library or session end clears it. No WakeLock or brightness control is used.

## Verification

- `test`: 415 JVM tests passed, zero failures/skips. Coverage includes snap distances 0/20/80/150/300/>300 m, raw marker, single FREE routing call, prescribed off-route start, camera anchor/occlusion/landscape/stale frame, compass axes/circular math/declination/camera subtraction, and screen-on state. The existing Valhalla 3.9 response fixture additionally confirms that the request START is retained while the first decoded geometry point differs by 100–300 m and is accepted by the snap policy.
- `lint`, `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: passed. Debug, Release and Android test APKs were produced.
- Pixel 8 emulator `connectedDebugAndroidTest`: 77 passed, zero failures/errors/skips, with local Valhalla and Kanto/Chubu TileServer returning HTTP 200. Android test XML was checked in addition to Gradle's exit code because unavailable services can otherwise appear as assumption failures in XML while the Gradle task succeeds.
- Synthetic smoke: FREE off-road raw START and marker with one routing request; prescribed off-route activation without a false match; MapLibre HEADING_UP lower anchor and NORTH_UP center; rendered fake device headings 0/90/180/270 on a rotated map; FREE preview/start/end `FLAG_KEEP_SCREEN_ON` transitions. These are software observations, not stationary physical field observations.
- A physical Android device was online and the connected suite was attempted. Installation stopped with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an existing BusNav installation uses a different signing key. The installed app was not uninstalled because that could erase its data. Physical connected tests and stationary manual smoke therefore remain unverified. The device serial is omitted.

## Limits and handoff

Phase 010.6C remains INCOMPLETE until physical connected and stationary manual checks can be performed. The 300 m straight line bound cannot establish access from a parking lot, distinguish an elevated road from its frontage road, or calibrate GPS quality. The sensor heading needs real sensor calibration and a location for true-north correction. Abrupt physical turns, screen timeout, and off-road departure need a stationary field check before a driving trial. Do not operate the screen while driving. Phase 010.6D may add inactive north-reset and scale controls without changing this heading ownership.
