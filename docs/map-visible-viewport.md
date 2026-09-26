# Visible map viewport (Phase 010.6E)

MapViewportInsets / VisibleMapRect / VisibleMapViewport use MapView-local pixels.
Insets clamp to leave at least one pixel. Editor cursor registration and the projected
ruler/shield span use the same rectangle; the editor bottom inset follows its sheet.

Navigation bottom occlusion is max(measured FREE left group, measured right location group).
Both groups include 32dp bottom spacing for attribution, and at least 48dp buttons.
When FREE actions disappear their old measurement is ignored. Portrait guidance/warnings
are overlays, so their appearance does not resize the underlying map. Landscape keeps
its guidance side column outside the map. The top overlay does not shift the lower anchor.

HEADING_UP follows the same raw GPS point for marker and target. Its center is
visibleBottom − max(markerOuterRadius + 8dp, 24dp). The ring radius is 22.4dp plus
3.5dp outer half-stroke, giving a 33.9dp margin. Camera top padding is
2 × max(visibleHeight − margin, visibleHeight / 2) − visibleHeight; bottom padding
is the measured occlusion. NORTH_UP retains zero padding and physical centering.
Typical 672dp/272dp usable heights give fractions 0.9496/0.8754, compared with 0.85.
Tiny viewports prioritize marker safety; they cannot guarantee a fraction above 0.85.

MapScreen uses fillMaxSize AndroidView and MATCH_PARENT MapView layout parameters.
No bitmap stretching or fixed aspect ratio is used. MapLibre 13.6.1 onSizeChanged calls
NativeMap.resizeView. The controller now reapplies anchor padding on map-height changes
even if a still-fresh GPS fix has not changed. Editor fit already cleared temporary
padding; route/plan overview now clears padding on animation completion while preserving
the physical center. See Review015e for measured surface bounds and local projection tests.
