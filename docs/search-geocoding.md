# Search and geocoding (Phase 010.7B)

## Architecture and endpoint

`PlaceSearchProvider` is a domain interface for forward search, reverse geocoding and OSM ID lookup. The production implementation is `NominatimPlaceSearchProvider`; the Compose UI and state holder consume only domain results. JSONv2 parsing and category normalization live in `NominatimParser`.

The production endpoint is `https://search-busnav.nobu0707.net` over HTTPS. Search uses `/search` with `q`, `format=jsonv2`, `countrycodes=jp`, `accept-language=ja`, `addressdetails=1`, `namedetails=1`, `extratags=1`, and `limit=10`. Reverse uses `/reverse` with `lat`, `lon`, and the same detail and language parameters. Lookup uses `/lookup?osm_ids=...` only after the user selects a result. URL query parameters are encoded by OkHttp. Production has no alternate or public Nominatim endpoint.

## Results and categories

Each result carries a stable OSM `N`/`W`/`R` ID, name, display name, coordinates, category, original category/type, address summary, importance, name details, extra tags, and optional distance from the current position. Japanese `name:ja` takes precedence over `name`, then the first part of `display_name`. Invalid coordinates or missing OSM identity discard only the affected row.

`highway=services` and `highway=rest_area` map to SA and PA. `railway=station` and `public_transport=station` identify stations; platform and entrance remain visible with their own source type. `highway=motorway_junction` maps to interchange. Shop, restaurant, airport, ferry terminal, tourism, public facility, place, and address categories use OSM category/type or tags. Names alone do not classify SA/PA or junctions. Unrecognized features remain `OTHER`. Nominatim ranking can place a platform, entrance, or nearby POI above the intended facility, so the UI always shows candidate category/type and location and requires a tap.

## Bias and request policy

The search dialog offers nationwide, current location, and visible map bias. A separate checkbox applies `bounded=1` only for a chosen visible map. Current location bias uses a nearby viewbox and does not bound national results. Map bias uses MapLibre's visible bounds. Typing two or more characters waits 450 ms; a single character requires IME Search. Query or bias changes cancel the previous coroutine, and a generation ID prevents an old response from replacing a newer result. HTTP 429 is shown without automatic retry. Other errors have a manual retry button. Network, timeout, and server errors include an IPv6 requirement hint, without asserting that IPv6 caused the failure.

## UI and route actions

The map control opens a search dialog with Japanese IME Search, bias controls, loading/empty/error states, candidate list, and `© OpenStreetMap contributors`. The result row displays name, category/source type, address or location, and current-position distance when available. A selected result creates a distinct blue BusNav map pin, moves the camera to at least zoom 15 while preserving bearing, and opens an action dialog. The pin is reinstalled on every map style load. Selecting a result never calculates a route.

On the normal map, destination starts the FREE destination selection flow; via and shaping open the Route Editor. In FREE selection, destination changes only `FreeNavigationPlan.destination`. The Route Editor offers START, DESTINATION, VIA, and SHAPING. Detour editing offers VIA and SHAPING. The selected result's name is stored with route points or the FREE destination, without relying on the OSM ID for restoration. The action dialog also supports inspecting the pin on the map.

Map cursor and route editor points register coordinates immediately. Reverse geocoding runs asynchronously afterward. A successful reverse label is prefixed `付近:` because it may name a nearby POI. Failure leaves the registered point and coordinate fallback intact. Lookup is limited to the selected OSM ID and cached for the session, including IDs absent in a successful response.

When known speed exceeds 2.0 m/s, the search text field is disabled and the dialog asks the user to stop safely. Unknown speed is not treated as driving. No persistent query history is written, and the search implementation does not log queries, addresses, or precise coordinates.

## Limits and Phase 011

Search requires reachability of the IPv6-only production host. OSM coverage and Nominatim ranking determine available candidates. The current implementation has no corridor SA/PA search, live occupancy, custom ranking, voice input, private address database, or search-history sync. Corridor and operational support remain for Phase 011.

## Route facility separation

Phase 011B route-aware SA/PA candidates use the dedicated POST `/busnav/v1/route-facilities` service and the active route geometry. Place Search remains an arbitrary location search with `/search`, `/reverse`, and `/lookup`; its candidates are not used to infer same-direction access. See [route-facilities](route-facilities.md).