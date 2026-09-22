# Review014b — Navigation camera modes, compass and vehicle marker

## Scope and baseline

- Phase010.5B. BASE_SHA: `0f011a378ff0aa329ac48135e46d88ae77b944fe` (actual HEAD read before edits).
- Phase010.5A.1 is accepted PASS / COMPLETE, including urban expressways, facilities, intersections, shield hysteresis and overlay ordering.
- Initial tracked worktree was clean. Existing untracked `.vscode/` and `gradle/gradle-daemon-jvm.properties` were left untouched and excluded from staging.
- No Phase010.5C cursor/viewport work, map data generation, PBF/MBTiles rebuild, reference screenshot assets or road-style changes.

## Implementation audit and ownership

Before this phase MapController followed position without navigation bearing, used a 72×72 arrow bitmap with iconSize=2 / MAP alignment, and reset zoom during recenter. Move gestures alone suspended follow. NavigationStateHolder already owned isNavigationStarted, following and recenter requests. RuntimeTheme reloads the style; OverlayLayerOrder restores route/traffic order; VisibleMapSpan projects top/bottom center.

The new pure orientation model, fix-level NavigationHeadingResolver, production preference repository, camera controller and Compose control have separate responsibilities. `NavigationUiState.navigationHeading` retains heading through style and screen changes. NavigationViewModel stores a separate navigation camera while the editor keeps its established camera ownership.

## Orientation and persistence

HEADING_UP is the default. NORTH_UP is the alternative. Only explicit navigation start on NAVIGATION enables the camera/control. The separate DataStore persists heading_up/north_up in both build variants; invalid values fall back to HEADING_UP. Atomic toggle edits avoid lost rapid taps. Loading a stored preference cannot replay an old preview-fit request. No Activity restart is required.

## Heading reliability, fallback and smoothing

Finite normalized GPS bearing at >=2.5m/s is primary. Low speed/missing bearing holds the last heading. If none exists, a MATCHED segment bearing is eligible only with reliable projection. Ambiguous/unreliable route matching cannot rotate the camera.

Monotonic fix age must be <10s; future, missing, old and duplicate timestamps cannot provide new heading evidence. Null/stale location stops camera updates. Circular math handles 359↔1 as ±2°. The 2° deadband suppresses small noise; >100° jumps need a consistent second fix within 25°/2.5s. The required ordinary turn sequence is accepted without low-pass delay; 10→220→12 holds 10 then accepts 12.

MapLibre camera transitions are replaced, not stacked. Animation is <=350ms and adapts to fix interval down to 80ms. There are no stale completion callbacks. Extreme real turns have one-fix confirmation latency.

## Camera, manual control and recenter

HEADING_UP follows resolved course, NORTH_UP follows position with bearing 0. First navigation follow establishes zoom 16.5 once; recreated navigation cameras, toggles and recenter preserve zoom. All follow updates remain top-down. Manual drag/rotate/zoom suspends follow without changing preference. Current location restores selected orientation. Preview overview requests cannot override an active following camera.

Traffic/settings panels hide the compass and suspend follow while open; closing restores the same preference and prior follow intent. FREE/PRESCRIBED/active DETOUR use the same policy. Detour planning and route editing receive no navigation camera state. Rejoin retains preference and existing follow intent.

## Marker design and geometry evidence

The original Canvas artwork uses a 56dp square, ring radius 22.4dp, dark outer edge, white halo and crimson ring/arrow. Density-aware bitmap sizing plus iconSize=1 avoids growing the earlier 2× arrow again. Symbol anchor, bitmap center and rotation pivot are all (28,28). The first arrow vertex is exactly (28,28); symmetric tail vertices extend below it. Geometry unit tests check equality, symmetry, bounds and positive size.

The vehicle source is the raw GPS point. With VIEWPORT alignment, HEADING_UP+follow sets screen rotation 0, including animation. NORTH_UP and manual exploration use heading minus actual camera bearing. The cardinal instrumentation checks native layer properties and rendered arrow pixels independently, preventing a double-rotation implementation from passing.

`images/reference_navigation_heading_up.jpg` was viewed as **marker reference only**. No reference pixels were copied. Camera behavior, compass position/shape, UI and layout were designed independently of the reference image.

## Compass, placement and accessibility

North rotation is normalized -cameraBearing. Its sign is demonstrated by projecting an actual geographic north offset through MapLibre at 0/90/180/270°, then comparing screen direction. This is not a formula-only assertion.

The control is 64×80dp, with N/needle and a mode caption, inside the map's top-right edge. The existing built-in compass is disabled. Japanese descriptions identify current mode and next action. Instrumentation checks >=48dp bounds, portrait/landscape display, and no overlap with current-location/overview controls. Guidance, highway, traffic and bottom navigation remain outside this map-edge area.

## Theme, region and map regressions

Light→Dark→Light and Kanto↔Chubu reloads retain camera bearing/zoom, marker and compass in both modes. Existing shields, urban facilities and intersection labels already use viewport alignment. The road-presentation suite now starts with 90° bearing and continues to verify C1/C2, national shields, IC/JCT/access/toll labels, named intersections, synthetic route-under-shield ordering and traffic icons. No style/tile inputs change.

Visible span still uses geographic distance between projected top/bottom center, including rotation. Show <=2.2km / hide >2.6km and hysteresis remain unchanged. Existing portrait/landscape span checks run with rotated cameras.

## Verification record

- Unit: 385 tests after adding 16 heading/geometry/preference tests; zero failures in the implementation run.
- Focused native camera tests: 3 PASS on SOG06 / Android 14, including all cardinals, real marker pixels, turn/spike traces, stale/null fixes, drag/recenter and both-mode theme/region reload.
- Application flow smoke: PASS on physical device with synthetic locations, portrait/landscape, saved NORTH_UP, Activity recreation and editor exclusion. The extended version additionally covers traffic panel return.
- Emulator: Pixel 8 / Android 16. The initial full run was stopped after MapLibre Vulkan renderer detach stalled. A thread dump identified nativeReset waiting on a paused renderer. Review012 already documents the required AVD flags; emulator restarted with `-gpu swiftshader -feature Vulkan -no-snapshot`. No application renderer/dependency workaround was introduced. A subsequent x86 Vulkan framebuffer snapshot crashed in readFramebuffer; screenshot helpers now capture Android display pixels instead. The rendered-arrow pixel assertion remains in place and does not depend on the native snapshot API. A long monolithic emulator run subsequently lost the AVD/ADB connection after 43 tests; final emulator validation is split into four independent AndroidJUnitRunner shards to release native renderer resources between runs.
- Full physical suite at implementation commit d730aed: **75 PASS, 0 failures, 0 skipped**. Unit 385 PASS; lint 0 errors / 20 warnings / 2 hints; Debug, Release and test APK builds PASS. A subsequent visual check found an initial navigation zoom race: the first navigation follow now establishes 16.5 atomically, while restored navigation/toggle/recenter preserve zoom. The application-flow test asserts 16.5 before and after both portrait/landscape recreation; final formal checks cover this fix.
- Final unit/lint/Debug/Release/test APK and full connected results are recorded by `scripts/run-review-checks.ps1` in archive `checks/`. Final status is reported only after those checks finish.
- Local synthetic screenshots and raw device reports are under build/phase0105b and excluded from archives. Device serials are not included in this review.

## Limitations and Phase010.5C handoff

The camera target remains centered in the map pane. A lower target position and generalized visible-map geometry/cursor belong to Phase010.5C and are not implemented. No device orientation sensor or 3D camera is added. Stationary startup waits for reliable route matching or movement; without either, current camera bearing is held. Driving comfort has been checked with synthetic stationary traces, not a road drive. One-fix confirmation applies to turns exceeding 100°.

## Commits and archives

Implementation and review changes are committed explicitly; unrelated pre-existing untracked files are excluded. Final HEAD, commit list and diff against BASE_SHA are recorded in archive metadata. Both review and full-review ZIPs are generated with BaseRef=`0f011a378ff0aa329ac48135e46d88ae77b944fe` and self-checked. Final file paths and completion status appear in the delivery report.
