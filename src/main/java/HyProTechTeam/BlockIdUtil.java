package HyProTechTeam;

public final class BlockIdUtil {
    private BlockIdUtil() {
    }

    public static boolean isIdOrState(String blockId, String baseId) {
        if (blockId == null || baseId == null) {
            return false;
        }
        String normalized = TieredIdUtil.stripNamespace(blockId, baseId);
        if (normalized == null) {
            return false;
        }
        if (normalized.equalsIgnoreCase(baseId)) {
            return true;
        }
        if (!normalized.regionMatches(true, 0, baseId, 0, baseId.length())) {
            return false;
        }
        if (normalized.length() == baseId.length()) {
            return false;
        }
        char separator = normalized.charAt(baseId.length());
        return !Character.isLetterOrDigit(separator);
    }

    public static int parseCableTierFromIdOrState(String blockId, String baseId) {
        if (blockId == null || baseId == null) {
            return -1;
        }
        String normalized = TieredIdUtil.stripNamespace(blockId, baseId);
        int tier = TieredIdUtil.parseTierSuffix(normalized, baseId);
        if (tier < 0) {
            tier = parseTierSuffix(normalized, baseId, "_S");
        }
        if (tier >= 0) {
            return tier;
        }
        if (normalized == null || !normalized.regionMatches(true, 0, baseId, 0, baseId.length())) {
            return -1;
        }

        int stateIndex = normalized.indexOf("Cable_T");
        if (stateIndex < 0) {
            return -1;
        }

        int i = stateIndex + "Cable_T".length();
        int value = 0;
        boolean found = false;
        while (i < normalized.length()) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = (value * 10) + (c - '0');
            found = true;
            i++;
        }
        return found ? value : -1;
    }

    private static int parseTierSuffix(String normalized, String baseId, String marker) {
        if (normalized == null || baseId == null || marker == null || marker.isEmpty()) {
            return -1;
        }
        String prefix = baseId + marker;
        if (!normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return -1;
        }
        if (normalized.length() == prefix.length()) {
            return -1;
        }
        int value = 0;
        int i = prefix.length();
        boolean foundDigit = false;
        for (; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            foundDigit = true;
            value = (value * 10) + (c - '0');
        }
        if (!foundDigit) {
            return -1;
        }
        if (i < normalized.length() && Character.isLetterOrDigit(normalized.charAt(i))) {
            return -1;
        }
        return value;
    }
}
