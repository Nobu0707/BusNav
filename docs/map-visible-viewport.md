# Visible map viewport (Phase 010.6D)

`MapViewportInsets`, `VisibleMapRect`, and `VisibleMapViewport` describe the MapView's unobscured rectangle in **MapView-local pixels**. Insets are clamped to leave at least one pixel. The Route Editor passes its current bottom sheet height, including drag positions, as the bottom inset. Navigation passes the measured bottom overlay occlusion. The map pane itself already excludes guidance and side panels.

The Route Editor cursor is drawn at `rect.centerX, rect.centerY`. Registration calls `projection.fromScreenLocation` on that same pixel through `MapController.cursorPosition()`. Sheet movement updates the inset without panning the camera. Route fit uses the sheet inset in MapLibre bounds padding, then removes persistent camera padding while preserving the physical map transform. Shield/facility span, preset zoom and ruler sampling all read the same viewport rectangle.

HEADING_UP uses the raw location as both the marker GeoPoint and camera target. MapLibre camera top padding is 0.70 × visible height and bottom padding is the bottom occlusion. Because the padded camera target lies halfway between the padded top and bottom, the marker appears at `top + 0.85 × visible height`: 85% from the visible top, 15% from the visible bottom. Portrait and landscape use the same formula. NORTH_UP keeps the existing centered camera policy. One accepted location fix continues to own the camera and marker update in the same map frame.

The model also applies when map width is reduced by a landscape side pane. FREE and Detour selection have no sheet inset today and therefore use the same viewport center with zero insets.
