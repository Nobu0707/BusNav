# Review 015g — navigation theme, guidance, controls and landscape anchor

## 1. Scope and 2. BASE_SHA

Phase010.6G adjusts only Android UI/theme/layout and camera presentation. The preflight HEAD was `0c83bdd5a542d309a64237dee65f4abdf3a748c2`. Preflight tracked diff and ignore-space-at-eol diff were empty. The existing untracked Gradle property, user reference images and prompts were preserved. No server/network change, tile regeneration, Search work or unrelated refactor was made.

## 3. Visual references

`reference_free_navigation_dark_bug.png`, `reference_prescribed_routes_dark_bug.png`, `reference_free_navigation_landscape_narrow_map.png`, `reference_landscape_vehicle_too_high.png`, `reference_landscape_guidance_target.png`, and `reference_portrait_guidance_target.png` under `images/` were inspected as visual requirements, not used as app assets. The dark-screen screenshots showed light color-scheme text over a dark window background. The landscape screenshots informed the guidance split and lower right vehicle target; no other product's icon, color or brand was copied.

## 4–5. Theme root cause and fix

`ThemeModeResolver`, `NavigationRoute` screen/started/route state, `BusNavTheme`, screen-local MaterialTheme usage, dialogs and panel colors were audited. The light color scheme was already selected for Free and Library. Their screen roots were transparent, allowing the dark host window background to show through, so light-theme text appeared nearly invisible. `NavigationRoute` now paints one root Box with `MaterialTheme.colorScheme.background`. Theme resolution explicitly requires the navigation screen, started session, active route, and night or tunnel. Free selection and preview, prescribed library, editor and developer connection remain LIGHT. Map style follows the same resolution. No screen-local white text/background patch was used. Material content colors remain scheme based.

## 6–7. Free landscape width and prescribed library

Free selection/preview map weight changed from 0.58 to 0.76 of the usable row; controls changed from 0.42 to 0.24 and their buttons stack in landscape. The selection cursor still reads the MapLibre map center and destination overlay uses the selected GeoPoint. A Compose bounds test checks 74–78% map width. The emulator's `free-general-preview-landscape.png` shows the broad map, light title/help/summary panel and readable actions; its deterministic fallback map reports the detailed-map server as disconnected. Prescribed Library retains its original controls and data behavior under the corrected light root background.

## 8–10. Warning, guidance and severity

The standalone deviation banner was removed from `NavigationTopOverlay`; `NavigationGuidanceCard` renders its message inside the same card ahead of the maneuver. The original warning test tag and polite live region are retained for accessibility and existing callers. Warning-only, guidance-only and combined states use one card; the warning is not duplicated. Prominent route-deviation warning uses vivid light red `#D80E2F` or dark red `#FF5C6C`; neutral position-confirmation text uses `onSurfaceVariant`.

## 11–12. Portrait size and typography

The prior compact card used 8dp padding, titleMedium instruction and titleMedium symbol, with distance inheriting default text size. The new portrait card has a minimum of 22% of MapArea height (capped at 180dp), maximum 28% of MapArea height, 12dp padding, and internal scroll for longer content. Typical height therefore grows from a roughly 60–80dp compact row to around 150–180dp on ordinary portrait maps, subject to actual content and viewport. Symbol is 40sp, primary 28sp, distance 32sp, secondary 18sp and warning 21sp. A Compose bounds test checks the card stays within 20–30% of MapArea while containing the warning.

## 13–14. Landscape guidance and vehicle target

The top-left overlay is 54% of MapArea width, leaving the upper right for controls and map. The card uses a compact row in landscape. The raw GPS GeoPoint stays the marker and camera target. For active, following landscape HEADING_UP only, native left camera padding is half the MapView width, targeting projected X≈75% of map width. Y targets `mapHeight − 56dp`, aligned with the bottom action button centerline and capped by the marker's bottom safety margin. The right action group stacks along the map edge so the marker does not overlap it; this was corrected after reviewing a real emulator screenshot. Portrait HEADING_UP keeps its old center X and visible-bottom−33.9dp Y. NORTH_UP and non-following retain zero new horizontal padding.

## 15–17. Projection, compass and unified ZoomScaleControl

`NavigationConsolidationTest` obtains `projection.toScreenLocation(currentLocation)` from actual MapLibre, checks the landscape X/Y targets within 3 physical pixels, portrait unchanged X/Y, MapArea/MapView/surface bounds, and local 100m north/east isotropy. Navigation compass bounds changed 64×80dp → 56×64dp; general compass 64×56dp → 56×56dp; inner visual changed 44dp → 36dp, with >=48dp touch bounds. One 60dp surface now contains +, projected ruler, −; no separate ruler card exists. Each zoom target remains 60×48dp. The ruler's 10m–10km nice-distance choices and projected `distance / metersPerPixel` width remain truthful, with a 48dp bar maximum inside 6dp horizontal padding.

Focused final emulator projection at density 2.625: portrait projected vehicle `(519.75, 1507.01)`px in a 1953px-high map, visible bottom 1596px, thus 88.99px (=33.9dp) above it. Landscape MapView 1974×901px projected vehicle `(1480.5, 755.0)`px, exactly 75% of width and 146px (=55.6dp) above physical bottom. Compose right action bounds clear the marker's 25.9dp outer radius. Both local isotropy measurements were approximately 1.0000056. The final landscape screenshot shows the vehicle between the bottom left and right action groups, with no control overlap; the map itself uses a deterministic fallback basemap for this test.

## 18. Aspect regression

Actual native projection testing checks Compose MapArea = MapScreen = MapView = GL surface dimensions through portrait → landscape → portrait and warning insertion/removal. The local projected north/east ratio must remain 0.9–1.1. No bitmap stretching or marker coordinate replacement was introduced.

## 19–23. Validation and archive policy

Final code static gates: `test` 429/429 PASS, zero failures/errors/skips; `lint` 0 errors, 21 warnings, 2 hints; `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` PASS. Each has one successful run on the final code in `build/phase0106g-evidence/gradle-final.txt`. An earlier focused physical install attempt failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; it is not counted as a test pass. Existing app data was not removed. Focused emulator projection/layout tests passed. One focused portrait height test first found a card exceeding the target cap; the responsive clamp was added and its retry passed. A prior combined static run failed one ruler-width assertion; the 48dp maximum was restored before the final pass.

Emulator full attempt 1 was stopped at case 12/80 when inspection of an actual screenshot revealed a vehicle/control overlap; it is not counted as a successful run. A focused final projection test passed after stacking the right actions and updating the camera anchor. Emulator full attempt 2 on the final APKs: **80/80 PASS**, zero failures/skips, 571.907 seconds (`build/phase0106g-evidence/emulator-full-final.txt`). No successful emulator full rerun was performed.

The physical device first rejected a Gradle-installed APK with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; this is an install failure, not a connected test pass. After emulator completion, a direct `adb install -r` of the final debug APK succeeded without an explicit uninstall or data deletion (`build/phase0106g-evidence/physical-apk-install.txt`). The final AndroidTest APK was installed, and physical full attempt 1 completed: **80/80 PASS**, zero failures/skips, 488.627 seconds (`build/phase0106g-evidence/physical-full-final.txt`). The actual MapLibre projection, card, layout and theme tests passed on this device. No successful physical full rerun was performed.

The emulator manual screenshot round checked a light Free selection screen, a wide landscape map, the half-width guidance, compact controls and lower-right vehicle clearance. Physical manual smoke requires confirmation that the device is stationary and available for interaction; it is pending that confirmation. The connected suite validates the listed UI behavior with deterministic fixtures but does not substitute for a human visual inspection of the physical screen.

Archive connected rerun: **NO**. Archives use the `-SkipChecks` path and preserved receipts; device tests are not rerun during archive creation.

## 24–26. Limits, handoff, commits and archives

The supplied screenshots cannot establish the exact stale-fix/follow state at capture time. Projection tests use stationary synthetic fixes; they do not establish driving comfort. Very short viewports may require card or control scrolling. Phase010.7 Search remains untouched. This phase is committed under `Phase010.6G refine navigation UI theme and guidance layout`; the final commit SHA is recorded in Git and both archive manifests. The review and full-review archives are created with `scripts/make-review-archive.ps1` and `scripts/make-full-review-archive.ps1`, respectively, using `-BaseRef 0c83bdd5a542d309a64237dee65f4abdf3a748c2 -SkipChecks`. Their timestamped names and SHA are reported in the handoff.
