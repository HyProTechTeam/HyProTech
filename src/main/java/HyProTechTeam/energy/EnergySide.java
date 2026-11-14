package HyProTechTeam.energy;

/** Six block faces, with offsets and bits for connection and direction masks. */
public enum EnergySide {
    EAST(1, 0, 0, "East"),
    WEST(-1, 0, 0, "West"),
    UP(0, 1, 0, "Up"),
    DOWN(0, -1, 0, "Down"),
    SOUTH(0, 0, 1, "South"),
    NORTH(0, 0, -1, "North");

    // The mask uses one bit per enum value; ALL_MASK enables every face.
    public static final EnergySide[] VALUES = values();
    public static final int ALL_MASK = (1 << VALUES.length) - 1;

    private final int dx;
    private final int dy;
    private final int dz;
    private final String label;
    private final int mask;

    /** Associates each face with its neighboring block offset, UI label, and mask bit. */
    EnergySide(int dx, int dy, int dz, String label) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.label = label;
        this.mask = 1 << ordinal();
    }

    /** Returns the X offset toward the neighbor on this face. */
    public int dx() {
        return dx;
    }

    /** Returns the Y offset toward the neighbor on this face. */
    public int dy() {
        return dy;
    }

    /** Returns the Z offset toward the neighbor on this face. */
    public int dz() {
        return dz;
    }

    /** Returns the bit representing this face in a side mask. */
    public int mask() {
        return mask;
    }

    /** Returns the display name for this face. */
    public String label() {
        return label;
    }

    /** Returns the neighboring block's face that points back toward this block. */
    public EnergySide opposite() {
        switch (this) {
            case EAST:
                return WEST;
            case WEST:
                return EAST;
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case SOUTH:
                return NORTH;
            case NORTH:
                return SOUTH;
            default:
                return this;
        }
    }

    /** Finds a face by enum name or display label, ignoring case; returns null if unknown. */
    public static EnergySide fromName(String name) {
        if (name == null) {
            return null;
        }
        for (EnergySide side : VALUES) {
            if (side.name().equalsIgnoreCase(name) || side.label.equalsIgnoreCase(name)) {
                return side;
            }
        }
        return null;
    }

    /** Finds the face with the exact unit offset, or null if the offset is not a face. */
    public static EnergySide fromDelta(int dx, int dy, int dz) {
        for (EnergySide side : VALUES) {
            if (side.dx == dx && side.dy == dy && side.dz == dz) {
                return side;
            }
        }
        return null;
    }
}
