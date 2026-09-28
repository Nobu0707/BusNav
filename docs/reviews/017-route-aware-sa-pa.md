# Review 017 — Android route aware SA/PA and operations support

1. **Scope:** Phase 011B adds route corridor SA/PA candidates, planned rest stops, a map overlay, and local operations notices to Android navigation.
2. **BASE_SHA:** `b4ee930e666c88c2fde6e166a8924288c1dcdf82`.
3. **Phase 011A handoff:** Uses the existing production facility service and its geometric candidate contract. The published Tokyo to Nagoya reference was 59 candidates; the Android live smoke asserts a nonempty ordered response.
4. **Endpoint:** HTTPS POST `https://search-busnav.nobu0707.net/busnav/v1/route-facilities`, 1,200 m corridor, SA and PA, limit 100. The fixed host currently requires global IPv6. No local server or fallback is used.
5. **Provider abstraction:** `RouteFacilityProvider` and domain results isolate UI, progress, and stop planning from the HTTP response.
6. **Route fingerprint:** SHA-256 of normalized continuous polyline6, corridor, and sorted types. A session cache keeps successful results; a generation ID rejects stale responses.
7. **Multi leg:** The existing Valhalla parser joins decoded leg shapes and removes repeated boundary points. `RouteFacilityPolyline` encodes the continuous route at precision 6 and has an explicit leg join test.
8. **Query trigger:** Only active geometry changes start a facility query. Route activation, FREE recalculation, detour, rejoin, and replacement qualify. GPS, matching, heading, and camera updates do not.
9. **Candidate semantics:** Stable OSM type and ID, SA/PA type, point, route progress, corridor distance, and `GEOMETRIC_CANDIDATE`. The Android parser drops invalid candidates, sorts by progress, and accepts an empty 200 response.
10. **Current progress:** The existing route matcher supplies reliable progress. Distance ahead is candidate route progress minus reliable matched route progress. Unreliable matches hold the last value but display a distance confirmation state.
11. **Upcoming filtering:** The sheet shows at most 20 unpassed candidates in route order. A candidate more than 500 m behind reliable progress stays passed until the route changes.
12. **Direction limitation:** Corridor proximity does not prove carriageway, legal access, or entrance availability. Facility name text never raises direction confidence; the sheet and detail display the caveat.
13. **UI:** The existing route menu opens the SA/PA sheet. It presents loading, empty, unavailable, candidate, planned, and selected detail states with approximate distances.
14. **Map markers:** Custom SA/PA badges render while the sheet is open. Selected and planned markers remain visible, with different colors. Selection moves the camera and uses the existing recenter policy.
15. **Planned stops:** Multiple OSM identities can be added in route order, without duplicates, and removed from the list or detail. A secondary next stop summary and one visual notice within 3 km use reliable local progress. Passed stops remain recorded.
16. **Persistence and migration:** Prescribed records use JSON payload v2 with `plannedStops`; v1 loads an empty list. The unchanged Room table remains at schema 1. FREE stops stay in the ViewModel session, including manual route replacement.
17. **Route edit reconciliation:** Stored OSM identities are retained. A new candidate response refreshes point and progress; missing identities are marked `NOT_ON_CURRENT_ROUTE_CANDIDATES` for review, without deletion.
18. **Detour and rejoin:** Each active geometry is queried or loaded from the session cache, and planned stops are reconciled against that geometry.
19. **Safety:** Known speed above 2.0 m/s disables detail selection, stop add/remove, and route changes. The list remains readable. Facility errors never stop navigation or trigger a reroute.
20. **Privacy:** No precise route, polyline, current GPS, request body, or device serial is logged or included in review artifacts.
21. **Attribution:** The sheet and detail credit OpenStreetMap contributors.
22. **Unit tests:** 451 tests passed, zero failures or skips, including parser, status mapping, encoding, cache and trigger behavior, stale responses, progress, stop planning, and payload migration.
23. **Static checks:** `test`, `lint`, `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` passed on the final source tree.
24. **Physical attempts and result:** Full run 1 failed at APK installation because an existing app had a different signature. A separate `.phase011b` Debug application ID allowed coexistence without deleting that app. Full run 2 executed 84 tests and found two old-test expectations, which were fixed. Full run 3 passed 85/85 tests with zero failures, errors, or skips. No successful full run was repeated..
25. **Live remote smoke:** Physical Android production routing and facility POST passed for a public Tokyo to Nagoya route; the result contained candidates in progress order. Exact candidate count was not written to the test log.
26. **UI interaction smoke:** Physical Android passed route menu, SA/PA sheet, candidate selection, local progress, planned stop add/remove, and landscape retention. A manual round on the installed Debug APK used a public Tokyo Station area to Nagoya city route (352.7 km). At least six candidate rows were visible; one example was Shirobebashi green belt, Smoking area (PA). The operator viewed the direction caveat, map badges, candidate detail and OSM credit, added a stop, closed and reopened the sheet, removed the stop, and confirmed landscape display. Location permission was withheld for this round, so precise ahead distance and active guidance were covered by physical instrumentation instead..
27. **Emulator:** NOT USED, per Phase 011B policy.
28. **Archive physical rerun:** NO; both archives use `-SkipChecks`.
29. **Limitations:** OSM coverage and IPv6 availability can leave the feature unavailable. Same carriageway, legal access, occupancy, closures, weather, and company synchronization are not established.
30. **Next handoff:** Phase 011C can verify direction and access topology. Phase 012 can add voice guidance. Licensed live traffic remains outside this phase.
31. **Commits and archives:** `478bc28538b20143de5b0e12279e58375c1a8acd` contains implementation, tests, and feature docs. This Review017 documentation commit follows. The review and full archives use `-SkipChecks` from final HEAD; their `*-latest.zip` aliases are recorded in the final handoff..
