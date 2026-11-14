package HyProTechTeam.machine;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Persistent machine identity, progress, and quarry-area settings for a block entity. */
public class MachineComponent implements Component<ChunkStore> {
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final StringCodec STRING_CODEC = new StringCodec();
    private static final BooleanCodec BOOLEAN_CODEC = new BooleanCodec();

    // Supply defaults for missing fields and normalize decoded quarry dimensions.
    public static final BuilderCodec<MachineComponent> CODEC =
            BuilderCodec.<MachineComponent>builder(MachineComponent.class, MachineComponent::new)
                    .addField(new KeyedCodec<>("MachineId", STRING_CODEC),
                            (component, value) -> component.machineId = value == null ? "" : value,
                            component -> component.machineId)
                    .addField(new KeyedCodec<>("Tier", INTEGER_CODEC),
                            (component, value) -> component.tier = value == null ? 0 : value,
                            component -> component.tier)
                    .addField(new KeyedCodec<>("Progress", INTEGER_CODEC),
                            (component, value) -> component.progress = value == null ? 0 : value,
                            component -> component.progress)
                    .addField(new KeyedCodec<>("ProgressMax", INTEGER_CODEC),
                            (component, value) -> component.progressMax = value == null ? 0 : value,
                            component -> component.progressMax)
                    .addField(new KeyedCodec<>("Enabled", BOOLEAN_CODEC),
                            (component, value) -> component.enabled = value == null || value,
                            component -> component.enabled)
                    .addField(new KeyedCodec<>("Working", BOOLEAN_CODEC),
                            (component, value) -> component.working = value != null && value,
                            component -> component.working)
                    .addField(new KeyedCodec<>("LastAnimTier", INTEGER_CODEC),
                            (component, value) -> component.lastAnimTier = value == null ? 0 : value,
                            component -> component.lastAnimTier)
                    .addField(new KeyedCodec<>("AreaWidth", INTEGER_CODEC),
                            (component, value) -> component.areaWidth = clampArea(value),
                            component -> component.areaWidth)
                    .addField(new KeyedCodec<>("AreaDepth", INTEGER_CODEC),
                            (component, value) -> component.areaDepth = clampArea(value),
                            component -> component.areaDepth)
                    .addField(new KeyedCodec<>("AreaVisible", BOOLEAN_CODEC),
                            (component, value) -> component.areaVisible = value != null && value,
                            component -> component.areaVisible)
                    .addField(new KeyedCodec<>("ReplaceMinedBlocks", BOOLEAN_CODEC),
                            (component, value) -> component.replaceMinedBlocks = value != null && value,
                            component -> component.replaceMinedBlocks)
                    .build();

    private String machineId = "";
    private int tier;
    private int progress;
    private int progressMax;
    private boolean enabled = true;
    private boolean working;
    private int lastAnimTier;
    private int areaWidth = defaultAreaSize();
    private int areaDepth = defaultAreaSize();
    private boolean areaVisible;
    private boolean replaceMinedBlocks;
    // Sound scheduling is runtime state and is not encoded in the persisted component.
    private transient long nextSoundMs;

    /** Returns the machine type identifier. */
    public String getMachineId() {
        return machineId;
    }

    /** Stores an empty ID instead of null. */
    public void setMachineId(String machineId) {
        this.machineId = machineId == null ? "" : machineId;
    }

    /** Returns the machine upgrade tier. */
    public int getTier() {
        return tier;
    }

    /** Prevents a negative machine tier. */
    public void setTier(int tier) {
        this.tier = Math.max(0, tier);
    }

    /** Returns current processing progress. */
    public int getProgress() {
        return progress;
    }

    /** Prevents negative machine progress. */
    public void setProgress(int progress) {
        this.progress = Math.max(0, progress);
    }

    /** Returns the processing progress threshold. */
    public int getProgressMax() {
        return progressMax;
    }

    /** Prevents a negative progress threshold. */
    public void setProgressMax(int progressMax) {
        this.progressMax = Math.max(0, progressMax);
    }

    /** Returns whether the machine is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Sets whether the machine is enabled. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns whether the machine is currently working. */
    public boolean isWorking() {
        return working;
    }

    /** Sets the machine's current working flag. */
    public void setWorking(boolean working) {
        this.working = working;
    }

    /** Returns the last animation tier recorded on the component. */
    public int getLastAnimTier() {
        return lastAnimTier;
    }

    /** Stores a non-negative last animation tier. */
    public void setLastAnimTier(int lastAnimTier) {
        this.lastAnimTier = Math.max(0, lastAnimTier);
    }

    /** Returns the configured quarry width. */
    public int getAreaWidth() {
        return areaWidth;
    }

    /** Sets the quarry width using the configured minimum and default rules. */
    public void setAreaWidth(int areaWidth) {
        this.areaWidth = clampArea(areaWidth);
    }

    /** Returns the configured quarry depth. */
    public int getAreaDepth() {
        return areaDepth;
    }

    /** Sets the quarry depth using the configured minimum and default rules. */
    public void setAreaDepth(int areaDepth) {
        this.areaDepth = clampArea(areaDepth);
    }

    /** Returns whether the quarry area display is enabled. */
    public boolean isAreaVisible() {
        return areaVisible;
    }

    /** Sets whether the quarry area display is enabled. */
    public void setAreaVisible(boolean areaVisible) {
        this.areaVisible = areaVisible;
    }

    /** Returns whether mined blocks should be replaced. */
    public boolean isReplaceMinedBlocks() {
        return replaceMinedBlocks;
    }

    /** Sets the machine's mined-block replacement preference. */
    public void setReplaceMinedBlocks(boolean replaceMinedBlocks) {
        this.replaceMinedBlocks = replaceMinedBlocks;
    }

    /** Returns the runtime deadline for the next machine sound. */
    public long getNextSoundMs() {
        return nextSoundMs;
    }

    /** Sets the runtime deadline for the next machine sound. */
    public void setNextSoundMs(long nextSoundMs) {
        this.nextSoundMs = nextSoundMs;
    }

    /** Copies machine fields and the runtime sound timestamp into a new component. */
    @Override
    public Component<ChunkStore> clone() {
        MachineComponent copy = new MachineComponent();
        copy.machineId = machineId;
        copy.tier = tier;
        copy.progress = progress;
        copy.progressMax = progressMax;
        copy.enabled = enabled;
        copy.working = working;
        copy.lastAnimTier = lastAnimTier;
        copy.areaWidth = areaWidth;
        copy.areaDepth = areaDepth;
        copy.areaVisible = areaVisible;
        copy.replaceMinedBlocks = replaceMinedBlocks;
        copy.nextSoundMs = nextSoundMs;
        return copy;
    }

    /** Uses the same explicit field copy for serialization. */
    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }

    /** Uses the default for null or non-positive dimensions and enforces the minimum otherwise. */
    private static int clampArea(Integer value) {
        int defaultSize = defaultAreaSize();
        int minSize = minAreaSize();
        if (value == null || value <= 0) {
            return defaultSize;
        }
        if (value < minSize) {
            return minSize;
        }
        return value;
    }

    /** Reads the configured base quarry dimension, with a lower bound of one. */
    private static int defaultAreaSize() {
        return Math.max(1, QuarryConfig.BASE_AREA);
    }

    /** Reads the configured minimum quarry dimension, with a lower bound of one. */
    private static int minAreaSize() {
        return Math.max(1, QuarryConfig.MIN_AREA);
    }

}
