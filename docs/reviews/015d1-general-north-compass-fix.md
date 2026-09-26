# Review 015d1 — General north compass rotation / review test de-duplication

## Scope and baseline

Phase 010.6D.1 follows up the fixed north-reset indicator found in Phase 010.6D review.
BASE_SHA: `df2fa18600648ab2dd4cd21546002c7e8c237383`.
Preflight had no tracked changes; existing untracked `.vscode/` and
`gradle/gradle-daemon-jvm.properties` were left untouched and are not committed.
No server changes, Phase 010.7 work, unrelated refactoring, reset, clean, or bulk staging.

Implementation commit: `2a75c81050a742f4ed2df31f5a68e45205667181`
(`fix: rotate general north compass with map bearing`).
Initial tested app tree: `d7a0c76de67be60fa116a84e18ab203896402876`.
Test-only follow-up: `a4cd0a0ffd2bb140ce54ee5b0e370aed5af4d349`
(`test: isolate compass pixel checks from tap ripple`).
Final app tree: `94e34b6b88d506f1af29825035e747a45d791dbc`.
Production main tree remains `1e356c44e1a0b0623e8bb1de39d77a15122b32c7` across all runs.
Final documentation commits do not change the app tree; final HEAD is in archive metadata.

## Original issue and fix

The non-navigation control previously drew fixed `Text("N ↑")`, regardless of camera bearing.
`GeneralNorthCompass` now applies the existing navigation helper
`northScreenRotation(cameraBearing)` to its Canvas pointer using Compose `rotate`.
There is no separate bearing-sign calculation. The button remains 64 x 56 dp with the
same click action and accessibility description. Its centered N stays upright so 90/180/270
degrees remain readable; only the directional pointer rotates around the center.

| MapLibre camera bearing | Shared helper / clockwise screen rotation | Pointer |
| ---: | ---: | --- |
| 0 | 0 | UP (general control hidden at north-up; isolated render verified) |
| 90 | 270 | LEFT |
| 180 | 180 | DOWN |
| 270 | 90 | RIGHT |

Unit coverage also checks 359/1 wrap, the neutral zone, the 4/356 visibility boundary, and
navigation/general exclusivity. Instrumentation checks actual needle-pixel direction for all
four bearings in Light and Dark, fixed label/touch-target bounds, real MapLibre geographic-north
projection, map camera changes, tap callbacks, and exclusive navigation display.
This is a directional pixel assertion, not a pixel-perfect screenshot comparison.

## Tap and visibility

`MapController.resetNorth` still uses `easeCamera` with the existing animation duration.
Its unchanged CameraPosition builder operation is extracted to `northUpCamera` for unit coverage.
Only bearing changes to 0; target, zoom, tilt and padding are preserved.
The connected map test taps each rotated cardinal, checks target/zoom/tilt, and also resets at
30-degree tilt. Navigation state remains inactive after a general reset.
Visibility policy is unchanged: inactive + at least 4 degrees from north shows the general control;
north-up and 359/1 are neutral/hidden; active navigation shows only NavigationCompass.

## Final static validation

Unit, lint and production builds each ran successfully once on the unchanged final production code (2026-09-26 UTC).
The initial PowerShell helper-loading attempt was blocked by execution policy before Gradle
started; process-scoped Bypass allowed the actual validation. After a Physical test assertion failed,
only the AndroidTest APK was rebuilt once to include the test-only ripple fix (the permitted
failure-fix exception). No successful production check or passing device case was repeated.
Lint completed with 0 errors, 21 warnings and 2 hints.

| Command | Result | Evidence |
| --- | --- | --- |
| `gradlew.bat test --console=plain` | PASS — 421 tests, 0 failures/errors/skips; 3 new unit cases | `checks/gradle-test.txt` |
| `gradlew.bat lint --console=plain` | PASS | `checks/gradle-lint.txt` |
| `gradlew.bat assembleDebug --console=plain` | PASS | `checks/gradle-assemble-debug.txt` |
| `gradlew.bat assembleRelease --console=plain` | PASS | `checks/gradle-assemble-release.txt` |
| `gradlew.bat assembleDebugAndroidTest --console=plain` | PASS — initial build, then one necessary rebuild after test failure fix | `checks/gradle-assemble-android-test.txt` |

## Connected validation

| Device | Attempts | Final result | Test count |
| --- | ---: | --- | --- |
| Pixel_8 Emulator / Android 16 | 1 | Suite PASS | 76 passed, 0 failed, 0 skipped |
| SOG06 Physical / Android 14 | 3 | Aggregated PASS | Attempt 1: install failure, 0 tests. Attempt 2: 75 passed / 1 failed. Attempt 3: failed method only, 1 passed. All 76 unique cases passed, 0 skipped. |

Emulator evidence is the preserved TestRunner transcript (76 start/finish pairs and terminal
0-failure summary) and the observed Gradle HTML report (76 tests, 100% successful).
The capture wrapper exited 1 after suite completion because PowerShell treated a JVM
sun.misc.Unsafe deprecation warning on stderr as a terminating error; its Gradle exit code was not
captured and is not claimed as 0. The first XML-copy helper used the wrong document-root shape,
so the next device run replaced the raw HTML/XML before preservation. The independent complete
TestRunner transcript is retained in `checks/phase0106d1-emulator-testrunner.txt`.
No emulator rerun was performed to recover a report.

Physical attempt 1 encountered INSTALL_FAILED_UPDATE_INCOMPATIBLE (existing APK signature
differed). Gradle misleadingly exited 0 although AndroidTestRunner reported an install failure;
this is recorded as FAIL / 0 tests, not PASS. Runner cleanup left no installed BusNav package.
Installing the unchanged debug and test APKs then succeeded. Attempt 2 is the allowed retry after
install failure, with separate stderr capture and mandatory result-count checks.
Attempt 2 finished with one failure: `renderedCardinalsKeepUprightLabelAndFixedTouchTarget`
could not match the pointer color at 180 degrees. The preceding saved Physical 90-degree image
showed an active tap ripple. Production/native-map cardinal, reset and exclusivity tests passed.
The correction moves all tap checks after all pixel assertions, preserving exact color tolerances
and directional checks, and saves images before assertions for future diagnosis.
Attempt 3 reran only that failed method and passed. The other 75 successful cases were not rerun.
This is an aggregated green Physical result, not one uninterrupted full-suite PASS.
The Emulator already passed this unchanged production implementation; no Emulator rerun was needed
for the test-only sequencing correction.

Evidence: `checks/phase0106d1-physical-attempt1.txt` through `attempt3.txt`,
`phase0106d1-physical-junit.txt`, `phase0106d1-physical-targeted-junit.txt` and
`phase0106d1-physical-final-cases.txt`. The retry method name/class matches the sole full-run failure.
The connected summary explicitly rejects the first attempt's misleading Gradle exit status.

Successful suites are not rerun for reassurance or packaging. Device identifiers are not recorded.
The full suite includes existing remote assumptions; no separate live-server rerun or local-server test
is added. Phase010.6D remote and cellular evidence remains in Review015d.

## Manual smoke and visual inspection

Physical manual smoke: PASS, user-reported on 2026-09-26, one stationary round.
After automatic tests completed, the unchanged Debug APK was reinstalled and BusNav was opened.
The user confirmed all requested items with no issues: approximately 90/180-degree map rotation,
north-pointer direction, tap to north-up, target/zoom retained, and only the navigation orientation
control after navigation starts. No second manual smoke was requested or performed.

The assistant inspected saved Emulator and Physical compass crops: the indicator points to
the expected sides, and the centered N remains upright/readable. Isolated Light/Dark pixel tests
cover all four cardinals. Automated crops are evidence of rendering, not a substitute for the
separately user-reported manual result.

## Phase010.6D regression

Production changes are limited to the general compass rendering and extracting its existing
bearing-only reset builder. Navigation compass, heading resolution, synchronized camera follow,
HEADING_UP 0.85 anchor, scale/ruler, visible viewport/cursor, route-point registration,
JAPAN-only basemap, remote-only live policy, device heading and KEEP_SCREEN_ON code are unchanged.

Regression evidence includes `NavigationRuntimeReliabilityTest` (portrait/landscape 0.85 anchor,
same GPS fix for marker/camera, device-compass math, screen-on policy), `MapControlsViewportTest`,
`RemoteJapanIntegrationTest`, and the existing connected camera/orientation, scale/ruler/cursor,
route editor and FREE/navigation suites. Connected outcomes and any assumptions are reported above;
real-world moving GPS behavior is not newly claimed as manually verified.

## Review/archive workflow

Audited `run-review-checks.ps1`, `make-review-archive.ps1`,
`make-full-review-archive.ps1` and their shared helpers.
The runner executes all static checks and adb/connectedDebugAndroidTest.
Both archive scripts accept `-BaseRef` and `-SkipChecks`; without SkipChecks they call the runner.
Packaging uses existing check receipts, not the runner. ZIP self-check and Android review signals
perform only filesystem/Git reads and do not call adb or connected tests.

Future policy, also recorded in `docs/review-archive.md`:
Review archive generation is separate from test execution.
Connected tests have one successful run per device on final implementation.
Do not rerun them during archive generation. Retry only failure, timeout, install/disconnect/
infrastructure failure or a fix for that failure.

Archive connected tests rerun: NO.
Archive mode: `-BaseRef df2fa18600648ab2dd4cd21546002c7e8c237383 -SkipChecks`.
Deliverables: `busnav-review-latest.zip` and `busnav-full-review-latest.zip`, plus timestamped copies.
Both archives are generated after this document commit using the saved check evidence.
Self-check receipts are inside each ZIP at `meta/archive-self-check.txt`; generation does not
invoke static or connected tests. See archive metadata for final HEAD and exact archive filename.

## Final status

Phase010.6D COMPLETE for this compass follow-up: production/unit/static checks pass, all 76
unique device cases pass on each device (Physical uses the documented failure-only retry), and
the user confirmed the one-round Physical manual smoke. Archive-only generation retains these
results without device test reruns.

Limits: Emulator Gradle process exit was not captured because of the logging-wrapper warning,
but its 76/76 completion is preserved independently. Physical has an aggregated result rather
than an uninterrupted full-suite PASS. No new moving-GPS road test or separate remote-server smoke
was performed; Phase010.6D live evidence and broader real-world limitations remain in Review015d.
