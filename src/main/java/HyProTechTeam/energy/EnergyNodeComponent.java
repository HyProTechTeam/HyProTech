package HyProTechTeam.energy;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class EnergyNodeComponent implements Component<ChunkStore> {
    public enum NodeType {
        SOLAR,
        CABLE,
        BATTERY,
        MACHINE,
        FURNACE,
        // Legacy value used by older quarry nodes; treat as MACHINE.
        QUARRY,
        WIND
    }

    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final BooleanCodec BOOLEAN_CODEC = new BooleanCodec();

    public static final BuilderCodec<EnergyNodeComponent> CODEC =
            BuilderCodec.<EnergyNodeComponent>builder(EnergyNodeComponent.class, EnergyNodeComponent::new)
                    .addField(new KeyedCodec<>("NodeType", new EnumCodec<>(NodeType.class)),
                            (component, value) -> component.nodeType = value,
                            component -> component.nodeType)
                    .addField(new KeyedCodec<>("Energy", INTEGER_CODEC),
                            (component, value) -> component.energy = value,
                            component -> component.energy)
                    .addField(new KeyedCodec<>("Capacity", INTEGER_CODEC),
                            (component, value) -> component.capacity = value,
                            component -> component.capacity)
                    .addField(new KeyedCodec<>("MaxTransfer", INTEGER_CODEC),
                            (component, value) -> component.maxTransfer = value,
                            component -> component.maxTransfer)
                    .addField(new KeyedCodec<>("Generation", INTEGER_CODEC),
                            (component, value) -> component.generation = value,
                            component -> component.generation)
                    .addField(new KeyedCodec<>("Consumption", INTEGER_CODEC),
                            (component, value) -> component.consumption = value,
                            component -> component.consumption)
                    .addField(new KeyedCodec<>("FurnaceBaseConsumption", INTEGER_CODEC),
                            (component, value) -> component.furnaceBaseConsumption =
                                    value != null ? value : 0,
                            component -> component.furnaceBaseConsumption)
                    .addField(new KeyedCodec<>("Progress", INTEGER_CODEC),
                            (component, value) -> component.progress = value,
                            component -> component.progress)
                    .addField(new KeyedCodec<>("ProgressMax", INTEGER_CODEC),
                            (component, value) -> component.progressMax = value,
                            component -> component.progressMax)
                    .addField(new KeyedCodec<>("InputMask", INTEGER_CODEC),
                            (component, value) -> component.inputMask = value,
                            component -> component.inputMask)
                    .addField(new KeyedCodec<>("OutputMask", INTEGER_CODEC),
                            (component, value) -> component.outputMask = value,
                            component -> component.outputMask)
                    .addField(new KeyedCodec<>("CableColor", INTEGER_CODEC),
                            (component, value) -> component.cableColor = value != null ? value : 0,
                            component -> component.cableColor)
                    .addField(new KeyedCodec<>("CableTier", INTEGER_CODEC),
                            (component, value) -> component.cableTier =
                                    value != null ? value : CableUpgradeConfig.MIN_TIER,
                            component -> component.cableTier)
                    .addField(new KeyedCodec<>("SolarTier", INTEGER_CODEC),
                            (component, value) -> component.solarTier =
                                    value != null ? value : SolarUpgradeConfig.MIN_TIER,
                            component -> component.solarTier)
                    .addField(new KeyedCodec<>("WindTier", INTEGER_CODEC),
                            (component, value) -> component.windTier =
                                    value != null ? value : WindUpgradeConfig.MIN_TIER,
                            component -> component.windTier)
                    .addField(new KeyedCodec<>("Enabled", BOOLEAN_CODEC),
                            (component, value) -> component.enabled = value != null ? value : true,
                            component -> component.enabled)
                    .build();

    private NodeType nodeType = NodeType.CABLE;
    private int energy;
    private int capacity;
    private int maxTransfer;
    private int generation;
    private int consumption;
    private int furnaceBaseConsumption;
    private int progress;
    private int progressMax;
    private int inputMask = EnergySide.ALL_MASK;
    private int outputMask = EnergySide.ALL_MASK;
    private int cableColor;
    private int cableTier = CableUpgradeConfig.MIN_TIER;
    private int solarTier = SolarUpgradeConfig.MIN_TIER;
    private int windTier = WindUpgradeConfig.MIN_TIER;
    private transient int connectedMask = EnergySide.ALL_MASK;
    private transient EnergyNodeStorage storage;
    private transient String lastFurnaceState = "";
    private transient String lastCableState = "";
    private transient long furnaceWorkingStartMs;
    private transient boolean furnaceWorking;
    private transient String lastWindState = "";
    private transient long lastWindAnimMs;
    private transient long nextSoundMs;
    private boolean enabled = true;

    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean isMachineLike() {
        return isMachineLike(nodeType);
    }

    public static boolean isMachineLike(NodeType type) {
        return type == NodeType.MACHINE || type == NodeType.QUARRY;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getMaxTransfer() {
        return maxTransfer;
    }

    public void setMaxTransfer(int maxTransfer) {
        this.maxTransfer = maxTransfer;
    }

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public int getConsumption() {
        return consumption;
    }

    public void setConsumption(int consumption) {
        this.consumption = consumption;
    }

    public int getFurnaceBaseConsumption() {
        return furnaceBaseConsumption;
    }

    public void setFurnaceBaseConsumption(int furnaceBaseConsumption) {
        this.furnaceBaseConsumption = Math.max(0, furnaceBaseConsumption);
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getProgressMax() {
        return progressMax;
    }

    public void setProgressMax(int progressMax) {
        this.progressMax = progressMax;
    }

    public int getInputMask() {
        return inputMask;
    }

    public void setInputMask(int inputMask) {
        this.inputMask = inputMask & EnergySide.ALL_MASK;
    }

    public int getOutputMask() {
        return outputMask;
    }

    public void setOutputMask(int outputMask) {
        this.outputMask = outputMask & EnergySide.ALL_MASK;
    }

    public int getCableColor() {
        return cableColor;
    }

    public void setCableColor(int cableColor) {
        this.cableColor = cableColor;
    }

    public int getCableTier() {
        return cableTier;
    }

    public void setCableTier(int cableTier) {
        this.cableTier = cableTier;
    }

    public int getSolarTier() {
        return solarTier;
    }

    public void setSolarTier(int solarTier) {
        this.solarTier = SolarUpgradeConfig.clampTier(solarTier);
    }

    public int getWindTier() {
        return windTier;
    }

    public void setWindTier(int windTier) {
        this.windTier = WindUpgradeConfig.clampTier(windTier);
    }

    public boolean allowsInput(EnergySide side) {
        return (inputMask & side.mask()) != 0;
    }

    public boolean allowsOutput(EnergySide side) {
        return (outputMask & side.mask()) != 0;
    }

    public EnergySideMode getSideMode(EnergySide side) {
        boolean input = allowsInput(side);
        boolean output = allowsOutput(side);
        return EnergySideMode.fromFlags(input, output);
    }

    public void setSideMode(EnergySide side, EnergySideMode mode) {
        int mask = side.mask();
        switch (mode) {
            case DISABLED:
                inputMask &= ~mask;
                outputMask &= ~mask;
                break;
            case INPUT:
                inputMask |= mask;
                outputMask &= ~mask;
                break;
            case OUTPUT:
                inputMask &= ~mask;
                outputMask |= mask;
                break;
            case BOTH:
                inputMask |= mask;
                outputMask |= mask;
                break;
            default:
                break;
        }
    }

    public EnergySideMode cycleSideMode(EnergySide side) {
        EnergySideMode next = getSideMode(side).next();
        setSideMode(side, next);
        return next;
    }

    public int getConnectedMask() {
        return connectedMask;
    }

    public String getLastFurnaceState() {
        return lastFurnaceState == null ? "" : lastFurnaceState;
    }

    public void setLastFurnaceState(String lastFurnaceState) {
        this.lastFurnaceState = lastFurnaceState == null ? "" : lastFurnaceState;
    }

    public String getLastCableState() {
        return lastCableState == null ? "" : lastCableState;
    }

    public void setLastCableState(String lastCableState) {
        this.lastCableState = lastCableState == null ? "" : lastCableState;
    }

    public long getFurnaceWorkingStartMs() {
        return furnaceWorkingStartMs;
    }

    public void setFurnaceWorkingStartMs(long furnaceWorkingStartMs) {
        this.furnaceWorkingStartMs = furnaceWorkingStartMs;
    }

    public boolean isFurnaceWorking() {
        return furnaceWorking;
    }

    public void setFurnaceWorking(boolean furnaceWorking) {
        this.furnaceWorking = furnaceWorking;
    }

    public String getLastWindState() {
        return lastWindState == null ? "" : lastWindState;
    }

    public void setLastWindState(String lastWindState) {
        this.lastWindState = lastWindState == null ? "" : lastWindState;
    }

    public long getLastWindAnimMs() {
        return lastWindAnimMs;
    }

    public void setLastWindAnimMs(long lastWindAnimMs) {
        this.lastWindAnimMs = lastWindAnimMs;
    }

    public long getNextSoundMs() {
        return nextSoundMs;
    }

    public void setNextSoundMs(long nextSoundMs) {
        this.nextSoundMs = nextSoundMs;
    }

    public void setConnectedMask(int connectedMask) {
        this.connectedMask = connectedMask & EnergySide.ALL_MASK;
    }

    public boolean isSideConnected(EnergySide side) {
        return (connectedMask & side.mask()) != 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public EnergyNodeStorage getStorage() {
        return getStorage(null);
    }

    public EnergyNodeStorage getStorage(Runnable onChanged) {
        if (storage == null) {
            storage = new EnergyNodeStorage(this, onChanged);
        } else {
            storage.setOnChanged(onChanged);
        }
        return storage;
    }

    @Override
    public Component<ChunkStore> clone() {
        EnergyNodeComponent copy = new EnergyNodeComponent();
        copy.nodeType = nodeType;
        copy.energy = energy;
        copy.capacity = capacity;
        copy.maxTransfer = maxTransfer;
        copy.generation = generation;
        copy.consumption = consumption;
        copy.furnaceBaseConsumption = furnaceBaseConsumption;
        copy.progress = progress;
        copy.progressMax = progressMax;
        copy.inputMask = inputMask;
        copy.outputMask = outputMask;
        copy.cableColor = cableColor;
        copy.cableTier = cableTier;
        copy.connectedMask = connectedMask;
        copy.lastCableState = lastCableState;
        copy.enabled = enabled;
        copy.solarTier = solarTier;
        copy.windTier = windTier;
        copy.nextSoundMs = nextSoundMs;
        return copy;
    }

    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }
}
