package net.nobu0707.busnav.tiles;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/** Exercises real OMT generation, especially preserving its existing relation fields. */
public final class BusNavProfileTest {
    public static void main(String[] args) {
        var runner = Planetiler.create(Arguments.fromArgs("--languages=ja"));
        var profile = new BusNavProfile(runner);
        var relation = new OsmElement.Relation(1, Map.of("type", "route", "route", "road",
            "network", "JP:national", "ref", "246"), List.of());
        var infos = profile.preprocessOsmRelation(relation);
        check(infos.size() == 1, "Do not overwrite OMT info under the same relation ID");
        var members = infos.stream().map(i -> new OsmReader.RelationMember<OsmRelationInfo>("", i)).toList();
        var geometry = new GeometryFactory().createLineString(new Coordinate[] {
            new Coordinate(139.69, 35.65), new Coordinate(139.71, 35.66)});
        var source = SimpleFeature.createFakeOsmFeature(geometry,
            Map.of("highway", "trunk", "ref", "246", "name", "玉川通り"), "osm", null, 7, members);
        var features = new FeatureCollector.Factory(runner.config(), runner.stats()).get(source);
        profile.processFeature(source, features);
        int count = 0;
        for (var feature : features) {
            var attrs = feature.getAttrsAtZoom(14);
            if (feature.getLayer().equals("transportation") || feature.getLayer().equals("transportation_name")) {
                check("national".equals(attrs.get("route_network")), "network attribute missing");
                check("246".equals(attrs.get("route_ref")), "ref attribute missing");
                if (feature.getLayer().equals("transportation_name"))
                    check("JP:national".equals(attrs.get("route_1_network")), "OMT relation network was lost");
                count++;
            }
        }
        check(count == 2, "both existing layers must be enriched");
        var point = new GeometryFactory().createPoint(new Coordinate(139.741, 35.677));
        var junction = SimpleFeature.createFakeOsmFeature(point,
            Map.of("highway", "motorway_junction", "name", "三宅坂JCT"), "osm", null, 8, List.of());
        var junctionFeatures = new FeatureCollector.Factory(runner.config(), runner.stats()).get(junction);
        profile.processFeature(junction, junctionFeatures);
        boolean found = false;
        for (var f : junctionFeatures) if (f.getLayer().equals("busnav_expressway_facilities")) {
            found = true;
            check("junction".equals(f.getAttrsAtZoom(14).get("facility_type")), "JCT source designation");
        }
        check(found, "custom point layer missing");
        var urbanInfo = profile.preprocessOsmRelation(new OsmElement.Relation(2, Map.of("type", "route", "route", "road",
            "network", "首都高速道路", "ref", "C2"), List.of()));
        var urban = SimpleFeature.createFakeOsmFeature(geometry, Map.of("highway", "motorway", "ref", "C2"), "osm", null, 9,
            urbanInfo.stream().map(i -> new OsmReader.RelationMember<OsmRelationInfo>("", i)).toList());
        var urbanFeatures = new FeatureCollector.Factory(runner.config(), runner.stats()).get(urban);
        profile.processFeature(urban, urbanFeatures);
        for (var f : urbanFeatures) if (f.getLayer().startsWith("transportation")) {
            check("urban_expressway".equals(f.getAttrsAtZoom(14).get("route_network")), "urban classification");
            check("C2".equals(f.getAttrsAtZoom(14).get("route_ref")), "urban ref");
        }
        System.out.println("BusNavProfileTest PASS: both layers enriched; OMT route relation retained");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
