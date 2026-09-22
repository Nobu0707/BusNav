# Road details and visible map scale

Phase010.5A.1 adds two point layers to the pinned BusNavProfile. Existing OMT layers and relation attributes remain intact.

## Source evidence

`busnav_expressway_facilities` includes `highway=motorway_junction` nodes and `barrier=toll_booth` nodes connected to an explicit motorway/motorway_link way. This also covers national expressways; no urban membership is invented for a facility. Toll booths in parking areas or with no motorway connectivity are excluded.

An explicit `JCT` / `ジャンクション` suffix in the source name or `junction=yes` on a motorway junction yields `junction`. Literal `入口` / `出口` suffixes yield entrance/exit; `出入口` and unqualified motorway junctions use the generic interchange icon. `本線料金所` / `toll=mainline` yields the mainline variant in the data; it currently shares the generic toll artwork. Source names are preserved, without appending IC. Ref, operator, network, exit_to, direction, toll and toll:etc are copied only when present. No entrance direction, ETC-only flag or route operator is invented.

`busnav_named_intersections` accepts named traffic_signals or junction=yes nodes, excluding motorway_junction. Bare named nodes and named pedestrian crossings are not sufficient. Name:ja precedes name. The rank is a display heuristic: signalized nodes referenced by at least two trunk/primary ways have rank 0; other accepted nodes rank 1. Splitting a road into ways can affect rank. It never changes the name or facility type.

## Presentation

Original vector icons distinguish access, junction and toll. Paired label layers use `jp-facility-label` plus dynamic white text, `icon-text-fit=both`, and a 2.6 em text offset to leave space beside the icon. Both text and background use viewport alignment. Intersection labels use pale/dark neutral backgrounds according to theme. Symbols participate in collision detection; some points/labels are intentionally omitted when crowded.

The implementation follows [MapLibre icon-text-fit](https://maplibre.org/maplibre-style-spec/layers/#icon-text-fit) and the [Android PropertyFactory API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-property-factory/index.html).

## Scale and layering

MapController projects the top-center and bottom-center of the visible MapView rectangle and measures their great-circle distance. Route Editor bottom occlusion is subtracted. Vertical distance is the portrait requirement and also evaluates the shorter dimension in landscape. More general occlusion/padding integration remains for Phase010.5C.

All route shield layers start hidden. They show at <=2,200 m and remain visible until >2,600 m. Invalid/empty projections hide them. JCT <=5,000 m, access <=3,000 m, toll <=2,000 m; major intersections <=3,000 m and normal intersections <=1,500 m. Minimum zoom and symbol spacing further restrict density.

Camera/layout/occlusion changes coalesce into a 75 ms update. Style properties are written only when visibility changes. Every style reload reinstalls assets, clears the per-layer cache, reapplies the current span, and restores ordering.

Road lines/names → active/candidate/detour lines → intersections and road shields → facility icons/labels → traffic → route points/current position. The invisible `busnav-shield-anchor` is the runtime insertion boundary. Fallback styles without shields retain a valid overlay order.
