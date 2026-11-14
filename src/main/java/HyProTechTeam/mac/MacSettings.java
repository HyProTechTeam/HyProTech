package HyProTechTeam.mac;

import java.util.List;

/** Data model for settings exposed through the optional MAC configuration integration. */
public final class MacSettings {
    public Battery battery = new Battery();
    public Solar solar = new Solar();
    public Wind wind = new Wind();
    public Cable cable = new Cable();
    public Furnace furnace = new Furnace();
    public OreCrusher oreCrusher = new OreCrusher();
    public AlloySmelter alloySmelter = new AlloySmelter();
    public Quarry quarry = new Quarry();

    /** An item ID and quantity in an upgrade cost. */
    public static final class Requirement {
        public String itemId;
        public Integer quantity;
    }

    /** Groups the requirements for one upgrade tier. */
    public static final class RequirementGroup {
        public List<Requirement> items;
    }

    /** Item, count, and chance settings for an optional processing bonus. */
    public static final class BonusDrop {
        public String itemId;
        public Integer quantity;
        public Double baseChance;
        public Double perTierChance;
    }

    /** Battery tier names, energy settings, and upgrade costs. */
    public static final class Battery {
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> maxTransfer;
        public List<RequirementGroup> upgradeRequirements;
    }

    /** Solar tier names, capacity, generation, and upgrade costs. */
    public static final class Solar {
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> generation;
        public List<RequirementGroup> upgradeRequirements;
    }

    /** Wind tier names, capacity, generation, and upgrade costs. */
    public static final class Wind {
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> generation;
        public List<RequirementGroup> upgradeRequirements;
    }

    /** Cable energy and item transfer settings and upgrade costs by tier. */
    public static final class Cable {
        public List<String> tierNames;
        public List<Integer> energyCapacity;
        public List<Integer> energyMaxTransfer;
        public List<Integer> itemMaxTransfer;
        public List<RequirementGroup> upgradeRequirements;
    }

    /** Furnace consumption multiplier override. */
    public static final class Furnace {
        public Integer consumptionMultiplier;
    }

    /** Ore-crusher tier, processing, upgrade, and bonus-drop overrides. */
    public static final class OreCrusher {
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> consumptionPerSecond;
        public List<Integer> outputMultiplier;
        public List<Double> processingSeconds;
        public List<RequirementGroup> upgradeRequirements;
        public List<BonusDrop> bonusDrops;
        public BonusDrop powderBonusDrop;
    }

    /** Alloy-smelter tier, processing, upgrade, and bonus-drop overrides. */
    public static final class AlloySmelter {
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> consumptionPerSecond;
        public List<Integer> outputMultiplier;
        public List<Double> processingSeconds;
        public List<RequirementGroup> upgradeRequirements;
        public List<BonusDrop> bonusDrops;
    }

    /** Quarry area, timing, energy, upgrade, and replacement overrides. */
    public static final class Quarry {
        public Integer baseArea;
        public Integer minArea;
        public Double basicSpeedSeconds;
        public Double quantumSpeedSeconds;
        public Integer backfillPlaceDelayTicks;
        public Integer backfillPostDelayTicks;
        public List<String> tierNames;
        public List<Integer> capacity;
        public List<Integer> consumptionPerSecond;
        public List<Integer> maxArea;
        public List<RequirementGroup> upgradeRequirements;
        public Boolean forceReplaceBlocks;
    }
}
