# Review 015f - Navigation overlays in landscape

## Scope and BASE_SHA

Phase010.6F follows completed Phase010.6E. Actual preflight HEAD / BASE_SHA:
1f30a2be252d7a537e2b5ce885ba17bed4e08203.
Preflight status/log/diff and ignore-space-at-eol checks found no tracked changes.
Existing .vscode/, gradle/gradle-daemon-jvm.properties and earlier reference images
are preserved. No reset, cleanup, server/network change, tile regeneration or Search work.

## Visual references and previous structure

Viewed images/reference_landscape_navigation_before.png and
images/reference_portrait_navigation_before.png as visual references, not runtime assets.
Landscape previously had a 24% guidance/warning/operations column, 76% map, then
88dp auxiliary column. Its marker appeared near the physical map center.
Portrait had map overlays reserving 84dp at right and an operations panel of 64dp
below the map. Controls were alongside the cards rather than underneath.

## Operations removal and preserved entrances

Both operations panels and the landscape left column are removed, along with the
unused PlaceholderPanel and its screen-only library callback. Route menu retains
FREE start, prescribed library and Route Editor. Developer Connections remains in
Route Editor. Integration flows now open the library through the retained route menu,
including navigation session-switch confirmation/cancellation.

## Unified overlay and full-width cards

Portrait: Column(MapArea weight=1, 72dp auxiliary row).
Landscape: Row(MapArea weight=1, 88dp auxiliary column).
Warnings and compact ordinary/highway guidance share NavigationTopOverlay in MapArea.
The overlay and cards fill map width minus 8dp at each side, with no control reservation.
Warning and guidance stack vertically; overlay height caps at 30% with overflow scrolling.
No transparent clickable map-sized overlay is added. FREE actions are map overlays in
both orientations; ARRIVED hides recalculation, PRESCRIBED hides the FREE group.

## Controls below overlay and compact auxiliary regression

The measured top overlay height flows through LocalNavigationMapTopOverlayPx to
MapScreen. Compass/zoom/ruler padding follows actual card measurements and resize,
not a hardcoded card height. Existing short-height compact arrangement remains:
compass beside the vertical +/-/ruler group. On very short maps that group scrolls
within its own narrow region; it cannot cover the top cards or bottom actions.
The 68dp ruler, zoom-only camera changes, navigation/general compass separation,
88dp auxiliary parent, 80dp button width, single-line labels and >=48dp targets remain.
This compact short-height exception does not put any control beside the TOP cards.

## Landscape HEADING_UP root-cause audit

The old formula already placed an ordinary viewport's target 33.9dp above its usable
bottom. The screenshot's physical-middle appearance is consistent with subtracting
the 136dp tall bottom corner groups from a short map: an approximately 343dp map
then has target y approximately 173dp. Thus removing the center clamp alone would
not move the marker in the normal landscape case.

Wide map corner groups (map width >=560dp) now use horizontal rows, reducing the
normal occlusion to 80dp. This moves the target approximately 56dp down without
weakening the required max(left,right) measurement or attribution clearance.
Narrow maps retain vertical groups. Old left-column height is never counted.

The old visibleHeight/2 floor is a separate tiny-viewport defect: with 62px usable,
old target y=31; desired bottom-safe target y=28.1. It is now removed. The exact live
freshness/follow state in the supplied screenshot cannot be reconstructed from pixels;
no claim is made that the tiny fallback caused that specific screenshot.

## Old/new anchor formula and safety

margin = max(markerOuterRadius + 8dp, 24dp) = 33.9dp for the existing marker.
Old: y=max(visibleHeight-margin, visibleHeight/2); top=2*y-visibleHeight; bottom=occlusion.
New: desiredY=max(visibleBottom-margin, 0); offset=2*desiredY-visibleHeight;
top=max(offset,0); bottom=occlusion-min(offset,0).
Negative offsets use extra non-negative native bottom padding, not centered fallback.
The top safety threshold is overlay bottom + radius + clearance. When there is no
room for a complete marker between overlays, bottom safety has priority; the code
does not pretend a collision-free placement is possible. NORTH_UP stays physically
centered with zero padding. Freshness gating and raw location remain unchanged.

## Actual projection evidence and aspect ratio

NavigationConsolidationTest injects a synthetic current fix, HEADING_UP/follow/active,
waits for map readiness/native dimensions, then uses projection.toScreenLocation.
Expected visible bottom comes from max(measured Compose left/right group heights),
independently of the camera's bottom padding. Evidence logs orientation, state,
MapView height, measured occlusion/top bounds, native padding, projected y, bottom
distance and local 100m north/east ratio. Tolerance is 3 physical pixels.
MapArea == MapScreen == native MapView == child surface/texture and surface buffer
are asserted. Projection isotropy must be 0.9..1.1. Portrait -> landscape -> portrait,
warning insertion/removal, reduced map height, FREE/PRESCRIBED and zoom are covered.
Card widths/controls below cards/operations absence are checked in real Compose bounds.

Development emulator evidence at density 2.625: portrait margin approximately 88.99px
(33.90dp); landscape approximately 87.99px (33.52dp, within native rounding tolerance).
Landscape map height 901px, measured bottom occlusion 210px, target y approximately
603.01px, usable bottom 691px; isotropy approximately 1.000006. These are development
receipts; final per-device results and exact values are recorded below when available.

## Static validation

Unit: 427 tests, zero failures/errors/skips; one successful run on the final revision.
Lint: zero errors, 20 warnings, 2 hints. Debug, Release and AndroidTest APKs build.
Development compile receipts are retained. Final Debug, AndroidTest, lint and Release
use phase0106f-compact-guidance-final after the compact-card correction. Final unit
receipt: phase0106f-unit-final. The earlier 427-test pass belonged to the pre-correction
revision; after the actual highway-card failure/fix, the final revision was checked
once. There are no repeated successful checks of an unchanged final revision.
No unchanged successful build/check is repeated merely for packaging.
Layout behavior is tested against actual Compose semantics, rather than a duplicate
constant-only unit policy. Unit tests cover short/no-center, density, top clearance,
NORTH_UP/non-follow, ruler and zoom. Existing mode/auxiliary tests remain.

## Device attempts and manual smoke

Targeted emulator attempt 1: 1 pass / 1 fail (ruler collapsed on reduced landscape).
After scoped scrolling correction, failed-method attempt 2: 1 pass.
Full attempt 1 on each device was explicitly stopped when source audit found that
PowerShell's pipe encoding had turned three new test menu labels into question marks.
This was a test-generation defect; app strings were not corrupted. Logs end with
Process crashed because am force-stop was used to avoid running known-invalid tests.
The Unicode input and an obsolete operations-label assertion were corrected before
full attempt 2. This interruption is counted, not concealed as a successful suite.

Full attempt 2: Physical completed 78 cases, 77 pass / 1 fail. The landscape highway
case expected E20 immediately visible despite the permitted 30%-height scrolling cap.
The test now scrolls to verify the actual sign. Visual review also prompted placing
symbol/distance/primary instruction in one full-width row, shortening ordinary and
highway cards rather than spending a separate row on the symbol. Existing sign and
accessibility fields remain. Each orientation iteration now explicitly enters FREE
before checking PRESCRIBED, ensuring landscape FREE corner groups are measured too.
Six affected physical cases (highway and consolidation classes) then passed.
Emulator attempt 2 was superseded at case 32 after the physical failure and final fix;
it is recorded as interrupted, not as an all-green full run.
Final full attempt 3: Physical 78/78 PASS, 543.36 seconds, zero failures/skips.
Emulator attempt 3 reached case 48, then Android's ReferenceQueueDaemon timed out
while targeting HardwareRenderer$DestroyContextRunnable. The runner reported
Process crashed. This is retained verbatim in the receipt, not counted as a PASS.
An ordinary adb reboot then could not bring up SurfaceFlinger. The emulator process
was allowed to exit and the same Pixel_8 AVD was cold-started without wiping data,
with -no-snapshot-load -no-snapshot-save -no-window -gpu swiftshader. This is a
per-launch software-renderer override, not a production or persisted AVD config change.
Full attempt 4 uses the same final APKs: 78/78 PASS, 493.691 seconds, zero failures/skips. The original host-renderer
path is not claimed as an uninterrupted green full run.
No physical full rerun after success.
Stationary physical manual smoke: NOT PERFORMED. The user answered that they cannot
check now on 2026-09-26. The final app was launched after the physical full PASS.
Automated screenshots/projection checks are not presented as manual acceptance.
No driving test is claimed.

## Archive policy, limitations and Phase010.7 handoff

Archive connected rerun: NO.
Create both review and full archives with -BaseRef
1f30a2be252d7a537e2b5ce885ba17bed4e08203 -SkipChecks, using preserved receipts.
Final archives: busnav-review-latest.zip / busnav-full-review-latest.zip and timestamped copies.
Implementation: 431182c71414242a12251f3e979417bec31dc8de.
Compact-card/test correction: e0addead05ff28c61c1dd096a9534d5c8c8b0c1b.
The final documentation commit ID is recorded in the archive HEAD metadata.

Limitations: physical display bottom differs from usable bottom above controls; very
short map panes cannot contain the entire marker plus all overlays without scrolling.
Compact controls can scroll; no changed network/server behavior or extra server smoke.
Existing full-suite remote cases use the production-like remote service. No local server.
Synthetic fix/projection tests do not establish field-driving comfort or the exact
runtime state of the supplied before screenshot. Reference images are not app assets.
Phase010.7 Search remains untouched.

Phase010.6F: INCOMPLETE. Stationary physical manual smoke remains outstanding by
explicit user response; automated completion alone does not mark the phase COMPLETE.

## Changed files

- Main: NavigationScreen.kt, HighwayGuidanceCard.kt, MapScreen.kt, MapController.kt,
  MapControls.kt, NavigationMapFrame.kt.
- Unit: NavigationControlsRefinementTest.kt.
- Instrumentation: NavigationScreenTest.kt, NavigationConsolidationTest.kt,
  HighwayGuidanceCardTest.kt, FreeNavigationFlowTest.kt, PrescribedRouteLibraryFlowTest.kt.
- Docs: ui/navigation-layout.md, navigation-map-orientation.md, map-visible-viewport.md,
  navigation-runtime-location-camera.md and this Review015f.
- Two specified portrait/landscape before reference images (root images/, not assets).

## Final projected position receipts

Physical final full suite (density 2.625):

| Orientation | Map height px | Measured bottom occlusion px | Projected y px | Visible bottom px | Bottom distance px |
| --- | ---: | ---: | ---: | ---: | ---: |
| Portrait | 2182 | 357 | 1737.0126 | 1825 | 87.9874 |
| Landscape | 928 | 210 | 629.6375 | 718 | 88.3625 |

Expected margin is 88.9875px (33.9dp), tolerance 3px. Reduced-height and returned
portrait measurements also pass. Local isotropy ranges about 1.0000054..1.0000058.
Native and Compose/surface dimensions match. Full raw measurements are preserved
in phase0106f-physical-projection-final.txt (last nine measurements of the final run).

Existing review archives include text receipts. PNG captures are preserved separately
in busnav-phase0106f-evidence.zip; they use a deterministic fallback basemap for
projection tests, not an assertion that the live Japan map failed.

Emulator final full suite (density 2.625, cold boot / SwiftShader):

| Orientation | Map height px | Measured bottom occlusion px | Projected y px | Visible bottom px | Bottom distance px |
| --- | ---: | ---: | ---: | ---: | ---: |
| Portrait | 1953 | 357 | 1507.0125 | 1596 | 88.9876 |
| Landscape | 901 | 210 | 603.0125 | 691 | 87.9875 |

All nine measurements (normal/reduced/PRESCRIBED through portrait-landscape-portrait)
pass the 3px tolerance, native bounds/surface and isotropy checks. Final emulator
isotropy is approximately 1.0000054..1.0000058. See
phase0106f-emulator-projection-final.txt and the per-device final case ledgers.

| Device | Full attempts | Final full result | Successful full reruns |
| --- | ---: | --- | ---: |
| Emulator | 4 (two superseded, one graphics-finalizer crash, one green) | 78/78 PASS | 0 |
| Physical | 3 (one interrupted, one 77/78, one green) | 78/78 PASS | 0 |

Targeted attempts were Emulator 2 and Physical 1, as detailed above. Final tested app
tree: 1e2af89fb339a227101aaa846e4119099fc6ccf5. Unit final: 427/427, no skips;
lint: 0 errors / 20 warnings / 2 hints; all three APK builds PASS.
All automated requirements are green. The phase remains INCOMPLETE solely because
the requested stationary physical manual round has not been performed.
