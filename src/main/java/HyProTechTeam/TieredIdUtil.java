package HyProTechTeam;

/** Recognizes and builds tiered block IDs while preserving optional namespaces. */
public final class TieredIdUtil {
    private TieredIdUtil() {
    }

    /** Removes a namespace only if the remaining ID begins with the expected base. */
    public static String stripNamespace(String blockId, String baseId) {
        if (blockId == null || baseId == null) {
            return blockId;
        }
        if (blockId.regionMatches(true, 0, baseId, 0, baseId.length())) {
            return blockId;
        }
        int colonIndex = blockId.indexOf(':');
        if (colonIndex > 0 && colonIndex + 1 < blockId.length()) {
            String trimmed = blockId.substring(colonIndex + 1);
            if (trimmed.regionMatches(true, 0, baseId, 0, baseId.length())) {
                return trimmed;
            }
        }
        return blockId;
    }

    /** Copies the original namespace to an unqualified replacement ID, if present. */
    public static String applyNamespace(String blockId, String baseId, String targetId) {
        if (blockId == null || baseId == null || targetId == null) {
            return targetId;
        }
        if (targetId.indexOf(':') >= 0) {
            return targetId;
        }
        int colonIndex = blockId.indexOf(':');
        if (colonIndex > 0 && colonIndex + 1 < blockId.length()) {
            return blockId.substring(0, colonIndex + 1) + targetId;
        }
        if (blockId.regionMatches(true, 0, baseId, 0, baseId.length())) {
            return targetId;
        }
        return targetId;
    }

    /** Accepts the base ID, a state-suffixed base, or a numeric tier with a boundary. */
    public static boolean isTieredId(String blockId, String baseId) {
        if (blockId == null || baseId == null) {
            return false;
        }
        String normalized = stripNamespace(blockId, baseId);
        if (normalized == null) {
            return false;
        }
        if (normalized.equalsIgnoreCase(baseId)) {
            return true;
        }
        if (isStateSuffixedBaseId(normalized, baseId)) {
            return true;
        }
        String prefix = baseId + "_T";
        if (!normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        if (normalized.length() == prefix.length()) {
            return false;
        }
        // Require at least one digit and reject a tier followed directly by a letter or digit.
        int i = prefix.length();
        boolean foundDigit = false;
        for (; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            foundDigit = true;
        }
        if (!foundDigit) {
            return false;
        }
        return i == normalized.length() || !Character.isLetterOrDigit(normalized.charAt(i));
    }

    /** Parses the numeric tier after _T; returns -1 when no valid tier is present. */
    public static int parseTierSuffix(String blockId, String baseId) {
        if (blockId == null || baseId == null) {
            return -1;
        }
        String normalized = stripNamespace(blockId, baseId);
        if (normalized == null) {
            return -1;
        }
        String prefix = baseId + "_T";
        if (!normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return -1;
        }
        if (normalized.length() == prefix.length()) {
            return -1;
        }
        int tier = 0;
        int i = prefix.length();
        boolean foundDigit = false;
        for (; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            tier = (tier * 10) + (c - '0');
            foundDigit = true;
        }
        if (!foundDigit) {
            return -1;
        }
        if (i < normalized.length() && Character.isLetterOrDigit(normalized.charAt(i))) {
            return -1;
        }
        return tier;
    }

    /** Uses the base ID for tier zero or below, and appends _T followed by higher tiers. */
    public static String buildTieredId(String baseId, int tier) {
        if (baseId == null) {
            return null;
        }
        if (tier <= 0) {
            return baseId;
        }
        return baseId + "_T" + tier;
    }

    /** A non-alphanumeric character after the base separates a block state suffix. */
    private static boolean isStateSuffixedBaseId(String normalized, String baseId) {
        if (normalized == null || baseId == null) {
            return false;
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
}
