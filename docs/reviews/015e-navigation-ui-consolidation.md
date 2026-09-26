# Review 015e — Navigation UI consolidation

## Scope and baseline

Phase010.6E follows completed Phase010.6D / 010.6D.1. BASE_SHA:
`37ac87c328c980ed7ced8ba2cfca27010e25ca03`.
Preflight: no tracked changes. Existing untracked `.vscode/`,
`gradle/gradle-daemon-jvm.properties` and two supplied reference images are preserved.
No pull/rebase/merge, destructive cleanup, server/data generation, Search or unrelated refactor.

## Visual references and layout audit

Viewed `images/reference_google_maps_navigation_layout.png` and
`images/reference_busnav_navigation_layout_before.png`. Only the forward-view/lower-marker
and map-overlay concepts inform this change. No reference image is a runtime asset;
no Google-specific artwork, color, typeface, logo or exact layout was copied.

Before: portrait stacked deviation, detour, FREE actions, traffic and large guidance above
the map, followed by 70dp operations and 72dp auxiliary controls. Landscape allocated
0.24/0.58/0.18 weights to guidance/map/auxiliary. General compass measured 64×56dp,
navigation compass 64×80dp; ruler bars nominally 80–140dp plus 12dp card padding.
The old ruler fallback could exceed that range, as could its upper hysteresis band.

## Ruler and zoom

Card width is now 68dp, inner bar at most 56dp, preferred minimum 28dp.
Nice distances remain 10/20/50/100/200/500m and 1/2/5/10km. Actual bar width is
distance divided by sampled projected meters/pixel. The hard maximum also applies to
hysteresis; fallback chooses a smaller nice distance instead of stretching the card.
The lower width preference is soft because nice distances are discrete. If even 10m
does not fit, or the bar would be subpixel, hide the ruler instead of lying about distance.
Single-line labelSmall preserves the `10 km` label inside the fixed card.

The preset UI/state and unreferenced ScaleMode/ScalePreset cycle were removed.
Vertical + / − controls are 64×48dp. ZOOM_STEP=1.0, clamped to native min/max zoom.
CameraPosition.Builder preserves target, bearing, tilt and padding; programmatic updates
do not call onGesture, change follow or orientation. Immediate moves accumulate repeated
taps. At zoom limits tapping is a clamped no-op; buttons are not disabled.
Pinch retains the existing manual gesture behavior. At available height below 260dp,
the compass sits beside the vertical zoom/ruler column to avoid bottom-right controls.

## HEADING_UP bottom-safe anchor

Old anchor: 0.85 of visible height. New center:
`visibleBottom - max(vehicleOuterRadiusPx + 8dp, 24dp)`.
22.4dp ring radius + 3.5dp outer half-stroke + 8dp clearance = **33.9dp**.
Top padding is `2 * max(visibleHeight - margin, visibleHeight/2) - visibleHeight`;
bottom padding is the measured occlusion. Density is passed from the actual MapView.
For 672dp/272dp usable heights the effective fractions are 0.9496/0.8754.
Extremely short viewports prioritize safety and cannot promise a fraction above 0.85.
NORTH_UP remains at its existing physical center. GPS coordinates, heading resolution
and the synchronized marker/camera frame are unchanged.

## Portrait top overlay and operations

MapArea is the first weighted content. NavigationTopOverlay contains safety/location/
deviation warnings, arrival status, traffic warnings, compact guidance and transient
detour actions. Semi-opaque cards leave the map visible. Overlay width leaves 84dp
at right for map controls; height is capped at 30% of the map, with scrolling for overflow.
Routine traffic provider status remains available in the traffic panel rather than adding
another top row. Highway compact guidance retains schematic, signs and full accessibility
description; uncertain highway guidance does not fall back to stale ordinary guidance.

Operations is reduced from 70 to 64dp to retain route/location information and its
prescribed-library entry. If no library callback exists, an active screen omits this
duplicate panel. The actual application retains the library entry. Portrait auxiliary
height and its five actions remain unchanged.

## FREE actions and bottom occlusion

FREE end is outlined above the primary filled recalculate pill at map bottom-left.
Both have at least 48dp touch targets. ARRIVED hides recalculation but retains end;
arrival status moves to the top (landscape: guidance column). PRESCRIBED has no FREE
actions. Existing route overview/current location remain bottom-right.

LocalNavigationMapBottomOcclusionPx receives the maximum measured left/right group
height, including 32dp attribution spacing. When FREE controls disappear their stale
height is ignored. Left/right widths are constrained separately. The camera uses the
map usable bottom rather than the physical display bottom.

## Aspect-ratio root-cause audit and sizing invariant

MapArea -> MapScreen -> AndroidView uses fillMaxSize, with no fixed aspectRatio or
stretched screenshot. MapView now declares MATCH_PARENT explicitly. The locally installed
MapLibre 13.6.1 bytecode confirms onSizeChanged invokes NativeMap.resizeView(width,height).
The existing SDK resize path is retained; no speculative manual renderer resize is added.

A concrete presentation defect was identified: the controller layout listener refreshed
editor fit/detail sampling but not navigation padding. With a repeated fresh stationary
fix, a changed map height could retain old top padding. The listener now requests a frame
update and compares map height independently of GPS timestamps. Freshness and gesture
gating still apply. This fixes stale centering/anchor placement; it is not proof that it
caused the user's reported anisotropic stretch.

Editor fit already cleared temporary padding. Route and multi-point plan overview now
clear it on successful animation completion while preserving the physical center.
Style reload still reinstalls overlays without a camera reset. Bottom-sheet inset handling
and Route Editor projection/cursor source are unchanged.

## Resize/projection and layout tests

NavigationConsolidationTest measures Compose map bounds, actual MapView width/height,
the child SurfaceView/TextureView, and SurfaceView buffer bounds when present. It checks
warning/guidance expansion, FREE/PRESCRIBED actions, a reduced map container and real
portrait -> landscape -> portrait transitions. Nearby 100m north/east projections must
have a length ratio within 0.9–1.1 at zero tilt. It also verifies projected marker margin,
zoom-only camera changes, no gesture callbacks, ruler width and control collisions.
Screenshots are captured for visual review. Existing editor/style/compass/device flows
remain in the full suite.

## Landscape auxiliary sizing

The 0.18-weight auxiliary column is replaced by a compact **88dp** parent within the
72–110dp constraint. Buttons occupy its 80dp inner width, with 12dp horizontal padding,
single-line labels and >=48dp height. Text fills this compact parent rather than an
unbounded fraction of the screen. A 900dp screen previously allocated about 156dp to
auxiliary controls. Remaining width is allocated 0.24/0.76 to guidance/map, returning
width to the map without enlarging the left guidance fraction. FREE controls use the
same map corners. Light/Dark and font scale 1.0/1.3 passed on both devices.

## Compass and other regressions

GeneralNorthCompass north-pointer rotation and NavigationCompass exclusivity/toggle
are unchanged. Regression scope includes VisibleMapViewport, Route Editor cursor,
traffic, JAPAN-only basemap, FREE/PRESCRIBED/Detour and KEEP_SCREEN_ON. Live tests in
the existing full suite use the production-like remote service; no additional remote
smoke, local server, server edits or data rebuilds are introduced.

## Validation ledger

Initial static revision: Unit 424 tests, zero failures/errors/skips; lint 0 errors,
20 warnings, 2 hints; Debug, Release and AndroidTest APK builds passed.
Code review subsequently found missing compact highway sign/accessibility information;
the correction is tracked separately from these initial receipts.

Final lint: PASS, 0 errors / 20 warnings / 2 hints. Debug, Release and AndroidTest APKs:
PASS. Unit-covered production policy code remained unchanged after the single 424-test
run, so Unit was not rerun. Initial static checks passed; subsequent necessary UI fixes
rebuilt/rechecked affected artifacts. There were four UI revisions: initial compact
guidance, restored highway signs/accessibility, attempted minimum label width, and final
88dp-parent label measurement. The final Debug+AndroidTest build shared one invocation;
final lint and Release each ran once afterward. No final successful check was repeated.

| Device | Attempts | Results |
| --- | ---: | --- |
| Android 16 Emulator | 3 | Full Gradle connected run: 74 passed / 4 failed. Failed-only retry: 3 passed / 1 failed. Final failed-method retry: 1 passed. All 78 unique methods pass, zero skipped. |
| Android 14 Physical | 2 | Full AndroidJUnitRunner run: 77 passed / 1 failed. Failed-only retry: 1 passed. All 78 unique methods pass, zero skipped. |

These are **aggregated green results**, not uninterrupted all-green full runs. Each
device had exactly one full attempt; passing cases were not rerun. Physical uses the
same AndroidJUnitRunner/APKs directly through adb, with complete per-case instrumentation
status and terminal JUnit summary, isolated from the Emulator Gradle result directory.
No successful device case was rerun for packaging.

Initial failures and corrections:
- Two portrait highway cases: restored full contentDescription and sign/ref information;
  uncertain highway content no longer borrows ordinary guidance fields.
- New native-bounds test: width threshold was tighter than integer-pixel rounding;
  use actual MapView density and 1px rounding allowance. Native bounds, surface buffer,
  100m north/east isotropy, 33.9dp margin, zoom-only state and all orientation/resize
  transitions then passed on both devices.
- Landscape label case: intrinsic wrapped Text had a smaller measured size than its
  paragraph constraints, producing didOverflowWidth. A minimum Text width alone did
  not resolve it. A compact 88dp parent and filled inner text measurement did; the
  font-scale/theme assertions passed without weakening them.

The initial wrapper had an array-scalar handling error before any test started. After
the first failed suite, screenshot pulling reported no folder because the new test
stopped before capturing; full XML/HTML and Gradle output were already preserved.
Later targeted execution saved all six orientation/state screenshots. Neither wrapper
issue triggered a full rerun.

Evidence: checks/phase0106e-*-attempt*.txt, phase0106e-*-final-cases.txt and
phase0106e-unit-summary.txt. Initial static receipts are preserved with the
phase0106e-initial- prefix. Per-device screenshot directories remain under
build/phase0106e-{emulator,physical}-results/screenshots. Early orientation captures can
precede the first rendered GL frame; subsequent resized captures show the marker/ruler.
These captures precede the final auxiliary-only text measurement correction and are not
claimed as final pixel-perfect screenshots. Existing native marker-pixel tests also passed.

Manual stationary smoke: **PASS — user-reported on 2026-09-26**, one round on the final
APK. The user answered “すべて問題なし” for portrait/landscape ruler, +/−, vehicle position,
FREE bottom-left actions, top warnings/guidance, map stretch, compact landscape controls,
compass and screen-on behavior. The app was launched on Physical after testing.
Current max-left/right occlusion and safe-margin behavior are retained based on that
acceptance; no new on-road driving test is claimed.

## Commits, archives and handoff

Implementation: fe742f1 (navigation overlays, map controls, anchor and tests).
Failure follow-up: c12859c (compact label measurement and pixel-rounding assertion).
Tested final app tree: 9c6d24f3286d5a379c7b12e13e36abd7e9bc6d31.
Final documentation commit is recorded in each archive's HEAD metadata.
Deliverables: busnav-review-latest.zip and busnav-full-review-latest.zip, plus timestamped
copies. They are generated after this document commit using existing receipts.
Review and full archive generation must use `-BaseRef 37ac87c328c980ed7ced8ba2cfca27010e25ca03 -SkipChecks`.
**Archive connected rerun: NO.**
Saved test results are preserved per device before another connected run can replace them.

Limitations: the original visual stretch must be distinguished from measured projection
and padding behavior; field driving is outside this stationary validation. Very small
viewports may scroll top content and cannot preserve the same anchor fraction. Extreme
zooms may hide the fixed-nice-distance ruler. Phase010.7 Search remains untouched.

Phase010.6E: **COMPLETE**, with the aggregated-test qualification and viewport limits
above. All 78 unique cases pass on each device and the final stationary manual round is
accepted. The original anisotropic rendering symptom was not reproduced; measured
projection/buffer bounds pass and the demonstrated stale camera-padding defect is fixed.
In very short landscape panes the 33.9dp safety margin can yield a fraction below 0.85;
the user accepted the actual final portrait/landscape placement. The next phase may
revisit collision-aware central-bottom placement if desired; no Phase010.7 implementation
was started.
