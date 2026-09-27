# Review 016 — Android search and geocoding

1. **Scope:** Phase 010.7B adds Android place search, geocoding, candidate selection, a map pin, and route actions. Corridor SA/PA and operational data remain Phase 011.
2. **BASE_SHA:** `3d91ba3ebd36be7a6b84936b3fe153e099105a43`.
3. **Phase 010.7A handoff:** Uses the existing self-hosted Japan-wide Nominatim 5.3.2 service; no server edits or local Nominatim setup.
4. **Endpoint:** `https://search-busnav.nobu0707.net`; `/search`, `/reverse`, `/lookup`, HTTPS only. The app has no public or local fallback.
5. **Provider abstraction:** `PlaceSearchProvider` and domain results keep UI independent of HTTP/JSONv2.
6. **DTO/parser:** `NominatimParser` ignores unknown fields, safely drops rows with invalid coordinates/identity, retains source tags and Japanese names.
7. **Category mapping:** Explicit `highway=services/rest_area`, station, motorway junction, shop, restaurant, airport, ferry, tourism, amenity, place and address tags. Name text does not classify SA/PA or IC/JCT.
8. **Bias:** Current location and visible map use Nominatim viewboxes; a separate map checkbox adds `bounded=1`.
9. **Debounce:** 450 ms for two or more characters; a single character needs IME Search. No automatic retry on 429.
10. **Stale cancellation:** Query or bias changes cancel the previous coroutine; a generation ID guards against late results.
11. **UI:** Map search control and a dialog with field, bias, loading/empty/error states, candidates and IME Search.
12. **Result rows:** Name, category/source type, address/location and optional distance; the first candidate is never auto-adopted.
13. **Pin:** Separate blue BusNav marker, camera focus with preserved bearing; style reload reinstalls it.
14. **FREE integration:** Destination selection sets `FreeNavigationPlan.destination` and name. Search selection alone never invokes routing.
15. **Route Editor integration:** START/DESTINATION/VIA/SHAPING actions save coordinates and name. Detour supports VIA/SHAPING.
16. **Reverse:** Cursor and editor points register immediately. Async reverse adds a tentative `付近:` label when available; failure leaves the point. FREE keeps the hint outside `FreeNavigationPlan`; editor name enrichment does not change the route revision.
17. **Lookup:** Selected result only, session cache; no ten-result fan-out.
18. **Attribution:** `© OpenStreetMap contributors` in result list and detail.
19. **IPv6/errors:** Network and timeout failures show a connection message and IPv6 requirement hint without diagnosing the cause; 429 has a wait-and-retry message.
20. **Safety lock:** Known speed above 2.0 m/s disables search text editing. Unknown speed does not lock input.
21. **Unit tests:** 442 tests, zero failures. Parser, provider requests/errors, debounce/stale results and route actions added. Final lint, Debug, Release and AndroidTest APK builds passed.
22. **Emulator connected:** INCOMPLETE. The first full run had two failures, including the late reverse revision issue. The second full run became unresponsive after 37 tests and was stopped as an infrastructure failure. A single-device retry was stopped at the user's request. There is no successful full emulator run.
23. **Physical connected:** PASS, 83 tests, zero failures on the second full run. The first run exposed three FREE reverse-enrichment failures and one new UI assertion failure; those were corrected. No successful physical run was repeated.
24. **Live remote smoke:** `curl --ipv6` to production `/search` with `q=東京駅` and required parameters returned HTTP 200, 8,842 bytes from an IPv6 address. The final physical Android instrumentation test queried the same production host in Japanese and passed. The emulator had no global IPv6 route, so its production-host smoke was skipped. No fallback.
25. **Privacy:** No persistent search history or query/address/precise-coordinate logging in search code. Archive excludes local properties, credentials, APKs and private traces.
26. **Limitations:** Depends on production IPv6 reachability and OSM coverage/ranking. Reverse can return a nearby POI. The emulator has no confirmed successful full connected run or direct production-host smoke. No corridor search or live occupancy.
27. **Phase 011 handoff:** Corridor SA/PA search and operational support remain unimplemented.
28. **Commits:** `9db75a9` implementation; `ea8c31b` AndroidTest compile fix; `1ca6a92` FREE reverse hint fix; `82bc1e0` route revision fix; `e1355b7` live Android smoke; review commit to follow.
29. **Archives:** Review and full archives are made with `-SkipChecks -AllowIncompleteConnected`, preserving the failed connected check log and the passing static checks.
30. **Archive connected rerun:** NO. Archives use `-SkipChecks` and do not invoke device tests.

## Check history

The first review-check pass succeeded for Unit, lint, Debug and Release. `assembleDebugAndroidTest` failed on an unused AndroidTest import, so the connected task did not start. The import was removed in `ea8c31b`; the AndroidTest APK then built successfully. Only the failed AndroidTest build and connected task were rerun.

The next full connected run reached both devices and reported 82 tests each. The new UI assertion expected `駅` while the result row showed `駅 / station`; this was corrected. Three existing FREE tests on the physical device showed that asynchronous reverse enrichment must not mutate a cursor destination plan. The emulator route UI test showed that a late reverse label must not increment a route revision. Both behaviors were corrected and covered by unit tests.

The final automated review pass succeeded for all static checks. The physical device completed 83/83 connected tests, including production search. The emulator stopped responding during its run; the resulting combined connected check is FAIL. A subsequent emulator-only attempt was interrupted on explicit user instruction. No successful device test was run again for archiving. Phase 010.7B remains INCOMPLETE until an emulator with production IPv6 connectivity completes the required smoke and full connected validation.
