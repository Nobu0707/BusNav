# Review015c1 — Physical Android package recovery and validation

## Baseline and device

BASE_SHA: a687c7fbca3f2a00e25a264f5fc80763c88a3ed6. The physical device was SOG06 on Android 14, with only the primary Android user. No device serial, private address, GPS trace, APK, signing key, or full logcat is retained in this review or the archives. Existing untracked .vscode/ and gradle/gradle-daemon-jvm.properties were left alone. No server source changed.

## Package, launcher, signature, and data

At preflight, net.nobu0707.busnav was **E: completely absent**. It appeared in neither normal pm list packages nor -u (retained) nor -d (disabled). pm path and dumpsys package had no package record, and resolve-activity found no activity. There was no secondary user or work profile to inspect. The source manifest declares exported .MainActivity with MAIN and LAUNCHER.

Review015c recorded INSTALL_FAILED_UPDATE_INCOMPATIBLE against a then-existing BusNav install, which establishes an incompatible signing certificate at that time. That old APK was absent before this recovery, so its certificate and the reason for its disappearance cannot be determined directly. The fresh APK passed apksigner verify --print-certs and uses CN=Android Debug. After installation, a temporary pull of the installed APK showed the same SHA-256 signing certificate as the current debug APK; the pulled file was deleted. A later install -r of a debug APK also succeeded without a signing conflict.

No stale-package cleanup, adb uninstall, pm clear, or work-profile deletion was performed in this task. **Prior app-data preservation: unknown.** There was no retained package state to inspect; this review does not claim that the prior Prescribed Route Library, Room database, or DataStore survived. A clean install succeeded, pm path returned an APK, and the launcher resolved to .MainActivity. monkey launched the app and topResumedActivity identified .MainActivity. Gradle connected tests remove the app when they finish, so it was reinstalled for normal-launch smoke and will be reinstalled after final review checks.

## Targeted location fix

Stationary physical GPS fixes periodically became older than the app's 15-second start-recency gate while the original LocationManager request required at least 1 m displacement. This left the FREE route start waiting at 現在地を更新中です even with location permission and a visible marker. AndroidLocationRequestPolicy.minimumDistanceMeters was changed from 1f to 0f so stationary devices can receive fresh fixes. Its unit assertion now requires 0f. No other application behavior or server source was changed.

After the fix and same-signature update install, three UI snapshots about 10 seconds apart all showed a live following state without the updating or low-accuracy warning. A real GPS FREE route calculation and stationary start then succeeded.

## Connected and build checks

The physical serial was selected explicitly through ANDROID_SERIAL with no emulator selected. The initial 77-test physical attempt used emulator-default 10.0.2.2 service URLs: 31 service assumptions were unmet and two NavigationScreenTest display assertions failed. The five NavigationScreenTest cases passed when rerun alone. With USB adb reverse for ports 8002 and 8080 and runtime test URLs on 127.0.0.1, the complete pre-fix physical suite passed: **77 total, 77 passed, 0 failed, 0 errors, 0 skipped**. The two display assertions did not recur in the full rerun; their initial cause is not established. The post-fix full physical suite also passed: **77 total, 77 passed, 0 failed, 0 errors, 0 skipped**.

After the source fix, test, lint, assembleDebug, assembleRelease, and assembleDebugAndroidTest passed. The post-fix unit XML counted **415 total, 415 passed, 0 failed, 0 errors, 0 skipped**. A fresh emulator connected suite after the fix, with both local services kept available, passed **77 total, 77 passed, 0 failed, 0 errors, 0 skipped**. Review archive checks will be generated for the final HEAD.

## Stationary physical smoke

| Check | Observation |
| --- | --- |
| Normal launch and navigation screen | Passed. The app launched from LAUNCHER and displayed the navigation screen without a crash. |
| Location permission and current location | Precise and coarse permissions were granted through the Android prompt. After the location fix, the UI showed fresh current-location state across three stationary snapshots, with the vehicle marker and following control visible. |
| Map rendering | The detailed map style loaded over USB reverse while the local services were running. No screenshot is archived. |
| Road-off FREE start | Passed while stationary using real GPS. The destination was set away from the raw marker; the preview showed a 0.9 km route, the route line began at a visually distinct snapped point on the nearest road, and 案内開始 entered active guidance. No departure or driving was performed. |
| HEADING_UP lower anchor | In active portrait guidance, the marker appeared below the center of the visible map along the route. Its exact 72% screen coordinate was not measured; landscape was not checked. |
| Marker/camera synchronization | An active session showed the marker and camera together at a stationary position. Successive physical location motion was not injected or driven; synthetic camera/location changes are covered by the physical connected suite. |
| Device compass | With guidance inactive and the portrait screen held, a roughly 90-degree clockwise device rotation changed the map marker's visible arrow from upper-left to lower-left while the map stayed north-up. The user's stationary rotation supplied this observation. |
| KEEP_SCREEN_ON | The active BusNav window carried the flag; after 案内終了, the flag was absent. Actual display timeout was not timed. Brightness was not changed. |

A device-wide mock GPS provider was considered for successive location smoke. Automatic approval review rejected granting shell mock-location permission while leaving a provider active across actions; no mock permission change or provider injection was performed. Real GPS was sufficient for the stationary FREE start. The local WSL development services stopped when no WSL session remained; a temporary WSL session kept them available for tests. The installed debug app's USB loopback endpoints require the host services and adb reverse to remain available.

## Phase state and limits

The package recovery, normal launch, post-fix physical and emulator connected suites, targeted stationary FREE start, orientation, and window-flag smoke support **Phase 010.6C COMPLETE**. Successive real marker/camera motion after departure, exact anchor percentage, landscape layout, actual display timeout, and prior app-data survival were not observed. Phase 010.6D remains out of scope.