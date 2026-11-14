package HyProTechTeam.furnace;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;

/** Holds the furnace tier bounds and configurable energy-consumption multiplier. */
public final class FurnaceConfig {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 6;

    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final int DEFAULT_CONSUMPTION_MULTIPLIER = 3;
    private static int consumptionMultiplier = DEFAULT_CONSUMPTION_MULTIPLIER;

    /** Codec-backed configuration with a default consumption multiplier. */
    public static final class ConfigData {
        public static final BuilderCodec<ConfigData> CODEC =
                BuilderCodec.builder(ConfigData.class, ConfigData::new)
                        .addField(new KeyedCodec<>("ConsumptionMultiplier", INTEGER_CODEC),
                                (config, value) -> {
                                    if (value != null) {
                                        config.consumptionMultiplier = value;
                                    }
                                },
                                config -> config.consumptionMultiplier)
                        .build();

        private int consumptionMultiplier = DEFAULT_CONSUMPTION_MULTIPLIER;

        public ConfigData() {
        }
    }

    private FurnaceConfig() {
    }

    /** Applies a positive multiplier or falls back to the default when it is below one. */
    public static void applyConfig(ConfigData data) {
        if (data == null) {
            return;
        }
        int value = data.consumptionMultiplier;
        consumptionMultiplier = value < 1 ? DEFAULT_CONSUMPTION_MULTIPLIER : value;
    }

    /** Restricts a furnace tier to the supported inclusive range. */
    public static int clampTier(int tier) {
        if (tier < MIN_TIER) {
            return MIN_TIER;
        }
        if (tier > MAX_TIER) {
            return MAX_TIER;
        }
        return tier;
    }

    /** Scales non-negative base consumption for each tier above one, capped at Integer.MAX_VALUE. */
    public static int computeConsumption(int baseConsumption, int tier) {
        long value = Math.max(0L, baseConsumption);
        int safeTier = clampTier(tier);
        int multiplier = Math.max(1, consumptionMultiplier);
        // Multiply once per tier step, limiting each result before the next multiplication.
        for (int i = 1; i < safeTier; i++) {
            value = Math.min((long) Integer.MAX_VALUE, value * multiplier);
        }
        return (int) value;
    }
}
