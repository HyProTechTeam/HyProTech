package HyProTechTeam.machine;

import HyProTechTeam.config.ConfigArrays;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.array.DoubleArrayCodec;
import com.hypixel.hytale.codec.codecs.array.IntArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.DoubleCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tier, processing, upgrade, and bonus-drop configuration for the alloy smelter. */
public final class AlloySmelterConfig {
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 5;
    public static final int INPUT_SLOT_COUNT = 4;
    public static final int OUTPUT_SLOT_COUNT = 6;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TIER_COUNT = MAX_TIER - MIN_TIER + 1;

    private static final StringCodec STRING_CODEC = new StringCodec();
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final DoubleCodec DOUBLE_CODEC = new DoubleCodec();
    private static final IntArrayCodec INT_ARRAY_CODEC = new IntArrayCodec();
    private static final DoubleArrayCodec DOUBLE_ARRAY_CODEC = new DoubleArrayCodec();
    private static final ArrayCodec<String> STRING_ARRAY_CODEC =
            new ArrayCodec<>(STRING_CODEC, String[]::new);

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
            9000,
            15000,
            24000,
            36000,
            52000
    };

    private static final int[] DEFAULT_CONSUMPTION_PER_SECOND = {
            300,
            600,
            1200,
            2000,
            3200,
            4800
    };

    private static final int[] DEFAULT_OUTPUT_MULTIPLIER = {
            1,
            1,
            1,
            1,
            1,
            1
    };

    private static final double[] DEFAULT_PROCESSING_SECONDS = {
            10.0,
            8.2,
            6.4,
            4.6,
            2.8,
            1.0
    };

    /** One item and quantity required for an upgrade. */
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

    /** Configures an extra item drop and its chance by tier. */
    public static final class BonusDrop {
        private String itemId;
        private int quantity;
        private double baseChance;
        private double perTierChance;

        public static final BuilderCodec<BonusDrop> CODEC =
                BuilderCodec.builder(BonusDrop.class, BonusDrop::new)
                        .addField(new KeyedCodec<>("ItemId", STRING_CODEC),
                                (drop, value) -> drop.itemId = normalizeItemId(value),
                                drop -> drop.itemId)
                        .addField(new KeyedCodec<>("Quantity", INTEGER_CODEC),
                                (drop, value) -> drop.quantity = normalizeQuantity(value),
                                drop -> drop.quantity)
                        .addField(new KeyedCodec<>("BaseChance", DOUBLE_CODEC),
                                (drop, value) -> drop.baseChance = normalizeChance(value),
                                drop -> drop.baseChance)
                        .addField(new KeyedCodec<>("PerTierChance", DOUBLE_CODEC),
                                (drop, value) -> drop.perTierChance = normalizeChance(value),
                                drop -> drop.perTierChance)
                        .build();

        /** Creates an empty drop for decoding. */
        public BonusDrop() {
            this("", 0, 0.0, 0.0);
        }

        /** Normalizes the item, quantity, and chance values. */
        public BonusDrop(String itemId, int quantity, double baseChance, double perTierChance) {
            this.itemId = normalizeItemId(itemId);
            this.quantity = normalizeQuantity(quantity);
            this.baseChance = normalizeChance(baseChance);
            this.perTierChance = normalizeChance(perTierChance);
        }

        /** Returns the configured bonus item ID. */
        public String getItemId() {
            return itemId;
        }

        /** Returns the configured bonus item count. */
        public int getQuantity() {
            return quantity;
        }

        /** Adds the per-tier chance and clamps the resulting probability to zero through one. */
        public double getChance(int tier) {
            int safeTier = clampTier(tier);
            double value = baseChance + perTierChance * safeTier;
            if (value < 0.0) {
                return 0.0;
            }
            return Math.min(1.0, value);
        }

        /** Returns the configured base chance. */
        public double getBaseChance() {
            return baseChance;
        }

        /** Returns the configured chance increment per tier. */
        public double getPerTierChance() {
            return perTierChance;
        }
    }

    private static final ArrayCodec<Requirement> REQUIREMENT_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(Requirement.CODEC, Requirement[]::new);
    private static final ArrayCodec<Requirement[]> REQUIREMENT_TABLE_CODEC =
            new ArrayCodec<>(REQUIREMENT_ARRAY_CODEC, Requirement[][]::new);
    private static final ArrayCodec<BonusDrop> BONUS_DROP_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(BonusDrop.CODEC, BonusDrop[]::new);

    private static final Requirement[][] DEFAULT_UPGRADE_REQUIREMENTS = {
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Copper", 20),
                    new Requirement("Ingredient_Bar_Iron", 10),
                    new Requirement("Ingredient_Leather_Medium", 10),
                    new Requirement("Ingredient_Bar_Silver", 5)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Iron", 20),
                    new Requirement("Ingredient_Bar_Silver", 10),
                    new Requirement("Ingredient_Bar_Gold", 8),
                    new Requirement("Ingredient_Bar_Cobalt", 6)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Cobalt", 12),
                    new Requirement("Ingredient_Bar_Adamantite", 4),
                    new Requirement("HyProTech_Slag", 12),
                    new Requirement("HyProTech_Ore_Chips", 8),
                    new Requirement("HyProTech_Powder_Cobalt", 10)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Adamantite", 10),
                    new Requirement("Ingredient_Bar_Thorium", 6),
                    new Requirement("HyProTech_Slag", 18),
                    new Requirement("HyProTech_Ore_Chips", 12),
                    new Requirement("HyProTech_Powder_Iron", 16)
            },
            new Requirement[] {
                    new Requirement("Ingredient_Bar_Adamantite", 20),
                    new Requirement("Ingredient_Bar_Thorium", 10),
                    new Requirement("HyProTech_Slag", 25),
                    new Requirement("HyProTech_Ore_Chips", 18),
                    new Requirement("HyProTech_Powder_Gold", 20)
            },
            new Requirement[0]
    };

    private static final BonusDrop[] DEFAULT_BONUS_DROPS = {
            new BonusDrop("HyProTech_Slag", 1, 0.50, 0.00),
            new BonusDrop("HyProTech_Ore_Chips", 1, 0.10, 0.00)
    };

    private static String[] tierNames = DEFAULT_TIER_NAMES.clone();
    private static int[] capacity = DEFAULT_CAPACITY.clone();
    private static int[] consumptionPerSecond = DEFAULT_CONSUMPTION_PER_SECOND.clone();
    private static int[] outputMultiplier = DEFAULT_OUTPUT_MULTIPLIER.clone();
    private static double[] processingSeconds = DEFAULT_PROCESSING_SECONDS.clone();
    private static Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);
    private static BonusDrop[] bonusDrops = copyBonusDrops(DEFAULT_BONUS_DROPS);
    private static List<BonusDrop> bonusDropList = toBonusDropList(bonusDrops);

    /** Codec-backed settings initialized with copies of the defaults. */
    public static final class ConfigData {
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
                        .addField(new KeyedCodec<>("ConsumptionPerSecond", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.consumptionPerSecond = value;
                                    }
                                },
                                config -> config.consumptionPerSecond)
                        .addField(new KeyedCodec<>("OutputMultiplier", INT_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.outputMultiplier = value;
                                    }
                                },
                                config -> config.outputMultiplier)
                        .addField(new KeyedCodec<>("ProcessingSeconds", DOUBLE_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.processingSeconds = value;
                                    }
                                },
                                config -> config.processingSeconds)
                        .addField(new KeyedCodec<>("UpgradeRequirements", REQUIREMENT_TABLE_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.upgradeRequirements = value;
                                    }
                                },
                                config -> config.upgradeRequirements)
                        .addField(new KeyedCodec<>("BonusDrops", BONUS_DROP_ARRAY_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.bonusDrops = value;
                                    }
                                },
                                config -> config.bonusDrops)
                        .build();

        private String[] tierNames = DEFAULT_TIER_NAMES.clone();
        private int[] capacity = DEFAULT_CAPACITY.clone();
        private int[] consumptionPerSecond = DEFAULT_CONSUMPTION_PER_SECOND.clone();
        private int[] outputMultiplier = DEFAULT_OUTPUT_MULTIPLIER.clone();
        private double[] processingSeconds = DEFAULT_PROCESSING_SECONDS.clone();
        private Requirement[][] upgradeRequirements = copyRequirements(DEFAULT_UPGRADE_REQUIREMENTS);
        private BonusDrop[] bonusDrops = copyBonusDrops(DEFAULT_BONUS_DROPS);

        public ConfigData() {
        }
    }

    private AlloySmelterConfig() {
    }

    /** Merges supplied tier values, processing settings, upgrade costs, and bonus drops. */
    public static void applyConfig(ConfigData data) {
        if (data == null) {
            return;
        }
        tierNames = ConfigArrays.mergeStringArray(data.tierNames, DEFAULT_TIER_NAMES, TIER_COUNT);
        capacity = ConfigArrays.mergeIntArray(data.capacity, DEFAULT_CAPACITY, TIER_COUNT, 0);
        consumptionPerSecond = ConfigArrays.mergeIntArray(
                data.consumptionPerSecond,
                DEFAULT_CONSUMPTION_PER_SECOND,
                TIER_COUNT,
                0);
        outputMultiplier = ConfigArrays.mergeIntArray(
                data.outputMultiplier,
                DEFAULT_OUTPUT_MULTIPLIER,
                TIER_COUNT,
                1);
        processingSeconds = ConfigArrays.mergeDoubleArray(
                data.processingSeconds,
                DEFAULT_PROCESSING_SECONDS,
                TIER_COUNT,
                0.01);
        upgradeRequirements = mergeRequirements(data.upgradeRequirements, DEFAULT_UPGRADE_REQUIREMENTS);
        bonusDrops = mergeBonusDrops(data.bonusDrops, DEFAULT_BONUS_DROPS);
        bonusDropList = toBonusDropList(bonusDrops);
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

    /** Returns the configured energy capacity. */
    public static int getCapacityForTier(int tier) {
        return capacity[clampTier(tier)];
    }

    /** Returns the configured energy consumption per second. */
    public static int getConsumptionPerSecond(int tier) {
        return consumptionPerSecond[clampTier(tier)];
    }

    /** Uses at least one unit or the configured consumption as the transfer limit. */
    public static int getMaxTransferForTier(int tier) {
        return Math.max(1, getConsumptionPerSecond(tier));
    }

    /** Returns the configured output multiplier. */
    public static int getOutputMultiplierForTier(int tier) {
        return outputMultiplier[clampTier(tier)];
    }

    /** Returns the configured processing duration in seconds. */
    public static double getProcessingSecondsForTier(int tier) {
        return processingSeconds[clampTier(tier)];
    }

    /** Converts processing seconds to at least one game tick. */
    public static int getProcessingDelayTicks(int tier) {
        double seconds = getProcessingSecondsForTier(tier);
        return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
    }

    /** Returns the next upgrade cost, or an empty array at the highest tier. */
    public static Requirement[] getUpgradeRequirements(int currentTier) {
        int safeTier = clampTier(currentTier);
        if (safeTier >= MAX_TIER) {
            return new Requirement[0];
        }
        Requirement[] requirements = upgradeRequirements[safeTier];
        if (requirements == null) {
            return new Requirement[0];
        }
        return requirements;
    }

    /** Returns the configured bonus list for a nonempty input ID, otherwise an empty list. */
    public static List<BonusDrop> getBonusDropsForInput(String inputId) {
        if (inputId == null || inputId.isEmpty()) {
            return Collections.emptyList();
        }
        return bonusDropList;
    }

    /** Returns the configured bonus-drop list. */
    public static List<BonusDrop> getBonusDropConfig() {
        return bonusDropList;
    }

    /** Starts with sanitized defaults and replaces each supplied tier row. */
    private static Requirement[][] mergeRequirements(Requirement[][] value, Requirement[][] defaults) {
        Requirement[][] result = new Requirement[TIER_COUNT][];
        for (int i = 0; i < TIER_COUNT; i++) {
            Requirement[] fallback = defaults != null && i < defaults.length ? defaults[i] : new Requirement[0];
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

    /** Drops null entries, blank IDs, and non-positive counts. */
    private static Requirement[] sanitizeRequirements(Requirement[] requirements) {
        if (requirements == null || requirements.length == 0) {
            return new Requirement[0];
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
            return new Requirement[0];
        }
        return cleaned.toArray(new Requirement[0]);
    }

    /** Uses sanitized supplied drops, or copies the defaults when the value is null. */
    private static BonusDrop[] mergeBonusDrops(BonusDrop[] value, BonusDrop[] defaults) {
        if (value == null) {
            return copyBonusDrops(defaults);
        }
        return sanitizeBonusDrops(value);
    }

    /** Returns a sanitized copy of the bonus drops. */
    private static BonusDrop[] copyBonusDrops(BonusDrop[] source) {
        if (source == null || source.length == 0) {
            return new BonusDrop[0];
        }
        return sanitizeBonusDrops(source);
    }

    /** Copies valid drops and normalizes their item IDs, counts, and chance values. */
    private static BonusDrop[] sanitizeBonusDrops(BonusDrop[] drops) {
        if (drops == null || drops.length == 0) {
            return new BonusDrop[0];
        }
        List<BonusDrop> cleaned = new ArrayList<>(drops.length);
        for (BonusDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            String itemId = normalizeItemId(drop.itemId);
            int quantity = normalizeQuantity(drop.quantity);
            double baseChance = normalizeChance(drop.baseChance);
            double perTierChance = normalizeChance(drop.perTierChance);
            if (itemId.isEmpty() || quantity <= 0) {
                continue;
            }
            cleaned.add(new BonusDrop(itemId, quantity, baseChance, perTierChance));
        }
        if (cleaned.isEmpty()) {
            return new BonusDrop[0];
        }
        return cleaned.toArray(new BonusDrop[0]);
    }

    /** Exposes the array as an unmodifiable list, or an empty list when absent. */
    private static List<BonusDrop> toBonusDropList(BonusDrop[] drops) {
        if (drops == null || drops.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(drops));
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

    /** Treats a missing decoded chance as zero. */
    private static double normalizeChance(Double value) {
        if (value == null) {
            return 0.0;
        }
        return normalizeChance(value.doubleValue());
    }

    /** Replaces non-finite chance values with zero. */
    private static double normalizeChance(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return value;
    }
}
