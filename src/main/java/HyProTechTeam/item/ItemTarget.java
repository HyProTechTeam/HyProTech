package HyProTechTeam.item;

/** Selects the intended inventory target for an item cable. */
public enum ItemTarget {
    AUTO("Auto"),
    INPUT("Input"),
    FUEL("Fuel"),
    OUTPUT("Output");

    private final String label;

    /** Stores the target's display label. */
    ItemTarget(String label) {
        this.label = label;
    }

    /** Returns the target's display label. */
    public String label() {
        return label;
    }

    /** Cycles Auto → Input → Fuel → Output → Auto. */
    public ItemTarget next() {
        switch (this) {
            case AUTO:
                return INPUT;
            case INPUT:
                return FUEL;
            case FUEL:
                return OUTPUT;
            case OUTPUT:
            default:
                return AUTO;
        }
    }
}
