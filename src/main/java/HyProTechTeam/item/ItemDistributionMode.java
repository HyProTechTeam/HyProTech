package HyProTechTeam.item;

/** Selects how a cable network chooses among eligible destinations. */
public enum ItemDistributionMode {
    ROUND_ROBIN("Round Robin"),
    NEAREST("Nearest"),
    FURTHEST("Furthest");

    private final String label;

    /** Stores the distribution mode's display label. */
    ItemDistributionMode(String label) {
        this.label = label;
    }

    /** Returns the mode's display label. */
    public String label() {
        return label;
    }

    /** Cycles Round Robin → Nearest → Furthest → Round Robin. */
    public ItemDistributionMode next() {
        switch (this) {
            case ROUND_ROBIN:
                return NEAREST;
            case NEAREST:
                return FURTHEST;
            case FURTHEST:
            default:
                return ROUND_ROBIN;
        }
    }
}
