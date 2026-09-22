package net.nobu0707.busnav.tiles;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.nobu0707.busnav.map.JapaneseRoadNetwork;
import net.nobu0707.busnav.map.RoadMapDetails;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;

/** Additive attributes before OMT merging; different networks/refs cannot merge across boundaries. */
public final class BusNavProfile extends OpenMapTilesProfile {
    public BusNavProfile(Planetiler runner) { super(runner); }
    private final java.util.concurrent.ConcurrentMap<Long, RoadRelation> roads = new java.util.concurrent.ConcurrentHashMap<>();
    record RoadRelation(long id, String network, String ref, String operator) implements OsmRelationInfo {}

    // Keep only candidate point IDs during pass 1, not every road/node in the region.
    private final java.util.Set<Long> detailNodes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> motorwayNodes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentMap<Long, Integer> majorWays = new java.util.concurrent.ConcurrentHashMap<>();
    @Override public void preprocessOsmNode(OsmElement.Node node) {
        super.preprocessOsmNode(node);
        if (node.hasTag("highway", "motorway_junction") || node.hasTag("barrier", "toll_booth") ||
            RoadMapDetails.namedIntersection(node.tags())) detailNodes.add(node.id());
    }
    @Override public void preprocessOsmWay(OsmElement.Way way) {
        super.preprocessOsmWay(way);
        boolean motorway = way.hasTag("highway", "motorway", "motorway_link");
        boolean major = way.hasTag("highway", "trunk", "primary");
        if (!motorway && !major) return;
        var seen = new java.util.HashSet<Long>();
        for (var node : way.nodes()) {
            long id = node.value;
            if (!detailNodes.contains(id) || !seen.add(id)) continue;
            if (motorway) motorwayNodes.add(id);
            if (major) majorWays.merge(id, 1, Integer::sum);
        }
    }
    private void processPoint(SourceFeature source, FeatureCollector features) {
        var type = RoadMapDetails.facility(source.tags(), motorwayNodes.contains(source.id()));
        if (type != RoadMapDetails.FacilityType.UNKNOWN) {
            var feature = features.point("busnav_expressway_facilities").setMinZoom(12)
                .setAttr("facility_type", type.name().toLowerCase(java.util.Locale.ROOT));
            if (!RoadMapDetails.name(source.tags()).isEmpty()) feature.setAttr("name", RoadMapDetails.name(source.tags()));
            for (String key : List.of("ref", "operator", "network", "exit_to", "direction", "toll", "toll:etc"))
                if (source.hasTag(key)) feature.setAttr(key, source.getString(key));
        }
        if (RoadMapDetails.namedIntersection(source.tags())) {
            boolean signalized = source.hasTag("highway", "traffic_signals");
            int count = majorWays.getOrDefault(source.id(), 0);
            features.point("busnav_named_intersections").setMinZoom(13)
                .setAttr("name", RoadMapDetails.name(source.tags()))
                .setAttr("signalized", signalized).setAttr("major_way_count", count)
                .setAttr("rank", RoadMapDetails.intersectionRank(count, signalized));
        }
    }

    @Override public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
        List<OsmRelationInfo> original = super.preprocessOsmRelation(relation);
        var result = new ArrayList<OsmRelationInfo>(original == null ? List.of() : original);
        if (relation.hasTag("type", "route") && relation.hasTag("route", "road") &&
            JapaneseRoadNetwork.networkKind(relation.getString("network")) != JapaneseRoadNetwork.Kind.OTHER)
        {
            var road = new RoadRelation(relation.id(), relation.getString("network"), relation.getString("ref"), relation.getString("operator"));
            roads.put(relation.id(), road);
            // Planetiler indexes one info per relation ID. Keep OMT info intact.
            if (result.isEmpty()) result.add(road);
        }
        return result;
    }

    @Override public void processFeature(SourceFeature source, FeatureCollector features) {
        super.processFeature(source, features);
        if (!OSM_SOURCE.equals(source.getSource())) return;
        if (source.isPoint()) { processPoint(source, features); return; }
        if (!source.canBeLine() || !source.hasTag("highway")) return;
        var memberships = source.relationInfo(OsmRelationInfo.class).stream().map(m -> roads.get(m.relation().id()))
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparingLong(RoadRelation::id)).toList();
        var candidates = memberships.stream().map(r -> JapaneseRoadNetwork.fromNetwork(r.network(), r.ref())).toList();
        var route = JapaneseRoadNetwork.classify(candidates, source.getString("network"), null,
            source.getString("highway"), source.getString("ref"));
        if (route.kind == JapaneseRoadNetwork.Kind.OTHER) return;
        for (var feature : features) {
            if (feature.isLine() && (feature.getLayer().equals("transportation") ||
                                    feature.getLayer().equals("transportation_name"))) {
                feature.setAttr("route_network", route.tileClass());
                memberships.stream().filter(r -> JapaneseRoadNetwork.networkKind(r.network()) == route.kind)
                    .findFirst().ifPresent(r -> {
                        feature.setAttr("route_source_network", r.network());
                        if (r.operator() != null) feature.setAttr("route_operator", r.operator());
                    });
                if (route.ref != null) feature.setAttr("route_ref", route.ref);
            }
        }
    }

    public static void main(String[] args) {
        var runner = Planetiler.create(Arguments.fromArgsOrConfigFile(args));
        Path sources = Path.of("/data/sources");
        runner.setDefaultLanguages(OpenMapTilesSchema.LANGUAGES)
            .fetchWikidataNameTranslations(sources.resolve("wikidata_names.json"))
            .setProfile(BusNavProfile::new)
            .addShapefileSource("EPSG:3857", LAKE_CENTERLINE_SOURCE, sources.resolve("lake_centerline.shp.zip"),
                "https://dev.maptiler.download/geodata/omt/lake_centerline.shp.zip")
            .addShapefileSource(WATER_POLYGON_SOURCE, sources.resolve("water-polygons-split-3857.zip"),
                "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip")
            .addNaturalEarthSource(NATURAL_EARTH_SOURCE, sources.resolve("natural_earth_vector.sqlite.zip"),
                "https://dev.maptiler.download/geodata/omt/natural_earth_vector.sqlite.zip")
            .addOsmSource(OSM_SOURCE, Path.of("/input/region.osm.pbf"))
            .setOutput("mbtiles", Path.of("/data/output.mbtiles")).run();
    }
}
