package HyProTechTeam.item;

/** Controls whether an item cable may take items, put items, both, or neither. */
public enum ItemMode {
    OFF("Off"),
    TAKE("Take"),
    PUT("Put"),
    BOTH("Take/Put");

    private final String label;

    /** Stores the short display label. */
    ItemMode(String label) {
        this.label = label;
    }

    /** Returns the mode's display label. */
    public String label() {
        return label;
    }

    /** Cycles Off → Take → Put → Take/Put → Off. */
    public ItemMode next() {
        switch (this) {
            case OFF:
                return TAKE;
            case TAKE:
                return PUT;
            case PUT:
                return BOTH;
            case BOTH:
            default:
                return OFF;
        }
    }

    /** Reports whether the mode permits extraction. */
    public boolean allowsTake() {
        return this == TAKE || this == BOTH;
    }

    /** Reports whether the mode permits insertion. */
    public boolean allowsPut() {
        return this == PUT || this == BOTH;
    }
}
