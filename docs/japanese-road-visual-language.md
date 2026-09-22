# Japanese road presentation — Phase010.5A

BusNav draws independently designed vector shield backgrounds and dynamic route numbers.
The supplied `images/reference_route_number_signs.png` is a shape/color reference only;
neither reference image is bundled as an application asset.

## Classification and tile contract

`JapaneseRoadNetwork.java` is the pure classifier shared by the Android tests and the
Planetiler profile. OSM `highway=primary` does **not** mean national road.

1. Recognized road-relation membership and explicit way network have priority.
2. A trusted provider can supply `expressway`, `national`, or `prefectural` explicitly.
3. For these Japanese regional extracts, motorway/motorway_link plus a complete E/C
   route token is the only fallback. Numeric-only motorway refs are insufficient.
4. Unknowns keep the neutral road hierarchy and have no shield.

Recognized networks: `JP:E`, `JP:C`, `首都高速道路`, `JP:national`,
`JP:prefectural` and its lowercase prefecture suffix. No prefecture label is guessed.
`network=road` in the original tiles is generic and is not national/prefectural evidence.
Walking/cycling relations and exit-node references are not route shields.

`BusNavProfile` subclasses the fixed OpenMapTiles profile and adds `route_network`
and, when valid, `route_ref` to existing **line features** in `transportation` and
`transportation_name`. Standard classes, names, refs, route relations and structure
attributes remain available. Attributes are added before line merging, preserving
network/ref boundaries. The OMT schema version remains 3.16.0; style metadata records
`busnav:road-schema=1`. Planetiler 0.10.2 stores one relation-info object per relation ID;
the extension preserves that original object and keeps its extra lookup separately.

When several networks overlap, choose expressway, national, prefectural in that order.
Within a category, relations are sorted by OSM relation ID and the first usable ref wins.
For a semicolon-delimited ref, only its first token is used: `1;246` becomes `1`.
An invalid first token is not silently skipped. Numbers stay paired with their network.
`E 1`/`C 4` normalize to `E1`/`C4`; national/prefectural numeric refs have 1–3 digits.
`国道246号` and `県道12号` are accepted in the matching category; road names such as
`4号新宿線`, ambiguous strings, full-width numbers and invalid numbers are not converted.

## Shapes, size and density

| Category | Background | Road: light / dark | Minimum zoom | Spacing |
| --- | --- | --- | --- | --- |
| Expressway | Green rounded rectangle, white text | `#397BB8` / `#629AD0` | 7 | 420 px |
| National | Blue rounded inverted triangle, white text | `#D16D61` / `#D7857D` | 8; 3-digit refs from 10 | 550 px |
| Prefectural | Blue hexagon, white text | `#50956C` / `#71AD86` | 13 | 680 px |
| Other | No shield | Existing neutral hierarchy | Existing | — |

Four Android vector drawables cover all numbers (one wider expressway background).
`JapaneseRoadShields` rasterizes these at 2× resolution and registers them on every
style load. There are no per-number PNGs or external sprite requests. Shield body colors
are identical in both themes: green `#08764D`, blue `#1552B6`, with white outlines.
Numbers are 13px centered text; the national shield has a small optical vertical offset.
Official-sign supplementary text is omitted at navigation scale for legibility.

Each SymbolLayer uses the same feature for its icon and text, line placement, viewport
alignment, sort key and collision detection. Neither icon nor text permits overlap or
ignores placement; both are required. Road names remain a separate layer, with shields
placed above them. Actual visible density depends on geometry, tile boundaries and
other labels, not a promised number of signs per screen or meters between signs.

Casing and increasing road widths remain intact. Tunnel dashes and bridge center
strokes use a lighter tint of the classified color, retaining both the category and a visible structural stroke over the road fill.
Active route cyan and its dark casing are drawn above basemap shields. Existing
detour, traffic, route-point and vehicle ordering is unchanged.

## Validation and limitations

Unit tests cover network precedence, unknowns, normalization, multi-refs, both palettes,
collision policy and theme parity. The profile integration test verifies both enriched
layers and preservation of OMT `route_1_network`. The pinned TileServer's MapLibre style
validator checks four regional/theme styles against actual source layers and attributes.
Android instrumentation covers live Kanto zooms 8/10/12/14/16 in both themes, suburban
and Chubu reloads, overlay order, rendered route/traffic symbols in both themes, and a synthetic nine-number fixture.

Incomplete OSM relation coverage produces deliberately neutral gaps, including some
segments with numeric refs. Unknown urban networks are not guessed. The chosen canonical
ref does not display every concurrent route. Overview zoom data is simplified by OMT.
Shield registration is provided by BusNav Android; a generic TileServer browser preview
does not install these application-owned images. The bundled offline fallback has no
road data. Release continues to use its existing fallback configuration.

Sources: [MapLibre SymbolLayer specification](https://maplibre.org/maplibre-style-spec/layers/#symbol),
[Planetiler SourceFeature API](https://github.com/onthegomap/planetiler/blob/v0.10.2/planetiler-core/src/main/java/com/onthegomap/planetiler/reader/SourceFeature.java).
See [Review014a](reviews/014a-japanese-road-map-style.md) for actual execution results.
