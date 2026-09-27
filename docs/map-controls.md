# Map controls (Phase 010.6E)

The map right edge contains the existing mutually exclusive navigation/general compass and one ZoomScaleControl surface holding +, the projected ruler, and −. General north rotation and the 4° neutral zone are unchanged. The general compass is 56×56dp; navigation is 56×64dp. Their needle visuals shrink from 44dp to 36dp while touch bounds remain at least 48dp.

Each zoom tap applies ZOOM_STEP = 1.0 and clamps to MapLibre's minZoomLevel/maxZoomLevel.
CameraPosition.Builder retains target, bearing, tilt and padding. The controller does
not call the gesture callback or change follow/orientation. Immediate camera updates
accumulate rapid taps. Pinch and other manual gestures retain their previous behavior.
Unused ScaleMode/ScalePreset state and the preset cycle were removed after auditing all callers.

ZoomScaleControl width is 60dp, horizontal ruler padding 6dp each side, bar maximum 48dp and preferred
minimum 22dp. It keeps the 10/20/50/100/200/500m and 1/2/5/10km labels. Projection at the
visible viewport center supplies meters/pixel. The bar is exactly distance / metersPerPixel;
it is never visually clamped independently of its label. The previous value can remain
within the lower hysteresis band, but may never exceed the maximum. At gaps between nice
values a shorter bar is allowed. At extreme close zoom where 10m cannot fit, or when the
bar would be subpixel, the ruler is hidden. Labels use single-line labelSmall typography.

Controls use 48dp or larger touch targets. At less than 260dp available map height,
the compass sits beside the vertical zoom/ruler column to avoid the bottom-right location
group. Current-location and route-overview controls remain bottom-right; FREE actions
are bottom-left. Built-in MapLibre compass remains disabled.
