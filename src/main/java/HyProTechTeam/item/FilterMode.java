package HyProTechTeam.item;

/** Selects whether listed item IDs are allowed or blocked. */
public enum FilterMode {
    WHITELIST("Whitelist"),
    BLACKLIST("Blacklist");

    private final String label;

    /** Stores the display label. */
    FilterMode(String label) {
        this.label = label;
    }

    /** Returns the filter's display label. */
    public String label() {
        return label;
    }

    /** Switches between whitelist and blacklist modes. */
    public FilterMode next() {
        return this == WHITELIST ? BLACKLIST : WHITELIST;
    }

    /** Parses a trimmed name without regard to case, or returns null for an unknown value. */
    public static FilterMode fromName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("whitelist".equalsIgnoreCase(trimmed)) {
            return WHITELIST;
        }
        if ("blacklist".equalsIgnoreCase(trimmed)) {
            return BLACKLIST;
        }
        return null;
    }
}
