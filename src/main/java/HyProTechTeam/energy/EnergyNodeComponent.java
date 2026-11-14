package HyProTechTeam.energy;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Persistent energy-node settings and runtime state for one block entity. */
public class EnergyNodeComponent implements Component<ChunkStore> {
    /** Identifies how a node participates in generation, transfer, or consumption. */
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

    // Persist node configuration and energy; visual and storage caches are not encoded.
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
    // These fields are runtime caches and are not part of the persisted codec.
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

    /** Returns the role used to process this energy node. */
    public NodeType getNodeType() {
        return nodeType;
    }

    /** Checks the node role, accepting the legacy quarry role as a machine. */
    public boolean isMachineLike() {
        return isMachineLike(nodeType);
    }

    /** Recognizes both the current machine role and the legacy quarry role. */
    public static boolean isMachineLike(NodeType type) {
        return type == NodeType.MACHINE || type == NodeType.QUARRY;
    }

    /** Assigns the node's energy-network role. */
    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /** Returns the node's currently stored energy. */
    public int getEnergy() {
        return energy;
    }

    /** Stores the supplied energy value; this setter does not clamp it. */
    public void setEnergy(int energy) {
        this.energy = energy;
    }

    /** Returns the energy storage capacity. */
    public int getCapacity() {
        return capacity;
    }

    /** Sets the energy capacity without adjusting the stored energy. */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /** Returns the configured maximum transfer rate. */
    public int getMaxTransfer() {
        return maxTransfer;
    }

    /** Sets the maximum transfer rate. */
    public void setMaxTransfer(int maxTransfer) {
        this.maxTransfer = maxTransfer;
    }

    /** Returns the configured energy generation rate. */
    public int getGeneration() {
        return generation;
    }

    /** Sets the configured energy generation rate. */
    public void setGeneration(int generation) {
        this.generation = generation;
    }

    /** Returns the configured energy consumption rate. */
    public int getConsumption() {
        return consumption;
    }

    /** Sets the energy consumption rate. */
    public void setConsumption(int consumption) {
        this.consumption = consumption;
    }

    /** Returns the furnace's base consumption before other adjustments. */
    public int getFurnaceBaseConsumption() {
        return furnaceBaseConsumption;
    }

    /** Sets furnace base consumption, clamping negative values to zero. */
    public void setFurnaceBaseConsumption(int furnaceBaseConsumption) {
        this.furnaceBaseConsumption = Math.max(0, furnaceBaseConsumption);
    }

    /** Returns current machine processing progress. */
    public int getProgress() {
        return progress;
    }

    /** Sets the current machine processing progress. */
    public void setProgress(int progress) {
        this.progress = progress;
    }

    /** Returns the machine's progress target. */
    public int getProgressMax() {
        return progressMax;
    }

    /** Sets the machine's progress target. */
    public void setProgressMax(int progressMax) {
        this.progressMax = progressMax;
    }

    /** Returns the bit mask of input-enabled faces. */
    public int getInputMask() {
        return inputMask;
    }

    /** Sets input-enabled faces, discarding bits outside the six-face mask. */
    public void setInputMask(int inputMask) {
        this.inputMask = inputMask & EnergySide.ALL_MASK;
    }

    /** Returns the bit mask of output-enabled faces. */
    public int getOutputMask() {
        return outputMask;
    }

    /** Sets output-enabled faces, discarding bits outside the six-face mask. */
    public void setOutputMask(int outputMask) {
        this.outputMask = outputMask & EnergySide.ALL_MASK;
    }

    /** Returns the cable's connection color. */
    public int getCableColor() {
        return cableColor;
    }

    /** Sets the cable's connection color. */
    public void setCableColor(int cableColor) {
        this.cableColor = cableColor;
    }

    /** Returns the cable upgrade tier. */
    public int getCableTier() {
        return cableTier;
    }

    /** Stores the supplied cable tier without clamping. */
    public void setCableTier(int cableTier) {
        this.cableTier = cableTier;
    }

    /** Returns the solar generator's upgrade tier. */
    public int getSolarTier() {
        return solarTier;
    }

    /** Stores the solar tier after clamping it to the supported range. */
    public void setSolarTier(int solarTier) {
        this.solarTier = SolarUpgradeConfig.clampTier(solarTier);
    }

    /** Returns the wind generator's upgrade tier. */
    public int getWindTier() {
        return windTier;
    }

    /** Stores the wind tier after clamping it to the supported range. */
    public void setWindTier(int windTier) {
        this.windTier = WindUpgradeConfig.clampTier(windTier);
    }

    /** Checks the input mask for the given face. */
    public boolean allowsInput(EnergySide side) {
        return (inputMask & side.mask()) != 0;
    }

    /** Checks the output mask for the given face. */
    public boolean allowsOutput(EnergySide side) {
        return (outputMask & side.mask()) != 0;
    }

    /** Combines the face's input and output bits into one mode. */
    public EnergySideMode getSideMode(EnergySide side) {
        boolean input = allowsInput(side);
        boolean output = allowsOutput(side);
        return EnergySideMode.fromFlags(input, output);
    }

    /** Sets or clears this face's input and output bits for the requested mode. */
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

    /** Advances and stores one face's mode, then returns the new mode. */
    public EnergySideMode cycleSideMode(EnergySide side) {
        EnergySideMode next = getSideMode(side).next();
        setSideMode(side, next);
        return next;
    }

    /** Returns the transient mask of currently connected faces. */
    public int getConnectedMask() {
        return connectedMask;
    }

    /** Returns the cached furnace visual state, using an empty string for null. */
    public String getLastFurnaceState() {
        return lastFurnaceState == null ? "" : lastFurnaceState;
    }

    /** Caches the furnace visual state, replacing null with an empty string. */
    public void setLastFurnaceState(String lastFurnaceState) {
        this.lastFurnaceState = lastFurnaceState == null ? "" : lastFurnaceState;
    }

    /** Returns the cached cable visual state, using an empty string for null. */
    public String getLastCableState() {
        return lastCableState == null ? "" : lastCableState;
    }

    /** Caches the cable visual state, replacing null with an empty string. */
    public void setLastCableState(String lastCableState) {
        this.lastCableState = lastCableState == null ? "" : lastCableState;
    }

    /** Returns the cached time at which furnace work began. */
    public long getFurnaceWorkingStartMs() {
        return furnaceWorkingStartMs;
    }

    /** Updates the cached furnace work-start time. */
    public void setFurnaceWorkingStartMs(long furnaceWorkingStartMs) {
        this.furnaceWorkingStartMs = furnaceWorkingStartMs;
    }

    /** Returns the transient furnace working flag. */
    public boolean isFurnaceWorking() {
        return furnaceWorking;
    }

    /** Updates the transient furnace working flag. */
    public void setFurnaceWorking(boolean furnaceWorking) {
        this.furnaceWorking = furnaceWorking;
    }

    /** Returns the cached wind animation state, using an empty string for null. */
    public String getLastWindState() {
        return lastWindState == null ? "" : lastWindState;
    }

    /** Caches the wind animation state, replacing null with an empty string. */
    public void setLastWindState(String lastWindState) {
        this.lastWindState = lastWindState == null ? "" : lastWindState;
    }

    /** Returns the timestamp of the last wind animation update. */
    public long getLastWindAnimMs() {
        return lastWindAnimMs;
    }

    /** Updates the cached wind animation timestamp. */
    public void setLastWindAnimMs(long lastWindAnimMs) {
        this.lastWindAnimMs = lastWindAnimMs;
    }

    /** Returns the next scheduled sound playback time. */
    public long getNextSoundMs() {
        return nextSoundMs;
    }

    /** Stores the next scheduled sound playback time. */
    public void setNextSoundMs(long nextSoundMs) {
        this.nextSoundMs = nextSoundMs;
    }

    /** Stores connected faces while discarding bits outside the six-face mask. */
    public void setConnectedMask(int connectedMask) {
        this.connectedMask = connectedMask & EnergySide.ALL_MASK;
    }

    /** Tests whether the given face appears in the connection mask. */
    public boolean isSideConnected(EnergySide side) {
        return (connectedMask & side.mask()) != 0;
    }

    /** Returns whether the node is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Sets whether the node is enabled. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns the transactional storage adapter without a change callback. */
    public EnergyNodeStorage getStorage() {
        return getStorage(null);
    }

    /** Creates or reuses the storage adapter and updates its final-commit callback. */
    public EnergyNodeStorage getStorage(Runnable onChanged) {
        if (storage == null) {
            storage = new EnergyNodeStorage(this, onChanged);
        } else {
            storage.setOnChanged(onChanged);
        }
        return storage;
    }

    /** Copies the fields explicitly listed below into a new component instance. */
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

    /** Uses the same field copy for the serializable clone. */
    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }
}
