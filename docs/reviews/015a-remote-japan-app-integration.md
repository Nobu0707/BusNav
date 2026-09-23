# Review015a — Phase 010.6A Remote Japan app integration

## 1. Scope

Android connection profiles, nationwide basemap selection, compatibility and host
validation only. No SSH, server changes, graph/MBTiles generation, pull, rebase or
merge. No unrelated navigation refactor. Device execution is explicitly excluded.

## 2. BASE_SHA

`15d208d85d5143b3b0129b7112769a44bd4ce750`

## 3. Preflight

On 2026-09-23 (JST), status was clean. Reviewed the latest 20 commits, HEAD,
normal diff/stat and ignore-space-at-eol diff/stat; all starting diffs were empty.
No applicable AGENTS.md was found in the repository or parent directories.

## 4. Current connection architecture

`DeveloperConnectionSettings` groups routing URL, map URL and region, now also the
environment. Debug uses `DataStoreDeveloperConnectionRepository`; release uses
`FixedConnectionRepository` without opening the developer DataStore. Debug defaults
come from BuildConfig (emulator URLs/KANTO); release routing is build-configured and
its map default is empty. Those defaults remain unchanged.

MainActivity observes one settings flow for map configuration. ValhallaRoutingEngine
reads `connections.settings.first().valhallaBaseUrl` per request, so endpoint changes
do not require engine recreation. MapScreen retains MapController and passes updated
BasemapConfig to `updateBasemap`. BasemapController requests reload only on a config
change. There is no coordinate-driven region selection.

## 5. Server facts

User-supplied: Valhalla `3.9.0-a3a5631c4`, single `japan.mbtiles`, bounds
`122.5607,20.08228,154.4709,45.8154`, zoom 0–14, custom facilities/intersection layers,
public HTTPS certificate, localhost-only backends 8002/8080 and IPv6-only public DNS.
Supplied server smoke covers Sapporo, Sendai, Tokyo, Nagoya, Osaka, Hiroshima,
Takamatsu, Fukuoka, Kumamoto and Naha, plus Tokyo–Nagoya, Tokyo–Sendai and
Osaka–Hiroshima. These server tests were not rerun as Android tests.

## 6. BasemapRegion.JAPAN

Added persisted ID `japan`, label 全国 / Japan. KANTO/CHUBU decode unchanged;
unknown/null region still falls back to KANTO for legacy/non-remote records. JAPAN
resolves to unqualified `/styles/busnav/style.json`, a single nationwide source.

## 7. ConnectionEnvironment

LOCAL_EMULATOR, LOCAL_LAN, REMOTE_TEST and CUSTOM have stable persisted IDs.
Developer Connections selects a draft profile; Save applies it. Non-remote profile
snapshots retain prior manual/LAN values across remote switches and restarts.

## 8. REMOTE_TEST

Central definition: `RemoteTestEndpoints`.

- Routing: `https://routing-busnav.nobu0707.net`
- Map: `https://maps-busnav.nobu0707.net`
- Region: JAPAN (locked)
- Light: `https://maps-busnav.nobu0707.net/styles/busnav-light/style.json`
- Dark: `https://maps-busnav.nobu0707.net/styles/busnav/style.json`

Remote URL/region fields cannot be edited. Repository validation rejects HTTP,
custom hosts and KANTO/CHUBU overrides on a remote profile. Inconsistent stored
remote records decode as the canonical full remote snapshot.

## 9. Style resolver

Uses the canonical server style JSON; no TileJSON parsing/reconstruction in Android.
Local regional paths remain unchanged. Pan never changes config, style or source.
Initial style failure uses only the existing embedded fallback with UNAVAILABLE;
source failures after style load mark UNAVAILABLE without a style replacement.
There is no regional fallback that could mask a failed nationwide map.

## 10. DataStore migration

Missing/unknown environment keys decode as CUSTOM while preserving existing URLs
and region. Read-only migration never changes legacy records. Save writes routing,
map, region, environment and profile snapshots in one `store.edit` transaction.
Reset removes active settings and snapshots. Host DataStore tests use the existing
Okio Preferences serializer harness and reopen the actual preferences file.

## 11. Legacy behavior

Legacy LAN example with KANTO and no environment key remains unchanged, including
after reading it. A remote round trip restores its manual/LAN values. Emulator
remains `10.0.2.2:8002/8080`, with its saved local region or KANTO. Local LAN restores
saved LAN values, falling back to legacy/manual values on first selection. Custom
restores saved manual settings. Legacy unqualified build style keeps its KANTO
default. Existing routing hot-reload tests still cover the next request using a
saved endpoint.

## 12. Atomic environment apply

One preferences edit and one emitted DeveloperConnectionSettings snapshot contain
the full environment. There are no separate routing and map writes. The emission
test rejects any mixed tuple. The existing asynchronous routing requests and native
map resource loads are not cancelled or awaited as a distributed transaction;
future requests/config consumption use the new snapshot.

## 13. Active navigation guard

The screen receives `uiState.navigationActive`, not the presentation-only flag that
becomes false when the settings dialog opens. Profile selection, fields, region,
Save and Reset are disabled with an explanation. Save/Reset also call tested
`updateWhenIdle`/`resetWhenIdle` guards before touching the repository. Checks and
Back remain available. Applies to active FREE/PRESCRIBED including detour sessions.

## 14. Light/Dark

ThemeModeResolver remains unchanged: normal LIGHT; active navigation plus night or
tunnel DARK. JAPAN round-trip URLs are covered alongside KANTO/CHUBU. IPv6 hint
survives theme copies. BasemapConfig changes trigger the existing style reload path.

## 15. Runtime shields

Every completed style load calls MapController.installOverlays, whose first step
calls JapaneseRoadShields.install without a region/mode condition. All 12 runtime
images remain registered: expressway/wide, urban/ring, national, prefectural,
facility label/access/junction/toll, and light/dark intersections. Both downloaded
public styles each reference 11 of these IDs; all are present (0 missing IDs).

## 16. Facilities/intersections

Read-only public style inspection confirmed both styles use only the vector source
`https://maps-busnav.nobu0707.net/data/japan.json`. Both include
`busnav_expressway_facilities` and `busnav_named_intersections`, facility
junction/access/toll icon/label layers and named intersection layers. IC,
entrance/exit, JCT, toll and green-label logic is unchanged. No server styles edited.

## 17. Route/traffic overlay restoration

Unchanged installOverlays restores route, detour, traffic, route-plan and vehicle
layers, calls OverlayLayerOrder.restore, updates visible-span details and renders
the latest location. Both public styles contain `busnav-shield-anchor`; route lines
remain below shields and traffic above them. A fake connection repository plus real
BasemapController/NavigationStateHolder test verifies remote selection requests
reload while the route and navigation state survive Light/Dark transitions.
Native image/overlay pixels were not exercised on a device.

## 18. IPv6-only limitation

Both hosts resolve AAAA `240b:12:1c09:a200::5`, with no A answer. Ordinary Android
DNS/HTTPS is used. Remote failed checks and unavailable map UI show
「現在のリモートテストサーバーはIPv6接続が必要です」 as a supplementary deployment
hint, not a diagnosis. No custom IPv6 sockets or network detection.

## 19. HTTPS/cleartext

REMOTE_TEST is canonical HTTPS-only. Debug HTTP manifest policy is unchanged;
release `usesCleartextTraffic="false"` and fixed-repository isolation are unchanged
and covered by the new test. No certificate pinning or release default promotion.

## 20. Unit tests

`./gradlew.bat test --console=plain`: PASS, 399 tests across 45 suites; 0 failures,
0 errors, 0 skipped. Includes 14 new RemoteJapanIntegrationTest cases covering the
requested A–T areas together with the existing region/theme/routing/navigation/map
policy tests. JVM state tests do not claim native MapLibre rendering validation.
The first attempt with the older workspace `.gradle-user` cache could not resolve
KSP 2.3.4; the normal user Gradle cache completed successfully. Java 21.0.9,
Gradle 9.7.1. Existing compiler warnings remain.

## 21. Lint

`./gradlew.bat lint --console=plain`: PASS (2m), 0 errors, 20 warnings, 2 hints.
Warnings concern existing dependency versions, Configuration sizing, logging and
version-catalog/KTX usage; hints concern existing primitive state boxing. No lint
error or newly introduced warning was found in this change.

## 22. assembleDebug

`./gradlew.bat assembleDebug --console=plain`: PASS (41s). Debug APK generated.

## 23. assembleRelease

`./gradlew.bat assembleRelease --console=plain`: PASS (3m 38s). Unsigned release APK
generated. Merged release Manifest still has `usesCleartextTraffic="false"`.
The existing native libraries were packaged without symbol stripping where the
toolchain reported it could not strip them; build completed successfully.

## 24. assembleDebugAndroidTest

`./gradlew.bat assembleDebugAndroidTest --console=plain`: PASS (1m 6s).
Instrumentation sources and APK compiled; no instrumentation was executed.

## 25. Optional Windows remote smoke

OPTIONAL / environment-dependent, performed 2026-09-23 JST. DNS: AAAA-only/no A.
Initial sandboxed HTTPS requests could not connect. Outside that network boundary,
`curl.exe https://routing-busnav.nobu0707.net/status` succeeded with the supplied
Valhalla version; HEAD of Light style returned HTTP 200. GET of both canonical
styles also succeeded and confirmed the source/layer/runtime image contracts above.
No server failure is inferred from the sandboxed attempt. No route calculation,
TileJSON reconstruction or Android network test was performed.

## 26. Emulator

Emulator: NOT RUN (user requested). No emulator launched; no adb invoked.

## 27. Physical Android

Physical Android: NOT RUN (user requested). `connectedDebugAndroidTest`, managed
device tests and physical smoke: NOT RUN (user requested). These exclusions are not
failures.

## 28. Regressions

FREE/PRESCRIBED, Detour/Rejoin, matcher/deviation, general/highway guidance,
HEADING_UP/NORTH_UP, compass and ring+arrow marker implementations are unchanged.
Existing JVM regressions pass. Road colors (expressway blue, national red,
prefectural green), urban/national marks and visible-span policies are unchanged.
The local NavigationCameraTest loop explicitly retains KANTO/CHUBU because that
fixture provisions only regional tiles; it was not expanded into a remote test.

## 29. Limitations

Device pixels, gestures, runtime shield/overlay restoration and Android IPv6
connectivity remain untested by explicit scope. Remote availability depends on
IPv6 reachability. Release production promotion is a separate decision. An
in-flight pre-switch route request is not cancelled by this phase; environment
switching during active navigation is blocked.

## 30. Phase010.5C handoff

After this app integration, resume Phase010.5C Visible Map Cursor. Do not treat the
excluded device tests as completed; carry them into a later authorized validation.

## 31. Commits

Commit subject: `feat: add Japan-wide remote map environment`.
The delivered archive metadata records exact final HEAD and BASE_SHA; the final
response supplies the commit SHA and full changed-file inventory.

Changed files (all under the app except the two documents):

| Area | Files |
| --- | --- |
| `app/src/main/java/net/nobu0707/busnav/developer/` | `ConnectionEnvironment.kt` (new), `ConnectionChecker.kt`, `DeveloperConnectionRepository.kt`, `DeveloperConnectionSettings.kt` |
| `app/src/main/java/net/nobu0707/busnav/map/` | `MapScreen.kt`, `basemap/BasemapConfig.kt`, `basemap/BasemapRegion.kt` |
| `app/src/main/java/net/nobu0707/busnav/ui/` | `navigation/NavigationScreen.kt`, `settings/developer/DeveloperConnectionScreen.kt`, `settings/developer/DeveloperConnectionUiState.kt` |
| `app/src/test/java/net/nobu0707/busnav/` | `developer/RemoteJapanIntegrationTest.kt` (new), `ui/theme/PresentationPolicyTest.kt` |
| `app/src/androidTest/java/net/nobu0707/busnav/map/` | `NavigationCameraTest.kt` |
| `docs/` | `deployment/remote-japan-app-integration.md` (new), `reviews/015a-remote-japan-app-integration.md` (new) |

## 32. Archives

Deliverables: `busnav-review-latest.zip` and `busnav-full-review-latest.zip`, with
timestamped copies recording the final commit prefix. The existing archive scripts
are used with `-SkipChecks` and the explicit BASE_SHA after recording the actual host
results and explicit SKIP device logs. run-review-checks.ps1 is not used because it
invokes adb. The scripts are unchanged.

Each archive's `meta/review-info.txt` identifies exact HEAD/BASE_SHA;
`meta/archive-self-check.txt` records entry counts and prohibited-file checks;
`checks/review-check-summary.txt` records this phase's results. The final delivery
also checks the new source/docs are included and no old device output/private-key
content is present. Exclusions: local.properties, .env, credentials, GPS traces,
serials, PBF/MBTiles, APK/build binaries, logcat and certificates/private keys.
