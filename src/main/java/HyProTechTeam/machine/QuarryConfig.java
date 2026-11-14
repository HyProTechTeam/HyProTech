package HyProTechTeam.machine;

import HyProTechTeam.config.ConfigArrays;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.array.DoubleArrayCodec;
import com.hypixel.hytale.codec.codecs.array.IntArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.DoubleCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import java.util.ArrayList;
import java.util.List;

/** Quarry tier configuration, mining timing, area bounds, and upgrade costs. */
public final class QuarryConfig {
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 5;
    public static int BASE_AREA = 5;
    public static int MIN_AREA = 2;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TIER_COUNT = MAX_TIER - MIN_TIER + 1;

    private static final StringCodec STRING_CODEC = new StringCodec();
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final DoubleCodec DOUBLE_CODEC = new DoubleCodec();
    private static final BooleanCodec BOOLEAN_CODEC = new BooleanCodec();
    private static final IntArrayCodec INT_ARRAY_CODEC = new IntArrayCodec();
    private static final DoubleArrayCodec DOUBLE_ARRAY_CODEC = new DoubleArrayCodec();
    private static final ArrayCodec<String> STRING_ARRAY_CODEC =
            new ArrayCodec<>(STRING_CODEC, String[]::new);

    private static final int DEFAULT_BASE_AREA = 5;
    private static final int DEFAULT_MIN_AREA = 2;
    private static final double DEFAULT_BASIC_SPEED_SECONDS = 0.7;
    private static final double DEFAULT_QUANTUM_SPEED_SECONDS = 0.1;
    private static final int DEFAULT_BACKFILL_PLACE_DELAY_TICKS = 2;
    private static final int DEFAULT_BACKFILL_POST_DELAY_TICKS = 2;
    private static final boolean DEFAULT_FORCE_REPLACE_BLOCKS = false;

    private static final String[] DEFAULT_TIER_NAMES = {
            "Basic",
            "Reinforced",
            "Industrial",
            "Advanced",
            "Precision",
            "Quantum"
    };

    /** One item and quantity required to upgrade the quarry. */
    public static final class Requirement {
        private String itemId;
        private int quantity;

        public static final BuilderCodec<Requirement> CODEC =
                BuilderCodec.builder(Requirement.class, Requirement::new)
                        .addField(new KeyedCodec<>("ItemId", STRING_CODEC),
                                (requirement, value) -> requirement.itemId = normalizeItemId(value),
                                requirement -> requirement.itemId)
                        .addField(new KeyedCodec<>("Quantity", INTEGER_CODEC),
                                (requirement, value) -> requirement.quantity = normalizeQuantity(value),
                                requirement -> requirement.quantity)
                        .build();

        /** Creates an empty requirement for decoding. */
        public Requirement() {
            this("", 0);
        }

        /** Normalizes the item ID and quantity. */
        public Requirement(String itemId, int quantity) {
            this.itemId = normalizeItemId(itemId);
            this.quantity = normalizeQuantity(quantity);
        }

        /** Returns the required item ID. */
        public String getItemId() {
            return itemId;
        }

        /** Returns the required item count. */
        public int getQuantity() {
            return quantity;
        }
    }

    private static final Requirement[] NO_REQUIREMENTS = new Requirement[0];

    private static final int[] DEFAULT_CAPACITY = {
            5000,
            50000,
            100000,
            200000,
            600000,
            1000000
    };

    private static final int[] DEFAULT_CONSUMPTION_PER_SECOND = {
            2000,
            4000,
            7900,
            15500,
            30500,
            60000
    };

    private static final int[] DEFAULT_MAX_AREA = {
            5,
            10,
            20,
            30,
            40,
            50
    };

    private static final Requirement[][] DEFAULT_UPGRADE_REQUIREMENTS = {
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Copper", 1),
                    new Requirement("Ingredient_Hide_Medium", 1)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Iron", 1),
                    new Requirement("Ingredient_Fire_Essence", 1)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Silver", 1),
                    new Requirement("Ingredient_Bar_Cobalt", 1)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Gold", 1),
                    new Requirement("Ingredient_Bar_Thorium", 1),
                    new Requirement("Ingredient_Fire_Essence", 1)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Adamantite", 1),
                    new Requirement("Ingredient_Bar_Cobalt", 1),
                    new Requirement("Ingredient_Hide_Medium", 1)
            },
            new Requirement[0]
    };

    private static final ArrayCodec<Requirement> REQUIREMENT_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(Requirement.CODEC, Requirement[]::new);
    private static final ArrayCodec<Requirement[]> REQUIREMENT_TABLE_CODEC =
            new ArrayCodec<>(REQUIREMENT_ARRAY_CODEC, Requirement[][]::new);

    private static String[] tierNames = DEFAULT_TIER_NAMES.clone();
    private static int[] capacity = DEFAULT_CAPACITY.clone();
    private static int[] consumptionPerSecond = DEFAULT_CONSUMPTION_PER_SECOND.clone();
    private static int[] maxArea = DEFAULT_MAX_AREA.clone();
    private static Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);
    private static double basicSpeedSeconds = DEFAULT_BASIC_SPEED_SECONDS;
    private static double quantumSpeedSeconds = DEFAULT_QUANTUM_SPEED_SECONDS;
    private static int backfillPlaceDelayTicks = DEFAULT_BACKFILL_PLACE_DELAY_TICKS;
    private static int backfillPostDelayTicks = DEFAULT_BACKFILL_POST_DELAY_TICKS;
    private static boolean forceReplaceBlocks = DEFAULT_FORCE_REPLACE_BLOCKS;

    /** Codec-backed quarry settings initialized from defaults. */
    public static final class ConfigData {
        public static final BuilderCodec<ConfigData> CODEC =
                BuilderCodec.builder(ConfigData.class, ConfigData::new)
                        .addField(new KeyedCodec<>("BaseArea", INTEGER_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.baseArea = value;
                                    }
                                },
                                config -> config.baseArea)
                        .addField(new KeyedCodec<>("MinArea", INTEGER_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.minArea = value;
                                    }
                                },
                                config -> config.minArea)
                        .addField(new KeyedCodec<>("BasicSpeedSeconds", DOUBLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.basicSpeedSeconds = value;
                                    }
                                },
                                config -> config.basicSpeedSeconds)
                        .addField(new KeyedCodec<>("QuantumSpeedSeconds", DOUBLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.quantumSpeedSeconds = value;
                                    }
                                },
                                config -> config.quantumSpeedSeconds)
                        .addField(new KeyedCodec<>("BackfillPlaceDelayTicks", INTEGER_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.backfillPlaceDelayTicks = value;
                                    }
                                },
                                config -> config.backfillPlaceDelayTicks)
                        .addField(new KeyedCodec<>("BackfillPostDelayTicks", INTEGER_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.backfillPostDelayTicks = value;
                                    }
                                },
                                config -> config.backfillPostDelayTicks)
                        .addField(new KeyedCodec<>("TierNames", STRING_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.tierNames = value;
                                    }
                                },
                                config -> config.tierNames)
                        .addField(new KeyedCodec<>("Capacity", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.capacity = value;
                                    }
                                },
                                config -> config.capacity)
                        .addField(new KeyedCodec<>("ConsumptionPerSecond", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.consumptionPerSecond = value;
                                    }
                                },
                                config -> config.consumptionPerSecond)
                        .addField(new KeyedCodec<>("MaxArea", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.maxArea = value;
                                    }
                                },
                                config -> config.maxArea)
                        .addField(new KeyedCodec<>("UpgradeRequirements", REQUIREMENT_TABLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.upgradeRequirements = value;
                                    }
                                },
                                config -> config.upgradeRequirements)
                        .addField(new KeyedCodec<>("ForceReplaceBlocks", BOOLEAN_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.forceReplaceBlocks = value;
                                    }
                                },
                                config -> config.forceReplaceBlocks)
                        .build();

        private int baseArea = DEFAULT_BASE_AREA;
        private int minArea = DEFAULT_MIN_AREA;
        private double basicSpeedSeconds = DEFAULT_BASIC_SPEED_SECONDS;
        private double quantumSpeedSeconds = DEFAULT_QUANTUM_SPEED_SECONDS;
        private int backfillPlaceDelayTicks = DEFAULT_BACKFILL_PLACE_DELAY_TICKS;
        private int backfillPostDelayTicks = DEFAULT_BACKFILL_POST_DELAY_TICKS;
        private String[] tierNames = DEFAULT_TIER_NAMES.clone();
        private int[] capacity = DEFAULT_CAPACITY.clone();
        private int[] consumptionPerSecond = DEFAULT_CONSUMPTION_PER_SECOND.clone();
        private int[] maxArea = DEFAULT_MAX_AREA.clone();
        private Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);
        private boolean forceReplaceBlocks = DEFAULT_FORCE_REPLACE_BLOCKS;

        public ConfigData() {
        }
    }

    private QuarryConfig() {
    }

    /** Validates dimensions, timing, per-tier values, upgrade costs, and replacement settings. */
    public static void applyConfig(ConfigData data) {
        if (data == null) {
            return;
        }
        int baseAreaValue = Math.max(1, data.baseArea);
        int minAreaValue = Math.max(1, data.minArea);
        if (baseAreaValue < minAreaValue) {
            baseAreaValue = minAreaValue;
        }
        BASE_AREA = baseAreaValue;
        MIN_AREA = minAreaValue;

        basicSpeedSeconds = normalizeSpeedSeconds(data.basicSpeedSeconds, DEFAULT_BASIC_SPEED_SECONDS);
        quantumSpeedSeconds = normalizeSpeedSeconds(data.quantumSpeedSeconds, DEFAULT_QUANTUM_SPEED_SECONDS);
        backfillPlaceDelayTicks =
                normalizeDelayTicks(data.backfillPlaceDelayTicks, DEFAULT_BACKFILL_PLACE_DELAY_TICKS);
        backfillPostDelayTicks =
                normalizeDelayTicks(data.backfillPostDelayTicks, DEFAULT_BACKFILL_POST_DELAY_TICKS);

        tierNames = ConfigArrays.mergeStringArray(data.tierNames, DEFAULT_TIER_NAMES, TIER_COUNT);
        capacity = ConfigArrays.mergeIntArray(data.capacity, DEFAULT_CAPACITY, TIER_COUNT, 0);
        consumptionPerSecond = ConfigArrays.mergeIntArray(
                data.consumptionPerSecond,
                DEFAULT_CONSUMPTION_PER_SECOND,
                TIER_COUNT,
                0);
        maxArea = ConfigArrays.mergeIntArray(data.maxArea, DEFAULT_MAX_AREA, TIER_COUNT, MIN_AREA);
        clampMaxArea();
        upgradeRequirements = mergeRequirements(data.upgradeRequirements, DEFAULT_UPGRADE_REQUIREMENTS);
        forceReplaceBlocks = data.forceReplaceBlocks;
    }

    /** Restricts a tier to the supported range. */
    public static int clampTier(int tier) {
        if (tier < MIN_TIER) {
            return MIN_TIER;
        }
        if (tier > MAX_TIER) {
            return MAX_TIER;
        }
        return tier;
    }

    /** Reports whether the clamped tier has an upgrade. */
    public static boolean hasNextTier(int tier) {
        return clampTier(tier) < MAX_TIER;
    }

    /** Returns the configured name for a tier. */
    public static String getTierName(int tier) {
        return tierNames[clampTier(tier)];
    }

    /** Returns energy capacity for a tier. */
    public static int getCapacityForTier(int tier) {
        return capacity[clampTier(tier)];
    }

    /** Returns base energy consumption per second for a tier. */
    public static int getConsumptionPerSecond(int tier) {
        return consumptionPerSecond[clampTier(tier)];
    }

    /** Uses at least one unit or the tier's consumption as the transfer limit. */
    public static int getMaxTransferForTier(int tier) {
        return Math.max(1, getConsumptionPerSecond(tier));
    }

    /** Returns the maximum area dimension for a tier. */
    public static int getMaxAreaForTier(int tier) {
        return maxArea[clampTier(tier)];
    }

    /** Clamps an area dimension between the configured minimum and tier maximum. */
    public static int clampAreaForTier(int tier, int value) {
        int max = getMaxAreaForTier(tier);
        if (value < MIN_AREA) {
            return MIN_AREA;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /** Adds a rounded ten-percent step, at least one, per dimension block beyond the base area. */
    public static int computeConsumptionForArea(int tier, int width, int depth) {
        int basePerSecond = getConsumptionPerSecond(tier);
        int stepPerSecond = Math.max(1, (int) Math.round(basePerSecond * 0.1));
        int extraWidth = Math.max(0, width - BASE_AREA);
        int extraDepth = Math.max(0, depth - BASE_AREA);
        int extraSteps = extraWidth + extraDepth;
        long totalPerSecond = (long) basePerSecond + (long) extraSteps * stepPerSecond;
        return totalPerSecond > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalPerSecond;
    }

    /** Converts the tier's mining duration from seconds to at least one tick. */
    public static int getMiningDelayTicks(int tier) {
        double seconds = getMiningSecondsForTier(tier);
        return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
    }

    /** Linearly interpolates mining duration between the basic and maximum tier settings. */
    public static double getMiningSecondsForTier(int tier) {
        int safeTier = clampTier(tier);
        if (MAX_TIER == MIN_TIER) {
            return basicSpeedSeconds;
        }
        double ratio = (double) (safeTier - MIN_TIER) / (double) (MAX_TIER - MIN_TIER);
        return basicSpeedSeconds + (quantumSpeedSeconds - basicSpeedSeconds) * ratio;
    }

    /** Returns the delay before placing a backfill block. */
    public static int getBackfillPlaceDelayTicks() {
        return backfillPlaceDelayTicks;
    }

    /** Returns the delay after placing a backfill block. */
    public static int getBackfillPostDelayTicks() {
        return backfillPostDelayTicks;
    }

    /** Returns the next upgrade's cost, or an empty array at the highest tier. */
    public static Requirement[] getUpgradeRequirements(int currentTier) {
        int safeTier = clampTier(currentTier);
        if (safeTier >= MAX_TIER) {
            return NO_REQUIREMENTS;
        }
        Requirement[] requirements = upgradeRequirements[safeTier];
        if (requirements == null) {
            return NO_REQUIREMENTS;
        }
        return requirements;
    }

    /** Returns whether block replacement is forced by configuration. */
    public static boolean isForceReplaceBlocks() {
        return forceReplaceBlocks;
    }

    /** Raises configured tier maxima below the minimum quarry dimension. */
    private static void clampMaxArea() {
        for (int i = 0; i < maxArea.length; i++) {
            if (maxArea[i] < MIN_AREA) {
                maxArea[i] = MIN_AREA;
            }
        }
    }

    /** Starts with sanitized defaults, then replaces each supplied tier row. */
    private static Requirement[][] mergeRequirements(Requirement[][] value, Requirement[][] defaults) {
        Requirement[][] result = new Requirement[TIER_COUNT][];
        for (int i = 0; i < TIER_COUNT; i++) {
            Requirement[] fallback = defaults != null && i < defaults.length ? defaults[i] : NO_REQUIREMENTS;
            result[i] = sanitizeRequirements(fallback);
        }
        if (value != null) {
            int limit = Math.min(value.length, TIER_COUNT);
            for (int i = 0; i < limit; i++) {
                Requirement[] cleaned = sanitizeRequirements(value[i]);
                if (cleaned != null) {
                    result[i] = cleaned;
                }
            }
        }
        return result;
    }

    /** Creates a sanitized copy of the requirement table and its entries. */
    private static Requirement[][] copyRequirements(Requirement[][] source) {
        return mergeRequirements(source, source);
    }

    /** Removes null entries, blank IDs, and non-positive quantities. */
    private static Requirement[] sanitizeRequirements(Requirement[] requirements) {
        if (requirements == null || requirements.length == 0) {
            return NO_REQUIREMENTS;
        }
        List<Requirement> cleaned = new ArrayList<>(requirements.length);
        for (Requirement requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            String itemId = normalizeItemId(requirement.itemId);
            int quantity = normalizeQuantity(requirement.quantity);
            if (itemId.isEmpty() || quantity <= 0) {
                continue;
            }
            cleaned.add(new Requirement(itemId, quantity));
        }
        if (cleaned.isEmpty()) {
            return NO_REQUIREMENTS;
        }
        return cleaned.toArray(new Requirement[0]);
    }

    /** Trims IDs and converts null or whitespace-only values to an empty string. */
    private static String normalizeItemId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    /** Treats a missing decoded quantity as zero. */
    private static int normalizeQuantity(Integer value) {
        if (value == null) {
            return 0;
        }
        return normalizeQuantity(value.intValue());
    }

    /** Clamps quantities to zero or above. */
    private static int normalizeQuantity(int value) {
        return Math.max(0, value);
    }

    /** Uses the fallback for a missing decoded duration. */
    private static double normalizeSpeedSeconds(Double value, double fallback) {
        if (value == null) {
            return fallback;
        }
        return normalizeSpeedSeconds(value.doubleValue(), fallback);
    }

    /** Uses the fallback for non-finite durations and enforces a 0.01 second minimum. */
    private static double normalizeSpeedSeconds(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.01, value);
    }

    /** Uses the fallback for a missing decoded tick delay. */
    private static int normalizeDelayTicks(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return normalizeDelayTicks(value.intValue(), fallback);
    }

    /** Uses the fallback for negative tick delays. */
    private static int normalizeDelayTicks(int value, int fallback) {
        if (value < 0) {
            return fallback;
        }
        return value;
    }
}
