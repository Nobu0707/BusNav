# Visible map viewport (Phase 010.6F)

MapViewportInsets / VisibleMapRect / VisibleMapViewport use MapView-local pixels.
Insets clamp to leave at least one pixel. Editor cursor and projected ruler/shield span
retain the same viewport model; the editor bottom inset follows its sheet.

Both portrait and landscape place warnings/guidance over the full-width MapArea.
No operations panel or left guidance column remains. The top overlay is measured;
its height places map controls below the cards. It does not shrink the native MapView.

Navigation bottom occlusion is max(measured FREE left group, measured right group).
Both include 32dp attribution clearance. When FREE disappears its old height is ignored.
For map width >=560dp each corner uses a horizontal button row: the normal occlusion
is 80dp instead of 136dp. Narrower map panes retain vertical corner groups.

HEADING_UP target y = visibleBottom - max(markerOuterRadius + 8dp, 24dp).
The ring radius is 22.4dp plus 3.5dp outer half-stroke: margin = 33.9dp.
The old max(visibleHeight - margin, visibleHeight / 2) floor is removed.
Let offset = 2 * desiredY - visibleHeight. Top padding = max(offset, 0);
bottom padding = measuredOcclusion - min(offset, 0). Negative offsets therefore use
additional bottom padding, not a centered fallback. NORTH_UP keeps zero padding.

The top safety threshold is measured overlay bottom + marker margin. If it fits below
the bottom-safe target there is no collision; if it exceeds that target the viewport
cannot fit the complete marker between overlays. Bottom safety has priority, with no
claim of collision-free operation in physically impossible sizes. Controls sit at the
right edge, outside the central marker corridor. Extremely tiny panes cannot contain a
whole marker. Short controls can scroll without intercepting gestures elsewhere.

MapScreen retains fillMaxSize AndroidView and MATCH_PARENT MapView parameters.
MapLibre's existing onSizeChanged/native resize path remains intact. The controller
refreshes padding for map height, overlay measurements and fresh fixes. Route/plan
fit padding cleanup, Route Editor behavior and projection isotropy are unchanged.
Stale fixes still do not drive camera updates. See Review015f for actual projected
points, measured corner occlusion, native surface bounds and rotation evidence.
