# Map controls (Phase 010.6D)

The map's right edge holds one compass, the scale preset button, and a projected scale ruler. Active navigation uses `NavigationCompass`; all other map modes show the general north button only after the camera is at least 4° from north. Tapping it eases bearing to 0° with the current target, zoom, and tilt. The touch target is 64×56dp and its description is `北を上に戻す`.

The preset button cycles NEAR (500m visible vertical span), NORMAL (1.8km), and WIDE (5km). Pinch zoom changes the mode to CUSTOM; the next tap selects NORMAL. The zoom calculation compares the current projected visible span with the preset target. It changes only zoom, so navigation follow, bearing, orientation and camera padding remain intact. NORMAL is below the existing 2.2km road shield show threshold; the 2.2/2.6km hysteresis is unchanged.

The ruler samples two MapLibre screen points around the **visible viewport center**, converts both with `projection.fromScreenLocation`, and measures geodesic distance. It selects a 10/20/50 × power-of-ten distance near 80–140dp and formats m/km. The existing 75ms map detail update coalesces camera movement; a 10% width band retains the previous label around nice-value boundaries. Projection sampling works while the map is rotated.

The built-in MapLibre compass remains disabled. The general button and navigation compass are mutually exclusive. The control stack is inside each map pane in portrait and landscape; navigation's separate current-location action remains available.
