package HyProTechTeam.config;

/** Normalizes per-tier configuration arrays to a fixed length with safe defaults. */
public final class ConfigArrays {
    private ConfigArrays() {
    }

    /** Copies defaults first, then replaces entries with nonblank trimmed strings. */
    public static String[] mergeStringArray(String[] value, String[] defaults, int expectedLength) {
        String[] result = new String[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            String fallback = defaults != null && i < defaults.length ? defaults[i] : "";
            result[i] = fallback == null ? "" : fallback;
        }
        // Ignore missing and extra entries; null or blank values leave the fallback in place.
        if (value != null) {
            int limit = Math.min(value.length, expectedLength);
            for (int i = 0; i < limit; i++) {
                String item = value[i];
                if (item == null) {
                    continue;
                }
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    result[i] = trimmed;
                }
            }
        }
        return result;
    }

    /** Merges integer entries and clamps both defaults and overrides to the minimum. */
    public static int[] mergeIntArray(int[] value, int[] defaults, int expectedLength, int minValue) {
        int[] result = new int[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            int fallback = defaults != null && i < defaults.length ? defaults[i] : minValue;
            result[i] = Math.max(minValue, fallback);
        }
        // Supplied entries replace the corresponding fallback, up to the expected length.
        if (value != null) {
            int limit = Math.min(value.length, expectedLength);
            for (int i = 0; i < limit; i++) {
                int v = value[i];
                result[i] = v < minValue ? minValue : v;
            }
        }
        return result;
    }

    /** Merges floating-point entries, replacing non-finite or too-small values with the minimum. */
    public static double[] mergeDoubleArray(double[] value, double[] defaults, int expectedLength, double minValue) {
        double[] result = new double[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            double fallback = defaults != null && i < defaults.length ? defaults[i] : minValue;
            if (!Double.isFinite(fallback) || fallback < minValue) {
                fallback = minValue;
            }
            result[i] = fallback;
        }
        // Apply the same finite-value and minimum checks to user-provided entries.
        if (value != null) {
            int limit = Math.min(value.length, expectedLength);
            for (int i = 0; i < limit; i++) {
                double v = value[i];
                if (!Double.isFinite(v) || v < minValue) {
                    v = minValue;
                }
                result[i] = v;
            }
        }
        return result;
    }
}
