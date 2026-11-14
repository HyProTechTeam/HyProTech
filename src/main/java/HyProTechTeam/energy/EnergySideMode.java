package HyProTechTeam.energy;

/** Input/output policy for one energy node face. */
public enum EnergySideMode {
    DISABLED("Off"),
    INPUT("In"),
    OUTPUT("Out"),
    BOTH("In/Out");

    private final String label;

    /** Stores the short label shown for this mode. */
    EnergySideMode(String label) {
        this.label = label;
    }

    /** Returns the mode's display label. */
    public String label() {
        return label;
    }

    /** Cycles Off → In → Out → In/Out → Off. */
    public EnergySideMode next() {
        switch (this) {
            case DISABLED:
                return INPUT;
            case INPUT:
                return OUTPUT;
            case OUTPUT:
                return BOTH;
            case BOTH:
            default:
                return DISABLED;
        }
    }

    /** Combines independent input and output permissions into a mode. */
    public static EnergySideMode fromFlags(boolean input, boolean output) {
        if (input && output) {
            return BOTH;
        }
        if (input) {
            return INPUT;
        }
        if (output) {
            return OUTPUT;
        }
        return DISABLED;
    }
}
