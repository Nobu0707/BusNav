package net.nobu0707.busnav.map;

import java.util.Map;
import java.util.Locale;

/** Uses explicit node tags, including literal facility designations in source names. */
public final class RoadMapDetails {
    private RoadMapDetails() {}
    public enum FacilityType { INTERCHANGE, ENTRANCE, EXIT, JUNCTION, TOLL_GATE, MAINLINE_TOLL_GATE, UNKNOWN }
    public static String name(Map<String, Object> tags) {
        Object ja = tags.get("name:ja");
        Object name = ja == null ? tags.get("name") : ja;
        return name == null ? "" : name.toString().trim();
    }
    public static FacilityType facility(Map<String, Object> tags, boolean onMotorway) {
        String name = name(tags);
        if ("toll_booth".equals(tags.get("barrier"))) {
            if (!onMotorway) return FacilityType.UNKNOWN;
            return "mainline".equals(tags.get("toll")) || name.contains("本線料金所")
                ? FacilityType.MAINLINE_TOLL_GATE : FacilityType.TOLL_GATE;
        }
        if (!"motorway_junction".equals(tags.get("highway"))) return FacilityType.UNKNOWN;
        if ("yes".equals(tags.get("junction")) || name.toUpperCase(Locale.ROOT).endsWith("JCT") ||
            name.endsWith("ジャンクション")) return FacilityType.JUNCTION;
        if (name.endsWith("入口") && !name.endsWith("出入口")) return FacilityType.ENTRANCE;
        if (name.endsWith("出口")) return FacilityType.EXIT;
        return FacilityType.INTERCHANGE;
    }
    public static boolean namedIntersection(Map<String, Object> tags) {
        return !name(tags).isEmpty() && ("traffic_signals".equals(tags.get("highway")) ||
            ("yes".equals(tags.get("junction")) && !"motorway_junction".equals(tags.get("highway"))));
    }
    /** Priority only; names and facility types are never synthesized from nearby roads. */
    public static int intersectionRank(int majorWayCount, boolean signalized) {
        return majorWayCount >= 2 && signalized ? 0 : 1;
    }
}
