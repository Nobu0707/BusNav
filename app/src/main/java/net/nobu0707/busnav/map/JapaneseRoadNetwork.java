package net.nobu0707.busnav.map;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared by Android and the pinned Planetiler extension; never infers national roads from highway class. */
public final class JapaneseRoadNetwork {
    private JapaneseRoadNetwork() {}
    public enum Kind { URBAN_EXPRESSWAY, EXPRESSWAY, NATIONAL_ROUTE, PREFECTURAL_ROUTE, OTHER }
    public static final class Route {
        public final Kind kind;
        public final String ref;
        public Route(Kind kind, String ref) { this.kind = kind; this.ref = ref; }
        public String tileClass() {
            return switch (kind) {
                case EXPRESSWAY -> "expressway";
                case URBAN_EXPRESSWAY -> "urban_expressway";
                case NATIONAL_ROUTE -> "national";
                case PREFECTURAL_ROUTE -> "prefectural";
                case OTHER -> "other";
            };
        }
    }
    private static final Pattern EXPRESS = Pattern.compile("[EC]\\s*([1-9][0-9]{0,2})([A-Z]?)");
    private static final Pattern NUMBER = Pattern.compile("[1-9][0-9]{0,2}");
    private static final Pattern NATIONAL = Pattern.compile("国道\\s*([1-9][0-9]{0,2})\\s*号?");
    private static final Pattern PREFECTURAL = Pattern.compile("(?:都道|道道|府道|県道)\\s*([1-9][0-9]{0,2})\\s*号?");

    public static Kind networkKind(String network) {
        if (network == null) return Kind.OTHER;
        if (network.equals("首都高速道路") || network.equals("名古屋高速道路"))
            return Kind.URBAN_EXPRESSWAY;
        if (network.equals("JP:E") || network.equals("JP:C"))
            return Kind.EXPRESSWAY;
        if (network.equals("JP:national")) return Kind.NATIONAL_ROUTE;
        if (network.equals("JP:prefectural") || network.matches("JP:prefectural:[a-z]+"))
            return Kind.PREFECTURAL_ROUTE;
        return Kind.OTHER;
    }

    /** Only the first semicolon-delimited token is canonical; an invalid first token is not skipped. */
    public static String normalize(String raw, Kind kind) {
        if (raw == null || kind == Kind.OTHER) return null;
        String first = raw.split(";", -1)[0].trim();
        if (kind == Kind.URBAN_EXPRESSWAY) {
            String upper = first.toUpperCase(Locale.ROOT).replace(" ", "");
            return upper.matches("(?:C[12]|[1-9][0-9]?|B|Y|[KS][1-9][0-9]?|R)") ? upper : null;
        }
        if (kind == Kind.EXPRESSWAY) {
            String upper = first.toUpperCase(Locale.ROOT);
            Matcher m = EXPRESS.matcher(upper);
            if (m.matches()) return upper.substring(0, 1) + m.group(1) + m.group(2);
            return null;
        }
        if (NUMBER.matcher(first).matches()) return first;
        Matcher m = (kind == Kind.NATIONAL_ROUTE ? NATIONAL : PREFECTURAL).matcher(first);
        return m.matches() ? m.group(1) : null;
    }

    public static Route fromNetwork(String network, String ref) {
        Kind kind = networkKind(network);
        return new Route(kind, normalize(ref, kind));
    }

    /** Caller orders relation candidates deterministically. Membership is evidence even without a usable ref. */
    public static Route classify(List<Route> relations, String network, String providerClass,
                                 String highway, String ref) {
        Route best = null;
        for (Route candidate : relations) {
            if (candidate.kind != Kind.OTHER && (best == null || candidate.kind.ordinal() < best.kind.ordinal() ||
                (candidate.kind == best.kind && best.ref == null && candidate.ref != null)))
                best = candidate;
        }
        Route direct = fromNetwork(network, ref);
        if (direct.kind != Kind.OTHER && (best == null || direct.kind.ordinal() < best.kind.ordinal())) best = direct;
        if (best != null) return best;
        Kind generated = switch (providerClass == null ? "" : providerClass) {
            case "expressway" -> Kind.EXPRESSWAY;
            case "urban_expressway" -> Kind.URBAN_EXPRESSWAY;
            case "national" -> Kind.NATIONAL_ROUTE;
            case "prefectural" -> Kind.PREFECTURAL_ROUTE;
            default -> Kind.OTHER;
        };
        if (generated != Kind.OTHER) return new Route(generated, normalize(ref, generated));
        // Audited Japanese regional inputs: motorway + an exact E/C token is safe; numeric-only is not.
        String normalized = normalize(ref, Kind.EXPRESSWAY);
        if (("motorway".equals(highway) || "motorway_link".equals(highway)) && normalized != null &&
            !normalized.equals("C1") && !normalized.equals("C2") && EXPRESS.matcher(normalized).matches()) return new Route(Kind.EXPRESSWAY, normalized);
        return new Route(Kind.OTHER, null);
    }
}
