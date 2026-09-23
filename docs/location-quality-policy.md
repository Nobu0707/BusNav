# Location quality and navigation start

`LocationQualityPolicy` assesses a raw fix using monotonic elapsed time and reported horizontal accuracy. These thresholds are heuristics for starting a session, not a guarantee of lane or road identity.

| Fix | Start | Strong guidance |
| --- | --- | --- |
| 0–30 m, age at most 15 s | Yes, GOOD | Only when route matching also agrees |
| Over 30–100 m | Yes, USABLE | Only when route matching also agrees |
| Over 100–150 m | Yes, DEGRADED | Suppressed |
| Over 150 m, invalid accuracy, or age over 15 s | No | Suppressed |

The newest raw fix remains the map marker and continuous tracking input. `RecentStartFixes` chooses the most accurate raw fix within the last 10 seconds for starting; if none qualifies it may use a valid fix up to 15 seconds old. Out-of-order fixes are discarded. This prevents one bad reading from immediately closing the start gate without keeping old coordinates indefinitely. FREE route calculation uses this selected raw point as START. Both FREE preview and PRESCRIBED route activation use the same gate. The stricter detour START and arrival thresholds remain unchanged.

The continuous `RouteMatcher` still accepts only reported accuracy at most 40 m for `MATCHED` and rejects fixes aged 10 seconds or more. `RouteDeviationDetector`, general turn guidance, highway JCT/exit guidance, and arrival remain dependent on reliable matching. A 120 m fix can therefore start navigation but shows uncertain guidance until a better fix arrives. The app never snaps the visible GPS marker to a route.

Android uses `LocationManager` GPS plus NETWORK at 1 second and 1 m minimum displacement with precise permission. This is the existing high accuracy GPS request, not Fused Location Provider. With approximate-only permission it subscribes to NETWORK only and asks for precise location in the UI. No wait-for-accurate-fix option or automatic Settings launch is used. Both coarse and fine permissions are declared; background location is outside scope.

Known limits: the thresholds are not calibrated against physical-device traces. Starting from a 100–150 m fix does not mean turn or lane guidance is reliable. Device testing is needed to assess request cadence, indoor/network behavior, and road geometry in the field.
