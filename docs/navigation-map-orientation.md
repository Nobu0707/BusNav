# Navigation map orientation — Phase010.5B

## Modes and activation

Navigation defaults to **HEADING_UP**. The map-edge compass toggles **NORTH_UP** without restarting the Activity. Only `BusNavScreen.NAVIGATION` with `isNavigationStarted` enables this camera and control. FREE, PRESCRIBED and active DETOUR share it. Preview, library, route editing and detour planning do not receive a navigation camera state.

The user preference lives in a separate production DataStore (`navigation_map_preferences`, key `navigation_map_orientation`, values `heading_up` / `north_up`). Missing/unknown values use HEADING_UP. Debug and Release use the same implementation. Reading completes before navigation rotation is enabled. Toggling is an atomic DataStore edit, including rapid repeated taps; developer-connection reset does not touch this preference.

## Heading policy

`NavigationHeadingResolver` is pure and runs for accepted location fixes in NavigationStateHolder, not Compose frames. GPS normalized bearing is primary when finite and speed >= 2.5 m/s. At low speed or without valid bearing, hold the last reliable heading. If none exists, a MATCHED route segment with reliable navigation projection may initialize it. AMBIGUOUS, UNRELIABLE and uncertain projections cannot supply fallback.

Without any usable heading, keep the camera's current bearing. Stale (>=10 seconds), future, timestamp-less and out-of-order monotonic fixes cannot change navigation heading/camera. There is no reset to arbitrary north for a missing bearing. Raw GPS remains the marker's coordinate; route matching does not move the marker.

Circular deltas use the shortest arc: 359→1 is +2°, 1→359 is -2°. Changes below 2° are held. Changes above 100° need a second fix within 25° and 2.5 seconds. Duplicate fixes cannot confirm themselves. Ordinary turns (0,20,45,80,90,120,180) are accepted immediately; 10,220,12 rejects the isolated spike. A real abrupt turn greater than 100° therefore has one-fix confirmation latency.

## Camera, gestures and lifecycle

MapController owns animation. HEADING_UP follows the resolved bearing; NORTH_UP follows location at 0°. The first navigation follow establishes the existing 16.5 zoom once; a recreated active navigation camera keeps its saved zoom. Toggle/recenter preserve zoom, and tilt stays at 0°. HEADING_UP following places the raw marker 33.9dp above the visible bottom, excluding the maximum measured left/right bottom overlay height. The margin includes the ring outer stroke plus 8dp clearance. Tiny viewports prioritize safety. NORTH_UP stays centered. Both orientations use the same raw location for camera target and marker. See [Visible map viewport](map-visible-viewport.md).

Move/rotate/zoom gestures suspend follow immediately. The orientation preference remains unchanged. Current location restores follow and the selected orientation. The camera-start gesture reason also covers pinch, rotation and double-tap zoom. MapLibre's built-in compass is disabled to avoid a competing reset action.

Each active navigation fix cancels previous camera transitions and moves camera and marker in one UI update. No old easing transition can chase a newer fix. Manual camera interaction still stops following. Style changes reinstall overlays without resetting camera or selected mode. The navigation ViewModel retains its own camera across Activity recreation, separate from editor camera state. Traffic/settings dialogs hide the compass and suspend camera following while open; returning preserves preference and follow intent.

## Marker and compass

The original Canvas marker is 56dp square with a 22.4dp radius ring, dark outline, white halo and crimson stroke/arrow. Density-aware rasterization and iconSize=1 keep it about 56dp across devices. The bitmap center and symbol anchor are (28,28); the first arrow vertex (tip) is exactly (28,28). Its symmetric tail extends downward. Rotation pivot and raw location coordinate use this same center.

Viewport alignment avoids double rotation. While following HEADING_UP, iconRotate=0 throughout camera animation. NORTH_UP displays resolved heading; manual exploration displays heading minus actual camera bearing. At rest, unreliable raw bearings do not rotate the navigation marker.

The independently designed 64×80dp map-edge control has a rotating N/needle and a mode caption. North is `normalize(-cameraBearing)`. Instrumentation verifies this against actual MapLibre projections of a geographic north point at 0/90/180/270°, and separately checks rendered arrow pixels in both modes. Japanese content descriptions name the current mode and the tap action. The control is inside the map pane, below the measured full-width guidance/warning overlay.

`images/reference_navigation_heading_up.jpg` was viewed for **marker reference only**. Camera behavior, compass, layout and UI are independent BusNav designs. No reference pixels, logos or screenshot crops are runtime assets.

## Map details and regression

The 2,200m show / >2,600m hide shield hysteresis is unchanged. The actual visible rectangle is projected through MapLibre's bearing, so great-circle distance remains meaningful when rotated. Facility, intersection and shield labels use viewport alignment; route lines remain beneath shields and traffic overlays above them. JAPAN is the official basemap, with Light/Dark styles.

## Validation and limitations

See [Review014b](reviews/014b-navigation-camera-marker.md) for commands and results. Unit coverage includes persistence across store reopening, invalid values, heading reliability, circular math, spikes, ordinary turns, camera policy and marker geometry. Native tests include camera cardinals, rendered pixels, north projection, stale/null fixes, drag/recenter, zoom preservation, reloads, navigation-only control, accessibility bounds and portrait/landscape.

On an inactive map, TYPE_ROTATION_VECTOR supplies the marker heading with display-rotation remapping, declination correction where location is available, and circular smoothing. Navigation still uses GPS course or reliable route fallback. No 3D tilt, automatic rerouting, or new map data is added. Field-driving comfort and very high-frequency GPS behavior remain limits of synthetic stationary testing.

## Phase010.6F overlay and lower-anchor update

Both orientations use the same full-width MapArea overlay; operations panels and the
landscape guidance side column are removed. Map controls follow the measured overlay
height. Wide map panes use horizontal corner actions, reducing the measured bottom
occlusion from the normal 136dp to 80dp while preserving attribution clearance.

The lower anchor is visibleBottom - 33.9dp; the visibleHeight/2 fallback is removed.
Negative anchor offsets use additional native bottom padding. NORTH_UP centering, raw
GPS, freshness gating, heading resolution, follow/gesture semantics and native resize
remain unchanged. A short pane alone no longer selects the centered fallback.

The reference landscape's near-middle physical position is consistent with subtracting
a tall bottom action group, even when the projected point is already bottom-aligned
within the usable rectangle. An extremely short viewport was a separate formula edge
case. Synthetic runtime evidence must not be claimed as a diagnosis of the exact
stale-location/follow state in the user's screenshot.

[Review015f](reviews/015f-navigation-overlay-landscape.md) records actual projected
positions against measured Compose corner groups, rotation, full-width overlays,
control placement and native bounds/isotropy. For impossible viewport sizes, bottom
safety takes precedence; small control regions scroll and top cards cap at 30%.
