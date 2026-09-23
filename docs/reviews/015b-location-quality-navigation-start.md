# Review015b — Phase 010.6B Location quality and navigation start

## 1. Scope and base

Android foreground navigation start and location quality. `BASE_SHA` is `93e52ffa135c7a80c400a38ad67a79c0423e5f45`. The starting tree was clean; the normal and ignore-space-at-eol diffs were empty. No server-side Ubuntu changes were made.

## 2. Original symptom and gate

The on-screen `位置が不確実です` comes from `guidanceUiState` in `GuidanceUiState.kt` when projection reliability is uncertain. That message is separate from the actual FREE route calculation gate: `FreeNavigationConfig.locationProblem` required accuracy at most 50 m and age at most 10 s. `FreeNavigationStateHolder.calculate` applied that gate and reported `現在地の精度が十分ではありません`. FREE preview start previously had no independent quality gate; PRESCRIBED activation previously had no shared quality gate. `RouteMatcherConfig` separately required accuracy at most 40 m and age less than 10 s for a reliable match.

## 3. Android request and permissions

`AndroidLocationProvider` uses `LocationManager`, not Fused Location Provider or `LocationRequest`. It already requested GPS and NETWORK at 1 s with 1 m minimum displacement and had no wait-for-accurate-fix setting. The GPS request is the high accuracy source both during navigation and before it, while the app is in the foreground. It now requests GPS only with `ACCESS_FINE_LOCATION`; approximate-only permission uses NETWORK, avoiding an all-provider `SecurityException`. Transitioning from approximate to precise restarts the subscription to add GPS. Manifest declares both FINE and COARSE, with no background location permission. The app does not launch Settings automatically.

## 4. Quality and start policy

`LocationQualityPolicy` classifies 0–30 m as GOOD, over 30–100 m as USABLE, over 100–150 m as DEGRADED, greater than 150 m or invalid accuracy as UNUSABLE, and fixes older than 15 s or with invalid monotonic timestamps as STALE. Boundaries are inclusive. These are heuristics, not calibrated probabilities. Both FREE and PRESCRIBED explicit starts use the same gate. The FREE routing START is a selected raw GPS point, never a snapped projection.

`RecentStartFixes` retains ordered raw fixes. It selects the most accurate start-eligible fix within 10 s, or one within 15 s if no preferred fix qualifies. Duplicate and out-of-order timestamps are rejected. A single poor fix cannot erase a recent usable fix. The map marker and continuous matcher still receive the newest raw fix.

## 5. Guidance, deviation, highway, and arrival

The start gate does not loosen `RouteMatcherConfig` (40 m, under 10 s). At 120 m the session can start, while the matcher marks the projection UNRELIABLE. `RouteDeviationDetector` does not promote this to OFF_ROUTE, general turn distance/instructions stay uncertain, and JCT/exit guidance is suppressed by existing reliability gates. A later reliable fix restores normal guidance. Arrival retains its existing stricter accuracy and repeated-evidence rules. Detour raw START also retains its stricter existing config.

## 6. UI and modes

FREE preview enables start only while the shared policy accepts a fix. DEGRADED displays a lower-accuracy warning. Blocked conditions display obtaining, updating, insufficient precision, or precise-permission text. PRESCRIBED start invokes the same policy and keeps the route open when blocked. `Approximate` is distinct from `Granted` and offers an explicit precise-location permission action. The raw GPS marker continues to display the latest fix. Neither HEADING_UP/NORTH_UP nor Light/Dark, traffic overlays, remote Japan connections, routing endpoints, or server data were changed.

## 7. Tests and checks

Pure unit tests cover all accuracy boundaries, invalid values, age 5/10/15 s, future and out-of-order timestamps, recent-best behavior, one poor fix, and GPS/NETWORK request policy. Integration tests cover FREE and PRESCRIBED starts at 120 m, guidance suppression and recovery at 20 m, and approximate permission. Existing matching, deviation, highway, detour, arrival, traffic, and route tests were rerun. AndroidTest source compilation is run, without device execution.

Results on the final source: `test` PASS (408 unit tests), `lint` PASS, `assembleDebug` PASS, `assembleRelease` PASS, and `assembleDebugAndroidTest` PASS. The first combined lint attempt hit a Kotlin lint analyzer internal error; a later single-worker, standalone lint run passed. No connected test was invoked.

## 8. Device exclusions and limits

Emulator: NOT RUN (user requested). Physical Android: NOT RUN (user requested). `connectedDebugAndroidTest`, adb, and managed-device tests: NOT RUN (user requested). Field calibration of 30/100/150 m heuristics, GPS behavior indoors, and battery impact remain for a later physical-device review.

## 9. Commits and archives

Implementation commit subject: `fix: relax navigation start location quality gating`. The review archive is `busnav-review-phase0106b.zip`; it contains the checked source diff, changed files, and check results. Excluded device tests are reported as NOT RUN. The final handoff records the resulting commit SHA.

## 10. Next handoff

Calibrate start thresholds and request behavior on physical Android after this excluded-device phase. In particular, compare urban parallel roads, tunnels, elevated highways, and approximate-only permission before changing matcher, deviation, or arrival thresholds.
