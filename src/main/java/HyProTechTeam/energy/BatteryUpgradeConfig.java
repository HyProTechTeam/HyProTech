package HyProTechTeam.energy;

import HyProTechTeam.config.ConfigArrays;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.array.IntArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import java.util.ArrayList;
import java.util.List;

/** Holds the active battery tier settings, their defaults, and upgrade costs. */
public final class BatteryUpgradeConfig {
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 5;
    private static final int TIER_COUNT = MAX_TIER - MIN_TIER + 1;

    // These codecs read and write individual configuration fields and their arrays.
    private static final StringCodec STRING_CODEC = new StringCodec();
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final IntArrayCodec INT_ARRAY_CODEC = new IntArrayCodec();
    private static final ArrayCodec<String> STRING_ARRAY_CODEC =
            new ArrayCodec<>(STRING_CODEC, String[]::new);

    /** One item and quantity required to upgrade a battery to its next tier. */
    public static final class Requirement {
        private String itemId;
        private int quantity;

        // Normalize fields during decoding, just as the constructor does for code-created values.
        public static final BuilderCodec<Requirement> CODEC =
                BuilderCodec.builder(Requirement.class, Requirement::new)
                        .addField(new KeyedCodec<>("ItemId", STRING_CODEC),
                                (requirement, value) -> requirement.itemId = normalizeItemId(value),
                                requirement -> requirement.itemId)
                        .addField(new KeyedCodec<>("Quantity", INTEGER_CODEC),
                                (requirement, value) -> requirement.quantity = normalizeQuantity(value),
                                requirement -> requirement.quantity)
                        .build();

        /** Creates an empty requirement for the codec to populate. */
        public Requirement() {
            this("", 0);
        }

        /** Trims the item ID and prevents negative quantities. */
        public Requirement(String itemId, int quantity) {
            this.itemId = normalizeItemId(itemId);
            this.quantity = normalizeQuantity(quantity);
        }

        /** Returns the item identifier used by the upgrade cost. */
        public String getItemId() {
            return itemId;
        }

        /** Returns the number of items required. */
        public int getQuantity() {
            return quantity;
        }
    }

    // One array contains the cost for a tier; the table contains one array per tier.
    private static final ArrayCodec<Requirement> REQUIREMENT_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(Requirement.CODEC, Requirement[]::new);
    private static final ArrayCodec<Requirement[]> REQUIREMENT_TABLE_CODEC =
            new ArrayCodec<>(REQUIREMENT_ARRAY_CODEC, Requirement[][]::new);

    private static final Requirement[] NO_REQUIREMENTS = new Requirement[0];
    // Array indices correspond to tiers 0 through 5 throughout this class.
    private static final String[] DEFAULT_TIER_NAMES = {
            "Basic",
            "Reinforced",
            "Industrial",
            "Advanced",
            "Precision",
            "Quantum"
    };
    private static final int[] DEFAULT_CAPACITY = {
            50_000,
            200_000,
            800_000,
            3_200_000,
            12_800_000,
            50_000_000
    };
    private static final int[] DEFAULT_MAX_TRANSFER = {
            5_000,
            20_000,
            80_000,
            320_000,
            1_280_000,
            5_000_000
    };
    // Each row is the cost to upgrade from that row's tier; the last tier has no successor.
    private static final Requirement[][] DEFAULT_UPGRADE_REQUIREMENTS = {
            new Requirement[] {
                    req("Ingredient_Bar_Copper", 1),
                    req("Ingredient_Leather_Medium", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Iron", 1),
                    req("Ingredient_Fire_Essence", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Silver", 1),
                    req("Ingredient_Bar_Cobalt", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Gold", 1),
                    req("Ingredient_Bar_Thorium", 1),
                    req("Ingredient_Fire_Essence", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Adamantite", 1),
                    req("Ingredient_Bar_Cobalt", 1),
                    req("Ingredient_Leather_Medium", 1)
            },
            new Requirement[0]
    };

    // Keep active values separate from defaults so loading configuration cannot alter the defaults.
    private static String[] tierNames = DEFAULT_TIER_NAMES.clone();
    private static int[] capacity = DEFAULT_CAPACITY.clone();
    private static int[] maxTransfer = DEFAULT_MAX_TRANSFER.clone();
    private static Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);

    /** Serializable configuration; missing fields retain their initialized defaults. */
    public static final class ConfigData {
        // Null decoded fields leave the default arrays intact.
        public static final BuilderCodec<ConfigData> CODEC =
                BuilderCodec.builder(ConfigData.class, ConfigData::new)
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
                        .addField(new KeyedCodec<>("MaxTransfer", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.maxTransfer = value;
                                    }
                                },
                                config -> config.maxTransfer)
                        .addField(new KeyedCodec<>("UpgradeRequirements", REQUIREMENT_TABLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.upgradeRequirements = value;
                                    }
                                },
                                config -> config.upgradeRequirements)
                        .build();

        // Give each decoded configuration its own copies of the default arrays and costs.
        private String[] tierNames = DEFAULT_TIER_NAMES.clone();
        private int[] capacity = DEFAULT_CAPACITY.clone();
        private int[] maxTransfer = DEFAULT_MAX_TRANSFER.clone();
        private Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);

        public ConfigData() {
        }
    }

    private BatteryUpgradeConfig() {
    }

    /** Creates a normalized requirement for the default cost table. */
    private static Requirement req(String itemId, int quantity) {
        return new Requirement(itemId, quantity);
    }

    /** Applies supplied values, filling missing tier entries from defaults and clamping numbers. */
    public static void applyConfig(ConfigData data) {
        if (data == null) {
            return;
        }
        tierNames = ConfigArrays.mergeStringArray(data.tierNames, DEFAULT_TIER_NAMES, TIER_COUNT);
        capacity = ConfigArrays.mergeIntArray(data.capacity, DEFAULT_CAPACITY, TIER_COUNT, 0);
        maxTransfer = ConfigArrays.mergeIntArray(data.maxTransfer, DEFAULT_MAX_TRANSFER, TIER_COUNT, 0);
        upgradeRequirements = mergeRequirements(data.upgradeRequirements, DEFAULT_UPGRADE_REQUIREMENTS);
    }

    /** Restricts an arbitrary tier to the valid range before indexing an array. */
    public static int clampTier(int tier) {
        if (tier < MIN_TIER) {
            return MIN_TIER;
        }
        if (tier > MAX_TIER) {
            return MAX_TIER;
        }
        return tier;
    }

    /** Reports whether the clamped tier can be upgraded further. */
    public static boolean hasNextTier(int tier) {
        return clampTier(tier) < MAX_TIER;
    }

    /** Returns the configured name for the clamped tier. */
    public static String getTierName(int tier) {
        return tierNames[clampTier(tier)];
    }

    /** Returns the configured energy capacity for the clamped tier. */
    public static int getCapacityForTier(int tier) {
        return capacity[clampTier(tier)];
    }

    /** Returns the configured maximum energy transfer for the clamped tier. */
    public static int getMaxTransferForTier(int tier) {
        return maxTransfer[clampTier(tier)];
    }

    /** Returns the cost of upgrading from this tier, or an empty array at the maximum tier. */
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

    /** Starts with sanitized defaults, then replaces rows supplied by the configuration. */
    private static Requirement[][] mergeRequirements(Requirement[][] value, Requirement[][] defaults) {
        Requirement[][] result = new Requirement[TIER_COUNT][];
        for (int i = 0; i < TIER_COUNT; i++) {
            Requirement[] fallback = defaults != null && i < defaults.length ? defaults[i] : NO_REQUIREMENTS;
            result[i] = sanitizeRequirements(fallback);
        }
        // Ignore extra rows; a null or empty supplied row sanitizes to an empty cost.
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

    /** Copies and sanitizes both the table and its individual requirement objects. */
    private static Requirement[][] copyRequirements(Requirement[][] source) {
        return mergeRequirements(source, source);
    }

    /** Drops missing entries, blank IDs, and non-positive quantities from a cost row. */
    private static Requirement[] sanitizeRequirements(Requirement[] requirements) {
        if (requirements == null || requirements.length == 0) {
            return NO_REQUIREMENTS;
        }
        // Create new requirement objects rather than sharing mutable decoded entries.
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

    /** Converts null or whitespace-only IDs to an empty string and trims other IDs. */
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

    /** Quantities below zero are represented as zero. */
    private static int normalizeQuantity(int value) {
        return Math.max(0, value);
    }
}
