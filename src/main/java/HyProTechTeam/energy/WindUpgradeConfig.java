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

/** Holds tier names, storage, generation, and upgrade costs for wind turbines. */
public final class WindUpgradeConfig {
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 5;
    private static final int TIER_COUNT = MAX_TIER - MIN_TIER + 1;

    // Reuse codecs for scalar fields and arrays in the configuration format.
    private static final StringCodec STRING_CODEC = new StringCodec();
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final IntArrayCodec INT_ARRAY_CODEC = new IntArrayCodec();
    private static final ArrayCodec<String> STRING_ARRAY_CODEC =
            new ArrayCodec<>(STRING_CODEC, String[]::new);

    /** One item and quantity required to upgrade from a tier. */
    public static final class Requirement {
        private String itemId;
        private int quantity;

        // Normalize decoded item IDs and quantities before storing them.
        public static final BuilderCodec<Requirement> CODEC =
                BuilderCodec.builder(Requirement.class, Requirement::new)
                        .addField(new KeyedCodec<>("ItemId", STRING_CODEC),
                                (requirement, value) -> requirement.itemId = normalizeItemId(value),
                                requirement -> requirement.itemId)
                        .addField(new KeyedCodec<>("Quantity", INTEGER_CODEC),
                                (requirement, value) -> requirement.quantity = normalizeQuantity(value),
                                requirement -> requirement.quantity)
                        .build();

        /** Supplies empty values until the codec populates the fields. */
        public Requirement() {
            this("", 0);
        }

        /** Trims the ID and prevents a negative quantity. */
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

    // A row is one upgrade's costs; the outer array contains one row per tier.
    private static final ArrayCodec<Requirement> REQUIREMENT_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(Requirement.CODEC, Requirement[]::new);
    private static final ArrayCodec<Requirement[]> REQUIREMENT_TABLE_CODEC =
            new ArrayCodec<>(REQUIREMENT_ARRAY_CODEC, Requirement[][]::new);

    private static final Requirement[] NO_REQUIREMENTS = new Requirement[0];
    // Indices in each defaults array correspond to tiers zero through five.
    private static final String[] DEFAULT_TIER_NAMES = {
            "Basic",
            "Reinforced",
            "Industrial",
            "Advanced",
            "Precision",
            "Quantum"
    };
    private static final int[] DEFAULT_CAPACITY = {
            5000,
            10000,
            20000,
            40000,
            80000,
            160000
    };
    private static final int[] DEFAULT_GENERATION = {
            500,
            910,
            1660,
            3020,
            5490,
            10000
    };
    // Each row is the cost to leave that tier; the highest tier has no next upgrade.
    private static final Requirement[][] DEFAULT_UPGRADE_REQUIREMENTS = {
            new Requirement[] {
                    req("Ingredient_Bar_Copper", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Iron", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Silver", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Gold", 1)
            },
            new Requirement[] {
                    req("Ingredient_Bar_Cobalt", 1)
            },
            new Requirement[0]
    };

    // Copy defaults into active settings so applying configuration does not alter the defaults.
    private static String[] tierNames = DEFAULT_TIER_NAMES.clone();
    private static int[] capacity = DEFAULT_CAPACITY.clone();
    private static int[] generation = DEFAULT_GENERATION.clone();
    private static Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);

    /** Codec-backed settings whose initial values are independent copies of the defaults. */
    public static final class ConfigData {
        // Keep initialized defaults whenever a decoded field is null.
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
                        .addField(new KeyedCodec<>("Generation", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.generation = value;
                                    }
                                },
                                config -> config.generation)
                        .addField(new KeyedCodec<>("UpgradeRequirements", REQUIREMENT_TABLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.upgradeRequirements = value;
                                    }
                                },
                                config -> config.upgradeRequirements)
                        .build();

        private String[] tierNames = DEFAULT_TIER_NAMES.clone();
        private int[] capacity = DEFAULT_CAPACITY.clone();
        private int[] generation = DEFAULT_GENERATION.clone();
        private Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);

        public ConfigData() {
        }
    }

    private WindUpgradeConfig() {
    }

    /** Creates a normalized requirement for the default cost table. */
    private static Requirement req(String itemId, int quantity) {
        return new Requirement(itemId, quantity);
    }

    /** Merges supplied tier values with defaults, clamping numeric values to zero or above. */
    public static void applyConfig(ConfigData data) {
        if (data == null) {
            return;
        }
        tierNames = ConfigArrays.mergeStringArray(data.tierNames, DEFAULT_TIER_NAMES, TIER_COUNT);
        capacity = ConfigArrays.mergeIntArray(data.capacity, DEFAULT_CAPACITY, TIER_COUNT, 0);
        generation = ConfigArrays.mergeIntArray(data.generation, DEFAULT_GENERATION, TIER_COUNT, 0);
        upgradeRequirements = mergeRequirements(data.upgradeRequirements, DEFAULT_UPGRADE_REQUIREMENTS);
    }

    /** Restricts an arbitrary tier to the supported array index range. */
    public static int clampTier(int tier) {
        if (tier < MIN_TIER) {
            return MIN_TIER;
        }
        if (tier > MAX_TIER) {
            return MAX_TIER;
        }
        return tier;
    }

    /** Returns whether the clamped tier has a successor. */
    public static boolean hasNextTier(int tier) {
        return clampTier(tier) < MAX_TIER;
    }

    /** Returns the configured name for the clamped tier. */
    public static String getTierName(int tier) {
        int safeTier = clampTier(tier);
        return tierNames[safeTier];
    }

    /** Returns the configured storage capacity for the clamped tier. */
    public static int getCapacityForTier(int tier) {
        int safeTier = clampTier(tier);
        return capacity[safeTier];
    }

    /** Returns the configured generation value for the clamped tier. */
    public static int getGenerationForTier(int tier) {
        int safeTier = clampTier(tier);
        return generation[safeTier];
    }

    /** Uses the tier's generation value as its maximum transfer setting. */
    public static int getMaxTransferForTier(int tier) {
        return getGenerationForTier(tier);
    }

    /** Returns the cost of the next upgrade, or an empty array at the highest tier. */
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

    /** Starts with sanitized defaults, then replaces each supplied tier row. */
    private static Requirement[][] mergeRequirements(Requirement[][] value, Requirement[][] defaults) {
        Requirement[][] result = new Requirement[TIER_COUNT][];
        for (int i = 0; i < TIER_COUNT; i++) {
            Requirement[] fallback = defaults != null && i < defaults.length ? defaults[i] : NO_REQUIREMENTS;
            result[i] = sanitizeRequirements(fallback);
        }
        // A null or empty supplied row sanitizes to an empty cost; extra rows are ignored.
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

    /** Creates a sanitized copy of the table and its requirement objects. */
    private static Requirement[][] copyRequirements(Requirement[][] source) {
        return mergeRequirements(source, source);
    }

    /** Removes null entries, blank item IDs, and non-positive item counts. */
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

    /** Trims an item ID and represents null or whitespace-only IDs as empty strings. */
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

    /** Clamps a quantity to zero or above. */
    private static int normalizeQuantity(int value) {
        return Math.max(0, value);
    }
}
