# Review011c — Phase008.5C 所定経路ライブラリ

## 1. Scope / 2. BASE_SHA / 3. Previous review

Scope: persistent prescribed route library, CRUD, exact restoration, editor draft integration and stable navigation identity.
Phase008.5B is accepted PASS / COMPLETE. Phase009 Detour / Rejoin is not implemented.

BASE_SHA captured by actual preflight: `90a8db48de73f5f932a00c5575bed8fefbfe91ed`.
Tracked worktree was clean. Existing untracked `.vscode/` and `gradle/gradle-daemon-jvm.properties` were preserved, not staged or deleted, and excluded from archive payloads.
No reset/checkout/clean, bulk staging, private addresses, serials, raw GPS traces, local.properties, server .env, map data or APKs were committed.

## 4. Existing repository audit

- ScheduledRoute owns immutable geometry/points, metadata and optional guidance. Guidance includes maneuver enum, geometry index ranges, verbal instructions, streets and HighwaySign consecutiveCount.
- RoutePlan owns editor input and has an ID independent of ScheduledRoute and the new library ID.
- VehicleProfile includes ID/name, length/width/height, weight and optional axle load.
- ScheduledRouteRepository / InMemoryScheduledRouteRepository previously supplied a debug sample. Both build types now start with no active route. The sample factory remains only an explicit test fixture.
- NavigationStateHolder already resets route generations, matcher/deviation/progress and highway caches on snapshot replacement. Saved open uses that same path.
- NavigationUiState now owns activePrescribedRouteId, display name, NavigationMode and isNavigationStarted.
- RoutePlanEditorViewModel retains the Phase008.5B cursor, sheet, camera and selection state. replacePlan increments revision and replaces the working input.
- RouteCalculationViewModel remains the only routing operation owner; calculate accepts the saved vehicle profile.
- MainActivity injects the repository from a simple application-context singleton container.

## 5. Storage decision / 6. DB schema / 7. Payload schema / 8. Mapping

Room 2.8.4 + KSP 2.3.4 + kotlinx.serialization 1.9.0. Verified with AGP 9.4.0 / Kotlin 2.3.21 / API23 minimum.
Room was selected for atomic route snapshots, coroutine DAO access and versioned schema tracking; geometry is not thousands of rows or a Preferences DataStore value.
[Official Room release](https://developer.android.com/jetpack/androidx/releases/room#2.8.4) and [KSP guidance](https://developer.android.com/build/migrate-to-ksp).

DB `prescribed-routes.db`, version 1, table `prescribed_routes`:
id primary key; name; description; createdAtEpochMillis; updatedAtEpochMillis; schemaVersion; payloadJson; distanceMeters; startName; destinationName.
Exported schema: `app/schemas/net.nobu0707.busnav.data.storage.prescribed.PrescribedRouteDatabase/1.json`.

Payload V1 stores RoutePlan (ID/name/all point IDs/types/coordinates/names), ScheduledRoute (ID/name/all geometry points/all route points/metadata),
all guidance maneuvers and signs including pre/post verbal instructions and consecutiveCount, and the complete VehicleProfile.
Record envelope and summary metadata are DB columns. Record name is canonical; rename intentionally preserves historical names inside the exact payload.
Pure DTO mappers keep serialization annotations out of domain models. Nullable guidance/axle load and all enum variants in the synthetic fixture are covered.

## 9. Identity / 10. Exact restoration / 11. No-network open

UUID is generated independently of name, plan ID and calculated route ID. Same-name records are allowed.
Open loads the stored snapshot, prepares navigation caches and requests overview; no RoutingEngine or HTTP call is in the library load path.
Recording-engine tests verify open, rename, delete, duplicate and starting an edit cause 0 routing calls; explicit calculation causes exactly 1.
The device UI scenario sets its routing engine unavailable after save and confirms map route layer and exact guidance/geometry survive open and Activity recreation.

## 12. CRUD / 13. Create / 14. Draft / 15. Recalculation

Navigation's operations card exposes “所定経路 • 一覧・保存”; bottom bar stays unchanged and single-line.
Empty state, create, list, current indicator, distance, updated time, endpoints, description, open/edit/overflow actions are present.
Create uses the existing editor and candidate preview; an adopted unsaved route can be registered from the library.
Saved edit creates a working snapshot with source record ID. DB is untouched until save. Back cancels.
The confirmed draft plan must equal current editor input before save. Domain validation additionally checks point ID/type/coordinate order against the calculated route.
Changed points require explicit calculation and candidate confirmation. Invalid candidates do not replace current snapshots.
Overwrite preserves ID and created time. VehicleProfile is retained and passed back to RoutingEngine.
Blank name, invalid plan, geometry/index inconsistency and invalid numeric profile/metadata are rejected.

## 16. Duplicate / 17. Delete policy

Duplicate / Save As generate a new UUID and creation/update timestamps while preserving exact geometry/guidance.
Original record stays unchanged. Delete requires confirmation.
Policy A: selected/active ID cannot be deleted; end use first. UI disables it and state holder rechecks at execution.
Room save transactions with existingOnly stop stale edits from resurrecting deleted rows. Rename/delete SQL is atomic; UI busy state serializes commands.

## 18. Active identity / 19. NavigationMode / 20. Sample cleanup

Navigation retains activePrescribedRouteId with PRESCRIBED mode. FREE is a foundation enum only.
“開く” selects and shows overview. “その他” → “ナビに使用” additionally starts the navigation theme.
Library/editor remain LIGHT. Route replacement uses existing matcher/deviation/progress/highway reset behavior.
No automatic debug or release sample is loaded or inserted into the saved library.

## 21. Large route / 22. Corruption/schema / 23. Performance

Synthetic/public data: 4,001 points and 100 maneuvers, all signs, detailed instructions, route/plan/profile fields.
Codec roundtrip and device save→close DB→reopen→load compare the entire record exactly, then delete.
Payload UTF-8 size: **304,773 bytes**. No simplification or decimal rounding.

One measured sample (wall-time, not a benchmark or latency guarantee):

| Device | Save | Load after reopening DB |
| --- | ---: | ---: |
| Emulator Android 16 | 158 ms | 103 ms |
| Physical Android 14 | 140 ms | 185 ms |

Malformed JSON returns Corrupt without deletion. Unknown DB payload version returns Unsupported; payload's own version is inspected before mapping future DTO fields.
Missing/deleted records produce an actionable error. List queries never deserialize the large payload.
No payload logging. DB work and codec work run on IO. Future DB migrations must be explicit and preserve records; no destructive fallback.

## 24. Unit tests / 25. Connected / 26. Emulator / 27. Physical

- Unit: **244 tests**, 0 failures (baseline 224 + 20 prescribed-route tests).
- Device instrumentation: **58 tests per device**, 0 failures, 0 skipped with local services available (baseline 52 + 6).
- lint, assembleDebug, assembleRelease and assembleDebugAndroidTest: PASS.
- Device DB tests: exact large-record restart persistence, summary consistency, rename/update/sort/delete, corruption/unsupported, concurrent rename/delete and stale overwrite rejection.
- UI E2E: empty→create/calculate→apply/save→list→Activity recreate→offline open/map layer/guidance→edit/cancel unchanged→edit/recalculate/overwrite→rename→duplicate→delete duplicate→original remains→end use/delete test original.
- Public Kanto route: Tokyo station vicinity to Ueno station vicinity calculated by actual local Valhalla, saved, DB reopened, exact geometry and guidance restored, renamed and deleted on both devices.
- Existing matching/deviation, general/highway guidance, theme, route editor camera/cursor/sheet/20-point scrolling/rotation, basemap and developer-settings tests included.

Initial run found a test-only wrong map layer identifier; corrected to the existing overlay constant.
The first run also skipped server-dependent cases while WSL was stopped. Services were brought up by starting the existing WSL environment, and final runs used dedicated temporary ADB reverse ports 18082/18088 to existing 8002/8080 services. No firewall or permanent endpoint change is needed.
Final review checks are rerun against the committed HEAD; their authoritative logs/counts are included in the archives.
Temporary test endpoint overrides and ADB reverse mappings are reset after verification.

## 28. Known limits / 29. Phase008.5D handoff / 30. Phase009 handoff

- Saved library persists across process death. The last selected record and unsaved draft are not auto-resumed after process death; reopen from the library. Activity recreation retains state.
- New route creation retains the existing development vehicle default. Vehicle editing UI is outside this phase; saved profile is preserved exactly.
- Background basemap offline distribution is outside scope. Saved route geometry/guidance restoration itself requires no routing server.
- No export/import or search UI; updated descending ordering is implemented.
- Phase008.5D: FREE mode and broader session UX.
- Phase009: refer to activePrescribedRouteId for detour/rejoin. No detour/rejoin logic was added.

## 31. Commits / 32. Archives

Implementation: `2a3b8a3d2a1bddc9449560696675e34f05d1bc3c` — feat: persist prescribed route library with exact restoration and draft editing.
Documentation commit: `docs: record prescribed route library design and verification`.
The final full HEAD is recorded in the archive's `meta/review-info.txt` and `checks/review-check-summary.txt` to avoid a self-referential SHA in this document.

Run `run-review-checks.ps1`, `make-review-archive.ps1` and `make-full-review-archive.ps1` with BaseRef equal to the captured BASE_SHA.
Deliverables: `busnav-review-latest.zip`, `busnav-full-review-latest.zip` and timestamped equivalents.
Each archive contains `meta/archive-self-check.txt`; publication requires RESULT: PASS and 0 prohibited entries.
Runtime/test DB, WAL/SHM/journal files, local IDE/JVM settings, .env, map data, APK/build files and device serials are excluded. Schema JSON is intentionally included.
Archive self-check uses the actual committed HEAD and captured BaseRef.

Implementation and functional verification: **Phase008.5C COMPLETE**.