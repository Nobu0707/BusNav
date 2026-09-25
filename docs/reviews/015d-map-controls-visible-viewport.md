# Review 015d — Map controls, visible viewport, Japan-only integration

## 1. Scope and baseline

Phase 010.6D begins at `d66831cf353efef43d8e75c166a1847bdde1f571` (BASE_SHA). The preflight worktree was clean. No pull, rebase, merge, server change, or map data regeneration was performed.

## 2. Code audit and decisions

The prior navigation frame used 0.44 × visible height as MapLibre top padding, yielding a 0.72 marker fraction. The editor cursor drew at full MapView center and registration projected that full center. Shield span already used a partial bottom inset, while fit and cursor used different coordinates. Developer Connections exposed KANTO and CHUBU, and debug defaults used local regional endpoints.

`VisibleMapViewport` now owns clamped insets and center coordinates. MapController uses its rectangle for cursor projection, shield/facility span, scale preset and ruler. The editor cursor draws at the same visible center. Sheet height is reported during drag; moving the sheet alone does not pan the map. Bounds fit uses bottom sheet padding, then clears MapLibre's persistent padding while preserving the map transform. FREE and Detour currently use zero bottom inset.

## 3. Japan-only basemap and remote policy

JAPAN is the only official app basemap. KANTO/CHUBU enum IDs remain solely for decoding old preferences; reads and updates normalize to JAPAN and persist `japan` in active and saved profiles. The region selector is removed. Debug defaults and REMOTE_TEST use `https://routing-busnav.nobu0707.net` and `https://maps-busnav.nobu0707.net`; Light/Dark paths are the nationwide styles. Local profile code remains for development, but live instrumented test settings choose REMOTE_TEST and ignore local endpoint overrides. No local Valhalla, TileServer, or LAN live smoke was run. Future Search live tests must likewise use a production-like remote endpoint. The server is IPv6-only; network reachability is recorded per device.

## 4. Navigation anchor and regression

HEADING_UP top padding changed from 0.44 × visible height to 0.70 × visible height. MapLibre therefore places the raw GPS marker at `visibleTop + 0.85 × visibleHeight`, leaving 15% below it. The same map-local formula applies in portrait and landscape. NORTH_UP retains its centered policy. The location fix still drives camera and marker in one NavigationMapFrame, with no shifted GeoJSON point or trailing transition. Existing stationary, off-road start, device compass, screen-on, shield/facility/intersection, Traffic, FREE, PRESCRIBED, Detour and Light/Dark checks remain in unit or connected coverage.

## 5. Controls

Non-navigation bearing at least 4° from north shows the 64×56dp north-reset compass; navigation shows only the existing orientation control. Reset eases bearing to 0° and preserves target, zoom and tilt. The scale button cycles 500m / 1.8km / 5km visible vertical spans. A pinch sets CUSTOM, whose next tap chooses NORMAL. Navigation preset updates zoom only, preserving follow, orientation, bearing and 0.85 anchor. The ruler projects two screen points near visible center, measures geographic distance and chooses a readable m/km nice value. Existing 75ms camera-detail coalescing and a 10% ruler width band limit label flicker. Shield show/hide thresholds remain 2.2/2.6km.

## 6. Validation policy and results

Targeted unit tests passed during development. Final unit, lint, Debug, Release and AndroidTest builds passed after fixing one obsolete regional-style expectation and a transient Windows JAR lock. Connected suites are run once per device after a successful result; retries are limited to failure, install/infrastructure failure or a test code fix.

| Device | Attempts | Result | Count and notes |
| --- | ---: | --- | --- |
| Pixel_8 Emulator / Android 16 | 6 | All cases passed across required retries; final full-suite run had one Compose idle timeout | Initial offline run stopped before tests on an uncached UTP dependency. First full suite: 78 cases, 10 failures, followed by test fixes. Targeted viewport and remote UI runs passed. Second full suite: 74 cases, 73 passed and one Compose idle timeout; that method passed on its targeted retry. No successful case was repeated for reassurance. |
| SOG06 Physical Android / Android 14 | 1 | Gradle connected suite PASS | 74 cases: 58 executed, 16 remote-map reachability assumptions. Remote routing cases passed. No retry. |

The initial Emulator full run contained 78 tests and 10 failures: three obsolete `@Ignore` regional cases were reported as failures by UTP, one new viewport test asserted before Compose updated, three tests tried to set a regional value on REMOTE_TEST, one remote availability probe failed, and one Route Editor test still expected the old full-map center. The obsolete regional cases were removed; the new map control and Route Editor tests passed in targeted reruns. The second full run's sole Compose idle timeout passed when retried in isolation, with no source change. This is an aggregated green result, not a single uninterrupted full-suite PASS. Remote unreachability is logged and does not trigger a local fallback.

## 7. Manual and remote smoke

Emulator one-round manual smoke: the remote Japan map rendered around Kanto, the scale button changed CUSTOM to NORMAL, and the projected ruler changed 100 m to 200 m. In Route Editor, the cursor appeared at the center of the visible map above the sheet. The remote routing and Japan map connected tests passed on Emulator. The nationwide source is fixed to `japan.json`; a manual Kanto-to-Kansai pan was not performed.

Physical one-round manual smoke: the scale button changed to NORMAL and the ruler to 200 m. The Route Editor cursor appeared at the visible map center; tapping register increased the point count from zero to one. The remote Japan map did not render on the device network and showed the disconnected banner, matching the 16 connected-test reachability assumptions. Remote routing tests passed. No local server was used.

Manual two-finger rotation and north reset, HEADING_UP marker placement in portrait and landscape, device-compass response, synchronized moving GPS follow, and KEEP_SCREEN_ON could not be visually established in this stationary ADB session. Unit and connected tests cover the 0.85 camera-anchor formula, compass policy, cursor screen-point projection and relevant Phase010.6C regressions; real-world GPS and landscape visual confirmation remain open. No operation was performed while a vehicle was moving.

## 8. Limits and Phase 010.7 handoff

The legacy regional server setup remains as historical tooling, but it is outside this phase's acceptance. Search server implementation remains Phase 010.7 work; its eventual live tests must use a production-like remote service. IPv6 reachability may differ between Emulator and Physical Android. A connected PASS cannot substitute for manual visual inspection of small-screen readability and real-world GPS behavior.

## 9. Commits and archives

Implementation and initial review commit: `a0841840128d9dfb1405dc971ce9e81ee01fe8e5` (`feat: unify Japan map controls and visible viewport`). This final result update is a separate documentation commit. The final HEAD is recorded in the archive metadata and final report. Lightweight and full review archives are built from that HEAD after the verified checks are summarized, without rerunning successful connected suites. Archives exclude device serials, APK/build files, GPS traces, private addresses, local.properties, .env, PBF/MBTiles and secrets.
