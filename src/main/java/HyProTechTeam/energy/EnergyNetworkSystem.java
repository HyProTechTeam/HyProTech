package HyProTechTeam.energy;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.furnace.FurnaceConfig;
import HyProTechTeam.sound.HyProTechSounds;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageLookup;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageUtil;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.doctorreborn.hytale.api.energy.v1.EnergyTransferer;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import HyProTechTeam.UpgradePersistence;

public class EnergyNetworkSystem extends EntityTickingSystem<ChunkStore> {
    private static final int BASIC_MACHINE_CAPACITY = 50000;
    private static final int BASIC_MACHINE_MAX_TRANSFER = 5000;
    private static final int FURNACE_CAPACITY = 5000;
    private static final int FURNACE_CONSUMPTION = 3;
    private static final int FURNACE_PROGRESS_MAX = 10000;
    private static final float ELECTRIC_FUEL_TIME = 1.0f;
    private static final boolean DEBUG_CABLE_UPGRADES = false;
    private static final double EXTERNAL_RATIO_EPSILON = 0.01;
    private static final int BATTERY_BASE_MASK = EnergySide.NORTH.mask() | EnergySide.SOUTH.mask();
    private static final int FURNACE_BASE_INPUT_MASK =
            EnergySide.EAST.mask() | EnergySide.UP.mask() | EnergySide.SOUTH.mask() | EnergySide.WEST.mask();
    private static final String[] CABLE_STATE_NAMES = buildCableStateNames();
    private static final String[] SOLAR_STATE_NAMES = buildTierStateNames("Solar_T", SolarUpgradeConfig.MAX_TIER);
    private static final String[] WIND_STATE_NAMES = buildTierStateNames("Wind_T", WindUpgradeConfig.MAX_TIER);
    private static final String[] FURNACE_STATE_NAMES = buildTierStateNames("Furnace_T", FurnaceConfig.MAX_TIER);
    private static volatile Field benchFuelTimeField;

    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private final Map<World, CableState> cableStates = new IdentityHashMap<>();

    public EnergyNetworkSystem(ComponentType<ChunkStore, EnergyNodeComponent> energyType) {
        this.energyType = energyType;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Archetype.of(WorldChunk.getComponentType(), BlockComponentChunk.getComponentType());
    }

    @Override
    public boolean isParallel(int total, int chunkSize) {
        return false;
    }

    @Override
    public void tick(
            float delta,
            int entityIndex,
            ArchetypeChunk<ChunkStore> chunk,
            Store<ChunkStore> store,
            CommandBuffer<ChunkStore> commandBuffer) {
        WorldChunk worldChunk = chunk.getComponent(entityIndex, WorldChunk.getComponentType());
        BlockComponentChunk blockComponents = chunk.getComponent(entityIndex, BlockComponentChunk.getComponentType());
        if (worldChunk == null || blockComponents == null) {
            return;
        }

        World world = worldChunk.getWorld();
        ChunkStore chunkStore = world == null ? null : world.getChunkStore();
        if (chunkStore == null) {
            return;
        }
        UpgradePersistence.cleanupExpired(world);

        CableState cableState = getCableState(world);
        long worldTick = world.getTick();
        if (worldTick != cableState.lastTick) {
            cableState.visitedByChunk.clear();
            cableState.lastTick = worldTick;
        }

        double sunlightFactor = 1.0;
        WorldTimeResource timeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (timeResource != null) {
            double baseFactor = timeResource.getSunlightFactor();
            sunlightFactor = SunlightUtil.adjustedSunlightFactor(world, timeResource, baseFactor);
        }

        int chunkX = worldChunk.getX();
        int chunkZ = worldChunk.getZ();
        IntArrayList invalidReferences = null;

        for (Int2ObjectMap.Entry<Holder<ChunkStore>> entry : blockComponents.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            Holder<ChunkStore> holder = entry.getValue();
            EnergyNodeComponent node = blockComponents.getComponent(blockIndex, energyType);
            if (node == null) {
                node = ensureFurnaceNode(
                        blockComponents, commandBuffer, world, chunkX, chunkZ, blockIndex, holder, null);
            }
            if (node != null) {
                processNode(blockComponents, chunkStore, world, chunkX, chunkZ, blockIndex, node, sunlightFactor, delta, cableState);
            }
        }

        for (Int2ReferenceMap.Entry<Ref<ChunkStore>> entry : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            int blockIndex = entry.getIntKey();
            Ref<ChunkStore> ref = entry.getValue();
            if (ref == null || !ref.isValid()) {
                if (invalidReferences == null) {
                    invalidReferences = new IntArrayList();
                }
                invalidReferences.add(blockIndex);
                continue;
            }
            EnergyNodeComponent node = blockComponents.getComponent(blockIndex, energyType);
            if (node == null) {
                node = ensureFurnaceNode(
                        blockComponents, commandBuffer, world, chunkX, chunkZ, blockIndex, null, ref);
            }
            if (node != null) {
                processNode(blockComponents, chunkStore, world, chunkX, chunkZ, blockIndex, node, sunlightFactor, delta, cableState);
            }
        }

        if (invalidReferences != null && !invalidReferences.isEmpty()) {
            boolean changed = false;
            for (int i = 0; i < invalidReferences.size(); i++) {
                int blockIndex = invalidReferences.getInt(i);
                Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
                if (ref != null && !ref.isValid()) {
                    blockComponents.removeEntityReference(blockIndex, ref);
                    disableTickingAt(world, chunkX, chunkZ, blockIndex);
                    changed = true;
                }
            }
            if (changed) {
                blockComponents.markNeedsSaving();
            }
        }
    }

    private void processNode(
            BlockComponentChunk blockComponents,
            ChunkStore chunkStore,
            World world,
            int chunkX,
            int chunkZ,
            int blockIndex,
            EnergyNodeComponent node,
            double sunlightFactor,
            float deltaSeconds,
            CableState cableState) {
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        int worldY = localY;

        if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            // Keep cable blocks out of vanilla ticking queues to avoid stale refs on break.
            disableTickingAt(world, chunkX, chunkZ, blockIndex);
        }

        boolean changed = UpgradePersistence.applyEnergyUpgrade(world, worldX, worldY, worldZ, node);
        if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            changed |= syncCableTierFromBlockId(world, worldX, worldY, worldZ, node);
        }
        double nodeSunlightFactor = sunlightFactor;
        if (node.getNodeType() == EnergyNodeComponent.NodeType.SOLAR
                && !SunlightUtil.hasSkyAccess(world, worldX, worldY, worldZ)) {
            nodeSunlightFactor = 0.0;
        }
        double nodeWindFactor = 0.0;
        if (node.getNodeType() == EnergyNodeComponent.NodeType.WIND) {
            nodeWindFactor = WindUtil.getWindFactor(world, worldX, worldY, worldZ);
        }
        changed |= tickNode(node, nodeSunlightFactor, nodeWindFactor, deltaSeconds);
        if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            changed |= syncCableBlockId(world, worldX, worldY, worldZ, node);
        }
        if (node.getNodeType() == EnergyNodeComponent.NodeType.SOLAR) {
            changed |= syncSolarOutputSide(world, worldX, worldY, worldZ, node);
            syncSolarState(world, worldX, worldY, worldZ, node);
            boolean generating = node.getGeneration() > 0 && nodeSunlightFactor > 0.0;
            HyProTechSounds.tickLoop(
                    world,
                    worldX,
                    worldY,
                    worldZ,
                    HyProTechSounds.EVENT_SOLAR_PANEL,
                    HyProTechSounds.FILE_SOLAR_PANEL,
                    HyProTechSounds.DEFAULT_LOOP_MS,
                    generating,
                    node);
        } else if (node.getNodeType() == EnergyNodeComponent.NodeType.WIND) {
            changed |= syncWindOutputSide(world, worldX, worldY, worldZ, node);
            syncWindState(world, worldX, worldY, worldZ, node, nodeWindFactor);
            boolean generating = node.getGeneration() > 0 && nodeWindFactor > 0.0;
            HyProTechSounds.tickLoop(
                    world,
                    worldX,
                    worldY,
                    worldZ,
                    HyProTechSounds.EVENT_WIND_TURBINE,
                    HyProTechSounds.FILE_WIND_TURBINE,
                    HyProTechSounds.DEFAULT_LOOP_MS,
                    generating,
                    node);
        } else if (node.getNodeType() == EnergyNodeComponent.NodeType.BATTERY) {
            changed |= syncBatterySides(world, worldX, worldY, worldZ, node);
        } else if (EnergyNodeComponent.isMachineLike(node.getNodeType())) {
            changed |= syncMachineSides(node);
        } else if (node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
            changed |= syncFurnaceSides(world, worldX, worldY, worldZ, node);
            changed |= syncFurnaceBench(world, chunkStore, worldX, worldY, worldZ, node, deltaSeconds);
        }
        if (changed) {
            blockComponents.markNeedsSaving();
        }

            if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                processCableNetwork(chunkStore, world, worldX, worldY, worldZ, deltaSeconds, cableState);
                return;
            }

            transferToNeighbors(blockComponents, chunkStore, world, worldX, worldY, worldZ, node, deltaSeconds);
    }

    private boolean tickNode(
            EnergyNodeComponent node,
            double sunlightFactor,
            double windFactor,
            float deltaSeconds) {
        if (deltaSeconds <= 0f) {
            return false;
        }

        boolean changed = ensureDefaults(node);
        int energy = node.getEnergy();
        EnergyNodeComponent.NodeType nodeType = node.getNodeType();

        if (nodeType == EnergyNodeComponent.NodeType.SOLAR) {
            int generated = (int) Math.round(node.getGeneration() * sunlightFactor * deltaSeconds);
            if (generated > 0 && energy < node.getCapacity()) {
                int add = Math.min(generated, node.getCapacity() - energy);
                node.setEnergy(energy + add);
                energy += add;
                changed = true;
            }
        } else if (nodeType == EnergyNodeComponent.NodeType.WIND) {
            int generated = (int) Math.round(node.getGeneration() * windFactor * deltaSeconds);
            if (generated > 0 && energy < node.getCapacity()) {
                int add = Math.min(generated, node.getCapacity() - energy);
                node.setEnergy(energy + add);
                energy += add;
                changed = true;
            }
        } else if (nodeType == EnergyNodeComponent.NodeType.FURNACE) {
            // Furnace progress is driven by the processing bench; energy use is handled separately.
        }

        if (energy > node.getCapacity()) {
            node.setEnergy(node.getCapacity());
            changed = true;
        }
        if (energy < 0) {
            node.setEnergy(0);
            changed = true;
        }

        return changed;
    }

    private boolean ensureDefaults(EnergyNodeComponent node) {
        boolean changed = false;
        EnergyNodeComponent.NodeType type = node.getNodeType();
        if (type == EnergyNodeComponent.NodeType.QUARRY) {
            node.setNodeType(EnergyNodeComponent.NodeType.MACHINE);
            changed = true;
            type = EnergyNodeComponent.NodeType.MACHINE;
        }
        if (type == EnergyNodeComponent.NodeType.CABLE) {
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            if (node.getCableTier() != tier) {
                node.setCableTier(tier);
                changed = true;
            }
            int capacity = CableUpgradeConfig.getEnergyCapacityForTier(tier);
            if (node.getCapacity() != capacity) {
                node.setCapacity(capacity);
                if (node.getEnergy() > capacity) {
                    node.setEnergy(capacity);
                }
                changed = true;
            }
            int maxTransfer = CableUpgradeConfig.getEnergyMaxTransferForTier(tier);
            if (node.getMaxTransfer() != maxTransfer) {
                node.setMaxTransfer(maxTransfer);
                changed = true;
            }
            if (node.getInputMask() == 0 && node.getOutputMask() == 0) {
                node.setInputMask(EnergySide.ALL_MASK);
                node.setOutputMask(EnergySide.ALL_MASK);
                changed = true;
            }
        } else if (type == EnergyNodeComponent.NodeType.SOLAR) {
            int tier = SolarUpgradeConfig.clampTier(node.getSolarTier());
            if (node.getSolarTier() != tier) {
                node.setSolarTier(tier);
                changed = true;
            }
            int capacity = SolarUpgradeConfig.getCapacityForTier(tier);
            if (node.getCapacity() != capacity) {
                node.setCapacity(capacity);
                if (node.getEnergy() > capacity) {
                    node.setEnergy(capacity);
                }
                changed = true;
            }
            int generation = SolarUpgradeConfig.getGenerationForTier(tier);
            if (node.getGeneration() != generation) {
                node.setGeneration(generation);
                changed = true;
            }
            int maxTransfer = SolarUpgradeConfig.getMaxTransferForTier(tier);
            if (node.getMaxTransfer() != maxTransfer) {
                node.setMaxTransfer(maxTransfer);
                changed = true;
            }
        } else if (type == EnergyNodeComponent.NodeType.WIND) {
            int tier = WindUpgradeConfig.clampTier(node.getWindTier());
            if (node.getWindTier() != tier) {
                node.setWindTier(tier);
                changed = true;
            }
            int capacity = WindUpgradeConfig.getCapacityForTier(tier);
            if (node.getCapacity() != capacity) {
                node.setCapacity(capacity);
                if (node.getEnergy() > capacity) {
                    node.setEnergy(capacity);
                }
                changed = true;
            }
            int generation = WindUpgradeConfig.getGenerationForTier(tier);
            if (node.getGeneration() != generation) {
                node.setGeneration(generation);
                changed = true;
            }
            int maxTransfer = WindUpgradeConfig.getMaxTransferForTier(tier);
            if (node.getMaxTransfer() != maxTransfer) {
                node.setMaxTransfer(maxTransfer);
                changed = true;
            }
        } else if (type == EnergyNodeComponent.NodeType.BATTERY) {
            if (node.getCapacity() <= 0) {
                node.setCapacity(BASIC_MACHINE_CAPACITY);
                changed = true;
            }
            if (node.getMaxTransfer() <= 0) {
                node.setMaxTransfer(BASIC_MACHINE_MAX_TRANSFER);
                changed = true;
            }
        } else if (EnergyNodeComponent.isMachineLike(type)) {
            if (node.getCapacity() <= 0) {
                node.setCapacity(BASIC_MACHINE_CAPACITY);
                changed = true;
            }
            if (node.getMaxTransfer() <= 0) {
                node.setMaxTransfer(BASIC_MACHINE_MAX_TRANSFER);
                changed = true;
            }
            if (node.getInputMask() == 0 && node.getOutputMask() == 0) {
                node.setInputMask(EnergySide.ALL_MASK);
                node.setOutputMask(EnergySide.ALL_MASK);
                changed = true;
            }
        } else if (type == EnergyNodeComponent.NodeType.FURNACE) {
            if (node.getCapacity() < FURNACE_CAPACITY) {
                node.setCapacity(FURNACE_CAPACITY);
                changed = true;
            }
            if (node.getMaxTransfer() != BASIC_MACHINE_MAX_TRANSFER) {
                node.setMaxTransfer(BASIC_MACHINE_MAX_TRANSFER);
                changed = true;
            }
            int baseConsumption = node.getFurnaceBaseConsumption();
            if (baseConsumption <= 0) {
                int fallback = node.getConsumption() > 0 ? node.getConsumption() : FURNACE_CONSUMPTION;
                node.setFurnaceBaseConsumption(fallback);
                baseConsumption = fallback;
                if (node.getConsumption() <= 0) {
                    node.setConsumption(fallback);
                }
                changed = true;
            }
            if (node.getConsumption() <= 0) {
                node.setConsumption(baseConsumption);
                changed = true;
            }
            if (node.getProgressMax() <= 0) {
                node.setProgressMax(FURNACE_PROGRESS_MAX);
                changed = true;
            }
            if (!node.isEnabled()) {
                node.setEnabled(true);
                changed = true;
            }
        }
        return changed;
    }

    private EnergyNodeComponent ensureFurnaceNode(
            BlockComponentChunk blockComponents,
            CommandBuffer<ChunkStore> commandBuffer,
            World world,
            int chunkX,
            int chunkZ,
            int blockIndex,
            Holder<ChunkStore> holder,
            Ref<ChunkStore> ref) {
        if (world == null || (holder == null && ref == null)) {
            return null;
        }

        BlockType blockType = getBlockTypeAt(world, chunkX, chunkZ, blockIndex);
        if (blockType == null || blockType.getId() == null) {
            return null;
        }
        if (!TieredIdUtil.isTieredId(blockType.getId(), HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            return null;
        }

        EnergyNodeComponent node = createFurnaceNode();
        if (holder != null) {
            holder.putComponent(energyType, node);
        } else if (commandBuffer != null && ref != null) {
            commandBuffer.putComponent(ref, energyType, node);
        } else {
            return null;
        }

        blockComponents.markNeedsSaving();
        return node;
    }


    private BlockType getBlockTypeAt(World world, int chunkX, int chunkZ, int blockIndex) {
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        int worldY = localY;

        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return null;
        }

        return accessor.getBlockType(worldX, worldY, worldZ);
    }

    private void disableTickingAt(World world, int chunkX, int chunkZ, int blockIndex) {
        if (world == null) {
            return;
        }
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }
        try {
            accessor.setTicking(worldX, localY, worldZ, false);
        } catch (Exception ignored) {
            // Best-effort only: invalid refs can remain in ticking queues after rapid break/place.
        }
    }

    private EnergyNodeComponent createFurnaceNode() {
        EnergyNodeComponent node = new EnergyNodeComponent();
        node.setNodeType(EnergyNodeComponent.NodeType.FURNACE);
        node.setEnergy(0);
        node.setCapacity(FURNACE_CAPACITY);
        node.setMaxTransfer(BASIC_MACHINE_MAX_TRANSFER);
        node.setConsumption(FURNACE_CONSUMPTION);
        node.setFurnaceBaseConsumption(FURNACE_CONSUMPTION);
        node.setProgress(0);
        node.setProgressMax(FURNACE_PROGRESS_MAX);
        node.setInputMask(FURNACE_BASE_INPUT_MASK);
        node.setOutputMask(FURNACE_BASE_INPUT_MASK);
        node.setEnabled(true);
        return node;
    }


    private void transferToNeighbors(
            BlockComponentChunk blockComponents,
            ChunkStore chunkStore,
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node,
            float deltaSeconds) {
        int connectedMask = 0;
        for (EnergySide side : EnergySide.VALUES) {
            int neighborX = worldX + side.dx();
            int neighborY = worldY + side.dy();
            int neighborZ = worldZ + side.dz();

            if (neighborY < ChunkUtil.MIN_Y || neighborY >= ChunkUtil.HEIGHT) {
                continue;
            }

            long neighborChunkIndex = ChunkUtil.indexChunkFromBlock(neighborX, neighborZ);
            BlockComponentChunk neighborComponents =
                    chunkStore.getChunkComponent(neighborChunkIndex, BlockComponentChunk.getComponentType());
            if (neighborComponents == null) {
                continue;
            }

            int localX = ChunkUtil.localCoordinate((long) neighborX);
            int localZ = ChunkUtil.localCoordinate((long) neighborZ);
            int neighborIndex = ChunkUtil.indexBlockInColumn(localX, neighborY, localZ);
            EnergyNodeComponent neighbor = neighborComponents.getComponent(neighborIndex, energyType);
            if (neighbor == null) {
                EnergyStorage external = EnergyStorageLookup.find(world, neighborX, neighborY, neighborZ);
                if (external == null) {
                    continue;
                }

                boolean inputAllowed = allowsInput(node, side) && external.supportsExtraction();
                boolean outputAllowed = allowsOutput(node, side) && external.supportsInsertion();
                if (!inputAllowed && !outputAllowed) {
                    continue;
                }
                connectedMask |= side.mask();

                if (transferEnergyExternal(blockComponents, node, side, external, deltaSeconds)) {
                    blockComponents.markNeedsSaving();
                }
                continue;
            }

            EnergySide neighborSide = side.opposite();
            if (isNeighborConnectable(node, neighbor, side, neighborSide)) {
                connectedMask |= side.mask();
            }

            if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                    || neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                continue;
            }

            if (!shouldTransfer(worldX, worldY, worldZ, neighborX, neighborY, neighborZ)) {
                continue;
            }

            if (transferEnergy(node, side, neighbor, neighborSide, deltaSeconds)) {
                blockComponents.markNeedsSaving();
                neighborComponents.markNeedsSaving();
            }
        }

        if (node.getConnectedMask() != connectedMask) {
            node.setConnectedMask(connectedMask);
        }
    }

    private void processCableNetwork(
            ChunkStore chunkStore,
            World world,
            int worldX,
            int worldY,
            int worldZ,
            float deltaSeconds,
            CableState cableState) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        int localX = ChunkUtil.localCoordinate((long) worldX);
        int localZ = ChunkUtil.localCoordinate((long) worldZ);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, worldY, localZ);

        if (!markVisited(cableState, chunkIndex, blockIndex)) {
            return;
        }

        CableNetwork network = buildCableNetwork(chunkStore, world, worldX, worldY, worldZ, cableState);
        if (network.cables.isEmpty()) {
            return;
        }

        updateCableConnectedMasks(network);
        applyNeighborMaxTransfers(network);
        updateCableStates(world, network);
        resetExternalBudgets(network, deltaSeconds);
        applyExternalTransfers(network, true);
        applyCableTransfers(network, deltaSeconds);
        applyExternalTransfers(network, false);
        distributeCableEnergy(network);
    }

    private CableNetwork buildCableNetwork(
            ChunkStore chunkStore,
            World world,
            int startX,
            int startY,
            int startZ,
            CableState cableState) {
        CableNetwork network = new CableNetwork();
        Long2ObjectMap<Int2ObjectMap<NeighborNode>> neighborsByChunk = new Long2ObjectOpenHashMap<>();
        Long2ObjectMap<Int2ObjectMap<ExternalNeighbor>> externalsByChunk = new Long2ObjectOpenHashMap<>();
        Deque<CablePos> queue = new ArrayDeque<>();
        queue.add(new CablePos(startX, startY, startZ));

        while (!queue.isEmpty()) {
            CablePos pos = queue.removeFirst();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            BlockComponentChunk components =
                    chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (components == null) {
                continue;
            }

            int localX = ChunkUtil.localCoordinate((long) pos.x);
            int localZ = ChunkUtil.localCoordinate((long) pos.z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.y, localZ);
            EnergyNodeComponent node = components.getComponent(blockIndex, energyType);
            if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.CABLE) {
                continue;
            }

            network.addCable(node, components, pos.x, pos.y, pos.z, chunkIndex);
            CableNode cableNode = network.cables.get(network.cables.size() - 1);
            int cableMaxTransfer = node.getMaxTransfer();

            for (EnergySide side : EnergySide.VALUES) {
                int neighborX = pos.x + side.dx();
                int neighborY = pos.y + side.dy();
                int neighborZ = pos.z + side.dz();

                if (neighborY < ChunkUtil.MIN_Y || neighborY >= ChunkUtil.HEIGHT) {
                    continue;
                }

                long neighborChunkIndex = ChunkUtil.indexChunkFromBlock(neighborX, neighborZ);
                BlockComponentChunk neighborComponents =
                        chunkStore.getChunkComponent(neighborChunkIndex, BlockComponentChunk.getComponentType());
                if (neighborComponents == null) {
                    continue;
                }

                int neighborLocalX = ChunkUtil.localCoordinate((long) neighborX);
                int neighborLocalZ = ChunkUtil.localCoordinate((long) neighborZ);
                int neighborIndex = ChunkUtil.indexBlockInColumn(neighborLocalX, neighborY, neighborLocalZ);
                EnergyNodeComponent neighbor = neighborComponents.getComponent(neighborIndex, energyType);
                if (neighbor == null) {
                    EnergyStorage external = EnergyStorageLookup.find(world, neighborX, neighborY, neighborZ);
                    if (external == null) {
                        continue;
                    }
                    if (!canCableConnect(node, side)) {
                        continue;
                    }

                    boolean inputAllowed = allowsInput(node, side) && external.supportsExtraction();
                    boolean outputAllowed = allowsOutput(node, side) && external.supportsInsertion();
                    if (!inputAllowed && !outputAllowed) {
                        continue;
                    }
                    cableNode.connectedMask |= side.mask();

                    Int2ObjectMap<ExternalNeighbor> chunkExternals = externalsByChunk.get(neighborChunkIndex);
                    if (chunkExternals == null) {
                        chunkExternals = new Int2ObjectOpenHashMap<>();
                        externalsByChunk.put(neighborChunkIndex, chunkExternals);
                    }
                    ExternalNeighbor ext = chunkExternals.get(neighborIndex);
                    if (ext == null) {
                        ext = new ExternalNeighbor(external, getExternalMaxTransfer(external));
                        chunkExternals.put(neighborIndex, ext);
                    }
                    if (inputAllowed) {
                        ext.inputTransfer += cableMaxTransfer;
                    }
                    if (outputAllowed) {
                        ext.outputTransfer += cableMaxTransfer;
                    }
                    continue;
                }

                if (!canCableConnect(node, side)) {
                    continue;
                }

                EnergySide neighborSide = side.opposite();
                if (neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                    if (!sameCableColor(node, neighbor)) {
                        continue;
                    }
                    if (!canCableConnect(neighbor, neighborSide)) {
                        continue;
                    }
                    cableNode.connectedMask |= side.mask();
                    cableNode.cableMask |= side.mask();
                    if (markVisited(cableState, neighborChunkIndex, neighborIndex)) {
                        queue.add(new CablePos(neighborX, neighborY, neighborZ));
                    }
                } else {
                    Int2ObjectMap<NeighborNode> chunkNeighbors = neighborsByChunk.get(neighborChunkIndex);
                    if (chunkNeighbors == null) {
                        chunkNeighbors = new Int2ObjectOpenHashMap<>();
                        neighborsByChunk.put(neighborChunkIndex, chunkNeighbors);
                    }
                    NeighborNode neighborNode = chunkNeighbors.get(neighborIndex);
                    if (neighborNode == null) {
                        neighborNode = new NeighborNode(neighbor, neighborComponents);
                        chunkNeighbors.put(neighborIndex, neighborNode);
                    }

                    boolean inputAllowed = allowsInput(node, side) && allowsOutput(neighbor, neighborSide);
                    boolean outputAllowed = allowsOutput(node, side) && allowsInput(neighbor, neighborSide);
                    if (!inputAllowed && !outputAllowed) {
                        continue;
                    }
                    cableNode.connectedMask |= side.mask();

                    if (inputAllowed) {
                        neighborNode.inputTransfer += cableMaxTransfer;
                    }
                    if (outputAllowed) {
                        neighborNode.outputTransfer += cableMaxTransfer;
                    }
                }
            }
        }

        for (Int2ObjectMap<NeighborNode> chunkNeighbors : neighborsByChunk.values()) {
            network.neighbors.addAll(chunkNeighbors.values());
        }
        for (Int2ObjectMap<ExternalNeighbor> chunkExternals : externalsByChunk.values()) {
            network.externals.addAll(chunkExternals.values());
        }

        return network;
    }

    private void applyCableTransfers(CableNetwork network, float deltaSeconds) {
        if (network.totalCapacity <= 0) {
            network.totalEnergy = 0;
            return;
        }

        int outputBudgetValue = getOutputBudget(network, deltaSeconds);
        TransferBudget outputBudget = new TransferBudget(outputBudgetValue);

        List<NeighborNode> solarNodes = new ArrayList<>();
        List<NeighborNode> activeFurnaces = new ArrayList<>();
        List<NeighborNode> idleFurnaces = new ArrayList<>();
        List<NeighborNode> batteries = new ArrayList<>();

        for (NeighborNode neighbor : network.neighbors) {
            EnergyNodeComponent node = neighbor.node;
            if (node.getNodeType() == EnergyNodeComponent.NodeType.SOLAR
                    || node.getNodeType() == EnergyNodeComponent.NodeType.WIND) {
                solarNodes.add(neighbor);
            } else if (node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
                boolean active = isFurnaceActive(node);
                if (active) {
                    activeFurnaces.add(neighbor);
                } else {
                    idleFurnaces.add(neighbor);
                }
            } else if (node.getNodeType() == EnergyNodeComponent.NodeType.BATTERY
                    || EnergyNodeComponent.isMachineLike(node.getNodeType())) {
                batteries.add(neighbor);
            }
        }

        for (NeighborNode neighbor : solarNodes) {
            transferNodeToNetwork(network, neighbor, deltaSeconds, Integer.MAX_VALUE);
        }

        List<NeighborNode> activeConsumers = new ArrayList<>(activeFurnaces);
        int activeDemand = computeActiveDemand(activeConsumers, deltaSeconds, outputBudgetValue);
        boolean dischargedToFill = false;
        if (activeDemand > network.totalEnergy) {
            int needed = activeDemand - network.totalEnergy;
            for (NeighborNode neighbor : batteries) {
                if (needed <= 0) {
                    break;
                }
                int moved = transferNodeToNetwork(network, neighbor, deltaSeconds, needed);
                needed -= moved;
            }
        }
        for (NeighborNode neighbor : activeConsumers) {
            transferNetworkToNode(network, neighbor, deltaSeconds, outputBudget);
        }

        int remainingCapacity = network.totalCapacity - network.totalEnergy;
        if (remainingCapacity > 0) {
            for (NeighborNode neighbor : batteries) {
                if (remainingCapacity <= 0) {
                    break;
                }
                int moved = transferNodeToNetwork(network, neighbor, deltaSeconds, remainingCapacity);
                if (moved > 0) {
                    dischargedToFill = true;
                }
                remainingCapacity -= moved;
            }
        }

        if (!dischargedToFill) {
            for (NeighborNode neighbor : batteries) {
                transferNetworkToNode(network, neighbor, deltaSeconds, outputBudget);
            }
        }
        for (NeighborNode neighbor : idleFurnaces) {
            transferNetworkToNode(network, neighbor, deltaSeconds, outputBudget);
        }

        if (network.totalEnergy < 0) {
            network.totalEnergy = 0;
        } else if (network.totalEnergy > network.totalCapacity) {
            network.totalEnergy = network.totalCapacity;
        }
    }

    private void resetExternalBudgets(CableNetwork network, float deltaSeconds) {
        if (network.externals.isEmpty()) {
            return;
        }
        for (ExternalNeighbor ext : network.externals) {
            ext.inputBudget = computeExternalBudget(ext.inputTransfer, ext.maxTransfer, deltaSeconds);
            ext.outputBudget = computeExternalBudget(ext.outputTransfer, ext.maxTransfer, deltaSeconds);
        }
    }

    private void applyExternalTransfers(CableNetwork network, boolean pull) {
        if (network.externals.isEmpty() || network.totalCapacity <= 0) {
            return;
        }

        double networkRatio = energyRatio(network.totalEnergy, network.totalCapacity);
        for (ExternalNeighbor ext : network.externals) {
            EnergyStorage storage = ext.storage;
            if (storage == null) {
                continue;
            }

            if (pull) {
                if (ext.inputBudget <= 0 || !storage.supportsExtraction()) {
                    continue;
                }
            } else {
                if (ext.outputBudget <= 0 || !storage.supportsInsertion()) {
                    continue;
                }
            }

            EnergyStorageSnapshot snapshot = snapshotStorage(storage);
            double storageRatio = energyRatio(snapshot.amount, snapshot.capacity);
            if (pull) {
                if (storageRatio <= networkRatio + EXTERNAL_RATIO_EPSILON) {
                    continue;
                }
                int maxAmount = Math.min(ext.inputBudget, network.totalCapacity - network.totalEnergy);
                if (maxAmount <= 0) {
                    continue;
                }
                long moved = extractFromStorage(storage, maxAmount);
                if (moved > 0) {
                    network.totalEnergy += moved;
                    ext.inputBudget -= (int) moved;
                    networkRatio = energyRatio(network.totalEnergy, network.totalCapacity);
                }
            } else {
                if (storageRatio >= networkRatio - EXTERNAL_RATIO_EPSILON) {
                    continue;
                }
                int maxAmount = Math.min(ext.outputBudget, network.totalEnergy);
                if (maxAmount <= 0) {
                    continue;
                }
                long moved = insertIntoStorage(storage, maxAmount);
                if (moved > 0) {
                    network.totalEnergy -= moved;
                    ext.outputBudget -= (int) moved;
                    networkRatio = energyRatio(network.totalEnergy, network.totalCapacity);
                }
            }
        }

        if (network.totalEnergy < 0) {
            network.totalEnergy = 0;
        } else if (network.totalEnergy > network.totalCapacity) {
            network.totalEnergy = network.totalCapacity;
        }
    }

    private int computeExternalBudget(int cableTransfer, int maxTransfer, float deltaSeconds) {
        if (deltaSeconds <= 0f) {
            return 0;
        }
        int transferPerSecond = Math.min(cableTransfer, maxTransfer);
        if (transferPerSecond <= 0) {
            return 0;
        }
        int limit = (int) Math.round(transferPerSecond * deltaSeconds);
        return Math.max(0, limit);
    }

    private long extractFromStorage(EnergyStorage storage, int maxAmount) {
        if (storage == null || maxAmount <= 0) {
            return 0;
        }
        TransactionContext context = TransactionContext.current();
        try (Transaction transaction = Transaction.openNested(context)) {
            long extracted = storage.extract(maxAmount, transaction);
            if (extracted > 0) {
                transaction.commit();
            }
            return extracted;
        }
    }

    private long insertIntoStorage(EnergyStorage storage, int maxAmount) {
        if (storage == null || maxAmount <= 0) {
            return 0;
        }
        TransactionContext context = TransactionContext.current();
        try (Transaction transaction = Transaction.openNested(context)) {
            long inserted = storage.insert(maxAmount, transaction);
            if (inserted > 0) {
                transaction.commit();
            }
            return inserted;
        }
    }

    private EnergyStorageSnapshot snapshotStorage(EnergyStorage storage) {
        long amount = 0;
        long capacity = 0;
        for (EnergyStorageView view : storage) {
            if (view == null) {
                continue;
            }
            amount += Math.max(0, view.getAmount());
            capacity += Math.max(0, view.getCapacity());
        }
        return new EnergyStorageSnapshot(amount, capacity);
    }

    private int getExternalMaxTransfer(EnergyStorage storage) {
        if (storage instanceof EnergyTransferer transferer) {
            long rate = transferer.getTransferRate();
            if (rate <= 0) {
                return 0;
            }
            return rate >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rate;
        }
        return Integer.MAX_VALUE;
    }

    private int computeActiveDemand(List<NeighborNode> activeFurnaces, float deltaSeconds, int maxDemand) {
        if (deltaSeconds <= 0f) {
            return 0;
        }

        int demand = 0;
        for (NeighborNode neighbor : activeFurnaces) {
            EnergyNodeComponent node = neighbor.node;
            int remaining = node.getCapacity() - node.getEnergy();
            if (remaining <= 0) {
                continue;
            }
            int transferPerSecond = Math.min(neighbor.outputTransfer, node.getMaxTransfer());
            if (transferPerSecond <= 0) {
                continue;
            }
            int transferLimit = (int) Math.round(transferPerSecond * deltaSeconds);
            if (transferLimit <= 0) {
                continue;
            }
            demand += Math.min(transferLimit, remaining);
            if (maxDemand > 0 && demand >= maxDemand) {
                return maxDemand;
            }
        }
        if (maxDemand > 0) {
            return Math.min(demand, maxDemand);
        }
        return demand;
    }

    private int transferNodeToNetwork(
            CableNetwork network,
            NeighborNode neighbor,
            float deltaSeconds,
            int maxAmount) {
        if (deltaSeconds <= 0f) {
            return 0;
        }

        EnergyNodeComponent node = neighbor.node;
        int available = node.getEnergy();
        if (available <= 0) {
            return 0;
        }

        int remainingCapacity = network.totalCapacity - network.totalEnergy;
        if (remainingCapacity <= 0) {
            return 0;
        }

        int transferPerSecond = Math.min(neighbor.inputTransfer, node.getMaxTransfer());
        if (transferPerSecond <= 0) {
            return 0;
        }

        int transferLimit = (int) Math.round(transferPerSecond * deltaSeconds);
        if (transferLimit <= 0) {
            return 0;
        }

        int amount = Math.min(transferLimit, available);
        amount = Math.min(amount, remainingCapacity);
        amount = Math.min(amount, Math.max(0, maxAmount));
        if (amount <= 0) {
            return 0;
        }

        node.setEnergy(available - amount);
        neighbor.components.markNeedsSaving();
        network.totalEnergy += amount;
        return amount;
    }

    private void transferNetworkToNode(
            CableNetwork network,
            NeighborNode neighbor,
            float deltaSeconds,
            TransferBudget outputBudget) {
        if (deltaSeconds <= 0f) {
            return;
        }

        if (network.totalEnergy <= 0) {
            return;
        }

        if (outputBudget.remaining <= 0) {
            return;
        }

        EnergyNodeComponent node = neighbor.node;
        int demand = node.getCapacity() - node.getEnergy();
        if (demand <= 0) {
            return;
        }

        int transferPerSecond = Math.min(neighbor.outputTransfer, node.getMaxTransfer());
        if (transferPerSecond <= 0) {
            return;
        }

        int transferLimit = (int) Math.round(transferPerSecond * deltaSeconds);
        if (transferLimit <= 0) {
            return;
        }

        int amount = Math.min(transferLimit, network.totalEnergy);
        amount = Math.min(amount, demand);
        amount = Math.min(amount, outputBudget.remaining);
        if (amount <= 0) {
            return;
        }

        node.setEnergy(node.getEnergy() + amount);
        neighbor.components.markNeedsSaving();
        network.totalEnergy -= amount;
        outputBudget.remaining -= amount;
    }

    private int getOutputBudget(CableNetwork network, float deltaSeconds) {
        if (deltaSeconds <= 0f || network.maxTransfer <= 0) {
            return 0;
        }
        int budget = (int) Math.round(network.maxTransfer * deltaSeconds);
        return Math.max(0, budget);
    }

    private void distributeCableEnergy(CableNetwork network) {
        int cableCount = network.cables.size();
        if (cableCount == 0) {
            return;
        }

        int totalCapacity = network.totalCapacity;
        if (totalCapacity <= 0) {
            for (CableNode cable : network.cables) {
                if (cable.node.getEnergy() != 0) {
                    cable.node.setEnergy(0);
                    cable.components.markNeedsSaving();
                }
            }
            network.totalEnergy = 0;
            return;
        }

        int energy = network.totalEnergy;
        if (energy < 0) {
            energy = 0;
        } else if (energy > totalCapacity) {
            energy = totalCapacity;
        }

        double ratio = (double) energy / totalCapacity;
        int[] allocation = new int[cableCount];
        int allocated = 0;
        for (int i = 0; i < cableCount; i++) {
            CableNode cable = network.cables.get(i);
            int target = (int) Math.floor(cable.capacity * ratio);
            if (target > cable.capacity) {
                target = cable.capacity;
            }
            allocation[i] = target;
            allocated += target;
        }

        int remainder = energy - allocated;
        int index = 0;
        while (remainder > 0 && cableCount > 0) {
            CableNode cable = network.cables.get(index % cableCount);
            if (allocation[index % cableCount] < cable.capacity) {
                allocation[index % cableCount] += 1;
                remainder--;
            }
            index++;
            if (index > cableCount * 2 && remainder > 0) {
                break;
            }
        }

        while (remainder < 0 && cableCount > 0) {
            CableNode cable = network.cables.get(index % cableCount);
            if (allocation[index % cableCount] > 0) {
                allocation[index % cableCount] -= 1;
                remainder++;
            }
            index++;
            if (index > cableCount * 2 && remainder < 0) {
                break;
            }
        }

        for (int i = 0; i < cableCount; i++) {
            CableNode cable = network.cables.get(i);
            int target = allocation[i];
            if (cable.node.getEnergy() != target) {
                cable.node.setEnergy(target);
                cable.components.markNeedsSaving();
            }
        }

        network.totalEnergy = energy;
    }

    private void updateCableConnectedMasks(CableNetwork network) {
        for (CableNode cable : network.cables) {
            if (cable.node.getConnectedMask() != cable.connectedMask) {
                cable.node.setConnectedMask(cable.connectedMask);
            }
        }
    }

    private void updateCableStates(World world, CableNetwork network) {
        for (CableNode cable : network.cables) {
            BlockAccessor accessor = world.getChunkIfLoaded(cable.chunkIndex);
            if (accessor == null) {
                continue;
            }

            BlockType blockType = accessor.getBlockType(cable.x, cable.y, cable.z);
            if (blockType == null) {
                continue;
            }

            EnergyNodeComponent node = cable.node;
            int visualMask = node.getConnectedMask() & (node.getInputMask() | node.getOutputMask());
            RotationTuple rotation = RotationTuple.get(
                    world.getBlockRotationIndex(cable.x, cable.y, cable.z));
            int localBaseMask = rotateMaskToLocal(visualMask, rotation);
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            String stateName = cableStateName(localBaseMask, tier);
            if (stateName.equals(node.getLastCableState())) {
                continue;
            }

            try {
                accessor.setBlockInteractionState(cable.x, cable.y, cable.z, blockType, stateName, false);
                node.setLastCableState(stateName);
            } catch (Exception e) {
                // aspo?? jednou zaloguj, a?? m???? d??kaz
                System.out.println("[HyProTech] Cable state '" + stateName + "' not found for blockType=" + blockType.getId());
                node.setLastCableState("");
            }
        }
    }

    private int rotateMaskToLocal(int mask, RotationTuple rotation) {
        int normalized = mask & EnergySide.ALL_MASK;
        if (rotation == null) {
            return normalized;
        }

        Rotation yaw = rotation.yaw() == null ? Rotation.None : rotation.yaw();
        Rotation pitch = rotation.pitch() == null ? Rotation.None : rotation.pitch();
        Rotation roll = rotation.roll() == null ? Rotation.None : rotation.roll();
        if (yaw == Rotation.None && pitch == Rotation.None && roll == Rotation.None) {
            return normalized;
        }

        int localMask = 0;
        for (EnergySide localSide : EnergySide.VALUES) {
            Vector3i local = new Vector3i(localSide.dx(), localSide.dy(), localSide.dz());
            Vector3i world = Rotation.rotate(local, yaw, pitch, roll);
            EnergySide worldSide = EnergySide.fromDelta(world.getX(), world.getY(), world.getZ());
            if (worldSide != null && (normalized & worldSide.mask()) != 0) {
                localMask |= localSide.mask();
            }
        }
        return localMask;
    }

    private void applyNeighborMaxTransfers(CableNetwork network) {
        for (NeighborNode neighbor : network.neighbors) {
            int cableTransfer = Math.max(neighbor.inputTransfer, neighbor.outputTransfer);
            if (cableTransfer <= 0) {
                continue;
            }
            if (neighbor.node.getMaxTransfer() != cableTransfer) {
                neighbor.node.setMaxTransfer(cableTransfer);
                neighbor.components.markNeedsSaving();
            }
        }
    }

    private boolean syncSolarOutputSide(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        EnergySide outputSide = getSolarOutputSide(world, worldX, worldY, worldZ);
        int outputMask = outputSide.mask() | outputSide.opposite().mask();
        int inputMask = outputMask;
        boolean changed = false;
        if (node.getOutputMask() != outputMask) {
            node.setOutputMask(outputMask);
            changed = true;
        }
        if (node.getInputMask() != inputMask) {
            node.setInputMask(inputMask);
            changed = true;
        }
        return changed;
    }

    private boolean syncWindOutputSide(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        int outputMask = EnergySide.ALL_MASK;
        int inputMask = outputMask;
        boolean changed = false;
        if (node.getOutputMask() != outputMask) {
            node.setOutputMask(outputMask);
            changed = true;
        }
        if (node.getInputMask() != inputMask) {
            node.setInputMask(inputMask);
            changed = true;
        }
        return changed;
    }

    private boolean syncBatterySides(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        Rotation yaw = getBlockYaw(world, worldX, worldY, worldZ);
        int allowedMask = rotateMaskByYaw(BATTERY_BASE_MASK, yaw);

        int inputMask = node.getInputMask();
        int outputMask = node.getOutputMask();
        int clampedInput = inputMask & allowedMask;
        int clampedOutput = outputMask & allowedMask;
        boolean changed = false;

        if (clampedInput != inputMask || clampedOutput != outputMask) {
            int rotatedInput = rotateMaskByYaw(inputMask, yaw);
            int rotatedOutput = rotateMaskByYaw(outputMask, yaw);
            if (((rotatedInput | rotatedOutput) & allowedMask) != 0) {
                clampedInput = rotatedInput & allowedMask;
                clampedOutput = rotatedOutput & allowedMask;
            }
            changed = true;
        }

        if (node.getInputMask() != clampedInput) {
            node.setInputMask(clampedInput);
            changed = true;
        }
        if (node.getOutputMask() != clampedOutput) {
            node.setOutputMask(clampedOutput);
            changed = true;
        }
        return changed;
    }

    private boolean syncFurnaceSides(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        Rotation yaw = getBlockYaw(world, worldX, worldY, worldZ);
        int inputMask = rotateMaskByYaw(FURNACE_BASE_INPUT_MASK, yaw);
        int outputMask = inputMask;
        boolean changed = false;

        if (node.getInputMask() != inputMask) {
            node.setInputMask(inputMask);
            changed = true;
        }
        if (node.getOutputMask() != outputMask) {
            node.setOutputMask(outputMask);
            changed = true;
        }
        return changed;
    }

    private boolean syncFurnaceBench(
            World world,
            ChunkStore chunkStore,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node,
            float deltaSeconds) {
        ProcessingBenchBlock benchState = getProcessingBenchState(world, worldX, worldY, worldZ);
        BenchBlock benchBlock = getBenchBlock(world, worldX, worldY, worldZ);
        if (benchState == null || benchBlock == null || deltaSeconds <= 0f) {
            HyProTechSounds.tickLoop(
                    world,
                    worldX,
                    worldY,
                    worldZ,
                    HyProTechSounds.EVENT_ELECTRIC_FURNACE,
                    HyProTechSounds.FILE_ELECTRIC_FURNACE,
                    HyProTechSounds.DEFAULT_LOOP_MS,
                    false,
                    node);
            return false;
        }

        boolean changed = false;
        int energy = node.getEnergy();
        int baseConsumption = node.getFurnaceBaseConsumption();
        if (baseConsumption <= 0) {
            baseConsumption = node.getConsumption() > 0 ? node.getConsumption() : FURNACE_CONSUMPTION;
            node.setFurnaceBaseConsumption(baseConsumption);
            changed = true;
        }

        int rawTier = benchBlock.getTierLevel();
        int tier = FurnaceConfig.clampTier(rawTier);
        int overrideTier = getFurnaceTierOverride(world, worldX, worldY, worldZ);
        if (overrideTier > tier) {
            benchBlock.setTierLevel(overrideTier);
            tier = overrideTier;
            changed = true;
        } else if (rawTier != tier) {
            benchBlock.setTierLevel(tier);
            changed = true;
        }

        int scaledConsumption = FurnaceConfig.computeConsumption(baseConsumption, tier);
        if (node.getConsumption() != scaledConsumption) {
            node.setConsumption(scaledConsumption);
            changed = true;
        }

        int consumption = (int) Math.round(scaledConsumption * deltaSeconds);
        if (scaledConsumption > 0 && consumption < 1) {
            consumption = 1;
        }

        boolean hasRecipe = benchState.getRecipe() != null;
        boolean shouldBeActive = node.isEnabled() && hasRecipe && consumption > 0 && energy >= consumption;
        long now = System.currentTimeMillis();
        if (shouldBeActive && node != null) {
            if (!node.isFurnaceWorking()) {
                node.setFurnaceWorking(true);
                node.setFurnaceWorkingStartMs(now);
            }
            long startMs = node.getFurnaceWorkingStartMs();
            if (startMs > 0L && (now - startMs) < 1000L) {
                // Delay actual processing while the close animation plays.
                shouldBeActive = false;
            }
        } else if (!shouldBeActive && node != null && node.isFurnaceWorking() && !hasRecipe) {
            node.setFurnaceWorking(false);
            node.setFurnaceWorkingStartMs(0L);
        }
        boolean usesFuel = false;
        ProcessingBench bench = benchState.getBench() instanceof ProcessingBench
                ? (ProcessingBench) benchState.getBench()
                : null;
        if (bench != null && bench.getFuel() != null && bench.getFuel().length > 0) {
            usesFuel = true;
        }

        if (benchState.isActive() != shouldBeActive) {
            benchState.setActive(shouldBeActive, benchBlock, null);
            changed = true;
        }
        if (shouldBeActive) {
            node.setEnergy(energy - consumption);
            energy -= consumption;
            changed = true;
        }
        if (usesFuel) {
            boolean fuelChanged = setBenchFuelTime(benchState, shouldBeActive ? ELECTRIC_FUEL_TIME : 0f);
            if (fuelChanged) {
                benchState.updateFuelValues(benchBlock.getWindows());
                changed = true;
            }
        } else if (!shouldBeActive) {
            if (setBenchInputProgress(benchState, 0f)) {
                changed = true;
            }
        }

        float inputProgress = benchState.getInputProgress();
        if (!usesFuel && !shouldBeActive && inputProgress != 0f) {
            if (setBenchInputProgress(benchState, 0f)) {
                changed = true;
            }
            inputProgress = 0f;
        }

        int progressMax = node.getProgressMax();
        if (progressMax > 0) {
            float progressRatio = 0f;
            if (hasRecipe) {
                float recipeTime = benchState.getRecipe().getTimeSeconds();
                if (recipeTime > 0f) {
                    progressRatio = Math.max(0f, Math.min(1f, inputProgress / recipeTime));
                }
            }
            int progress = (int) Math.round(progressRatio * progressMax);
            if (progress < 0) {
                progress = 0;
            } else if (progress > progressMax) {
                progress = progressMax;
            }
            if (node.getProgress() != progress) {
                node.setProgress(progress);
                changed = true;
            }
        }

        syncFurnaceState(world, worldX, worldY, worldZ, Math.max(0, tier - 1), benchState, node);
        HyProTechSounds.tickLoop(
                world,
                worldX,
                worldY,
                worldZ,
                HyProTechSounds.EVENT_ELECTRIC_FURNACE,
                HyProTechSounds.FILE_ELECTRIC_FURNACE,
                HyProTechSounds.DEFAULT_LOOP_MS,
                node != null && node.isFurnaceWorking(),
                node);
        syncFurnaceBlockId(world, worldX, worldY, worldZ, tier, benchState, node);
        return changed;
    }

    private boolean syncMachineSides(EnergyNodeComponent node) {
        int outputMask = EnergySide.ALL_MASK;
        int inputMask = outputMask;
        boolean changed = false;
        if (node.getOutputMask() != outputMask) {
            node.setOutputMask(outputMask);
            changed = true;
        }
        if (node.getInputMask() != inputMask) {
            node.setInputMask(inputMask);
            changed = true;
        }
        return changed;
    }

    private int getFurnaceTierOverride(World world, int worldX, int worldY, int worldZ) {
        if (world == null) {
            return -1;
        }
        BlockType blockType = world.getBlockType(worldX, worldY, worldZ);
        if (blockType == null || blockType.getId() == null) {
            return -1;
        }
        String blockId = blockType.getId();
        if (!TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            return -1;
        }
        int tierIndex = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE);
        if (tierIndex < 0) {
            return -1;
        }
        return FurnaceConfig.clampTier(tierIndex + 1);
    }

    private void syncFurnaceBlockId(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            int tier,
            ProcessingBenchBlock benchState,
            EnergyNodeComponent node) {
        if (world == null || benchState == null || node == null) {
            return;
        }
        BlockType blockType = world.getBlockType(worldX, worldY, worldZ);
        if (blockType == null || blockType.getId() == null) {
            return;
        }
        String blockId = blockType.getId();
        if (!TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            return;
        }

        int desiredTierIndex = Math.max(0, tier - 1);
        int currentTierIndex = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE);
        if (currentTierIndex < 0) {
            currentTierIndex = 0;
        }
        if (currentTierIndex == desiredTierIndex) {
            return;
        }

        if (benchState.isActive() || benchState.getInputProgress() > 0f) {
            return;
        }

        String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ELECTRIC_FURNACE, desiredTierIndex);
        UpgradePersistence.queueBlockSwapWithBench(
                world,
                new Vector3i(worldX, worldY, worldZ),
                upgradedId,
                node);
    }

    private void syncSolarState(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        if (node == null) {
            return;
        }
        int tier = SolarUpgradeConfig.clampTier(node.getSolarTier());
        syncTieredMachineState(world, worldX, worldY, worldZ, tier, SOLAR_STATE_NAMES, "Solar");
    }

    private void syncWindState(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node,
            double windFactor) {
        if (node == null) {
            return;
        }
        int tier = WindUpgradeConfig.clampTier(node.getWindTier());
        int safeTier = Math.max(0, Math.min(tier, WIND_STATE_NAMES.length - 1));
        String baseState = WIND_STATE_NAMES[safeTier];
        boolean working = node.getGeneration() > 0
                && windFactor > 0.0;
        String stateName = working ? baseState + "_Working" : baseState;
        if (stateName.equals(node.getLastWindState())) {
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }
        BlockType blockType = accessor.getBlockType(worldX, worldY, worldZ);
        if (blockType == null) {
            return;
        }

        try {
            accessor.setBlockInteractionState(worldX, worldY, worldZ, blockType, stateName, false);
            node.setLastWindState(stateName);
            if (working) {
                node.setLastWindAnimMs(System.currentTimeMillis());
            } else {
                node.setLastWindAnimMs(0L);
            }
        } catch (Exception e) {
            try {
                accessor.setBlockInteractionState(worldX, worldY, worldZ, blockType, baseState, false);
                node.setLastWindState(baseState);
                node.setLastWindAnimMs(0L);
            } catch (Exception ignored) {
                System.out.println("[HyProTech] Wind state '" + stateName
                        + "' not found for blockType=" + blockType.getId());
            }
        }
    }

    private boolean syncCableTierFromBlockId(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        int idTier = getCableTierFromBlockId(world, worldX, worldY, worldZ, HyProTechIds.BLOCK_ENERGY_CABLE);
        if (idTier < 0 || idTier <= node.getCableTier()) {
            return false;
        }
        node.setCableTier(idTier);
        int capacity = CableUpgradeConfig.getEnergyCapacityForTier(idTier);
        node.setCapacity(capacity);
        if (node.getEnergy() > capacity) {
            node.setEnergy(capacity);
        }
        node.setMaxTransfer(CableUpgradeConfig.getEnergyMaxTransferForTier(idTier));
        return true;
    }

    private boolean syncCableBlockId(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            EnergyNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        BlockType blockType = world.getBlockType(worldX, worldY, worldZ);
        if (blockType == null || blockType.getId() == null) {
            return false;
        }
        String blockId = blockType.getId();
        if (!isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)) {
            return false;
        }
        int desiredTier = CableUpgradeConfig.clampTier(node.getCableTier());
        if (desiredTier <= 0) {
            return false;
        }
        String upgradedId = buildEnergyCableTieredId(desiredTier);
        upgradedId = TieredIdUtil.applyNamespace(blockId, HyProTechIds.BLOCK_ENERGY_CABLE, upgradedId);
        if (isIdOrState(blockId, upgradedId)) {
            return false;
        }
        UpgradePersistence.queueBlockSwap(world, new Vector3i(worldX, worldY, worldZ), upgradedId, node, null);
        return true;
    }

    private String buildEnergyCableTieredId(int tier) {
        if (tier <= 0) {
            return HyProTechIds.BLOCK_ENERGY_CABLE;
        }
        return HyProTechIds.BLOCK_ENERGY_CABLE + "_S" + tier;
    }

    private void syncTieredMachineState(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            int tier,
            String[] stateNames,
            String label) {
        if (world == null || stateNames == null || stateNames.length == 0) {
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }

        BlockType blockType = accessor.getBlockType(worldX, worldY, worldZ);
        if (blockType == null) {
            return;
        }

        int safeTier = Math.max(0, Math.min(tier, stateNames.length - 1));
        String stateName = stateNames[safeTier];
        try {
            accessor.setBlockInteractionState(worldX, worldY, worldZ, blockType, stateName, false);
        } catch (Exception e) {
            System.out.println("[HyProTech] " + label + " state '" + stateName
                    + "' not found for blockType=" + blockType.getId());
        }
    }

    private void syncFurnaceState(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            int tierIndex,
            ProcessingBenchBlock benchState,
            EnergyNodeComponent node) {
        if (world == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }

        BlockType blockType = accessor.getBlockType(worldX, worldY, worldZ);
        if (blockType == null) {
            return;
        }

        int safeTier = Math.max(0, Math.min(tierIndex, FURNACE_STATE_NAMES.length - 1));
        boolean hasRecipe = benchState != null && benchState.getRecipe() != null;
        boolean working = benchState != null && (benchState.isActive() || benchState.getInputProgress() > 0f);
        if (!working && node != null && node.isFurnaceWorking() && hasRecipe) {
            // Keep working latched while a recipe is still present to avoid flicker.
            working = true;
        }
        String baseState = FURNACE_STATE_NAMES[safeTier];
        String stateName = baseState;
        if (working) {
            long now = System.currentTimeMillis();
            long startMs = node != null ? node.getFurnaceWorkingStartMs() : 0L;
            if (startMs > 0L && (now - startMs) < 1000L) {
                // Startup close animation phase.
                stateName = "ProcessingStart";
            } else {
                // Working loop.
                stateName = "Processing";
            }
        } else if (node != null && node.isFurnaceWorking()) {
            node.setFurnaceWorking(false);
            node.setFurnaceWorkingStartMs(0L);
            node.setLastFurnaceState("");
        }
        if (node != null && stateName.equals(node.getLastFurnaceState())) {
            return;
        }
        try {
            accessor.setBlockInteractionState(worldX, worldY, worldZ, blockType, stateName, false);
            if (node != null) {
                node.setLastFurnaceState(stateName);
            }
        } catch (Exception e) {
            try {
                accessor.setBlockInteractionState(worldX, worldY, worldZ, blockType, baseState, false);
                if (node != null) {
                    node.setLastFurnaceState(baseState);
                }
            } catch (Exception ignored) {
                System.out.println("[HyProTech] Furnace state '" + stateName
                        + "' not found for blockType=" + blockType.getId());
            }
        }
    }

    private int getCableTierFromBlockId(World world, int worldX, int worldY, int worldZ, String baseId) {
        if (world == null || baseId == null) {
            return -1;
        }
        BlockType blockType = world.getBlockType(worldX, worldY, worldZ);
        if (blockType == null || blockType.getId() == null) {
            return -1;
        }
        return BlockIdUtil.parseCableTierFromIdOrState(blockType.getId(), baseId);
    }

    private boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }

    private boolean setBenchFuelTime(ProcessingBenchBlock benchState, float value) {
        Field field = benchFuelTimeField;
        if (field == null) {
            try {
                field = ProcessingBenchBlock.class.getDeclaredField("fuelTime");
                field.setAccessible(true);
                benchFuelTimeField = field;
            } catch (NoSuchFieldException e) {
                return false;
            }
        }
        try {
            float current = field.getFloat(benchState);
            if (Float.compare(current, value) == 0) {
                return false;
            }
            field.setFloat(benchState, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private boolean setBenchInputProgress(ProcessingBenchBlock benchState, float value) {
        float current = benchState.getInputProgress();
        if (Float.compare(current, value) == 0) {
            return false;
        }
        benchState.setInputProgress(value);
        return true;
    }

    private ProcessingBenchBlock getProcessingBenchState(World world, int worldX, int worldY, int worldZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return null;
        }

        return BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                worldX,
                worldY,
                worldZ);
    }

    private BenchBlock getBenchBlock(World world, int worldX, int worldY, int worldZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return null;
        }

        return BlockModule.get().getComponent(
                BenchBlock.getComponentType(),
                world,
                worldX,
                worldY,
                worldZ);
    }

    private EnergySide getSolarOutputSide(World world, int worldX, int worldY, int worldZ) {
        Rotation yaw = getBlockYaw(world, worldX, worldY, worldZ);
        if (yaw == Rotation.Ninety) {
            return EnergySide.WEST;
        }
        if (yaw == Rotation.OneEighty) {
            return EnergySide.SOUTH;
        }
        if (yaw == Rotation.TwoSeventy) {
            return EnergySide.EAST;
        }
        return EnergySide.NORTH;
    }

    private Rotation getBlockYaw(World world, int worldX, int worldY, int worldZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return Rotation.None;
        }

        RotationTuple rotation = RotationTuple.get(
                world.getBlockRotationIndex(worldX, worldY, worldZ));
        if (rotation == null || rotation.yaw() == null) {
            return Rotation.None;
        }

        return rotation.yaw();
    }

    private Vector3i rotateLocalOffset(int localX, int localZ, Rotation yaw) {
        if (yaw == Rotation.Ninety) {
            return new Vector3i(localZ, 0, -localX);
        }
        if (yaw == Rotation.OneEighty) {
            return new Vector3i(-localX, 0, -localZ);
        }
        if (yaw == Rotation.TwoSeventy) {
            return new Vector3i(-localZ, 0, localX);
        }
        return new Vector3i(localX, 0, localZ);
    }

    private int rotateMaskByYaw(int mask, Rotation yaw) {
        int normalized = mask & EnergySide.ALL_MASK;
        if (yaw == null || yaw == Rotation.None) {
            return normalized;
        }

        int rotated = 0;
        for (EnergySide side : EnergySide.VALUES) {
            if ((normalized & side.mask()) == 0) {
                continue;
            }
            EnergySide rotatedSide = rotateSideByYaw(side, yaw);
            if (rotatedSide != null) {
                rotated |= rotatedSide.mask();
            }
        }
        return rotated;
    }

    private EnergySide rotateSideByYaw(EnergySide side, Rotation yaw) {
        if (side == EnergySide.UP || side == EnergySide.DOWN) {
            return side;
        }
        if (yaw == Rotation.Ninety) {
            switch (side) {
                case NORTH:
                    return EnergySide.WEST;
                case WEST:
                    return EnergySide.SOUTH;
                case SOUTH:
                    return EnergySide.EAST;
                case EAST:
                    return EnergySide.NORTH;
                default:
                    return side;
            }
        }
        if (yaw == Rotation.OneEighty) {
            switch (side) {
                case NORTH:
                    return EnergySide.SOUTH;
                case SOUTH:
                    return EnergySide.NORTH;
                case EAST:
                    return EnergySide.WEST;
                case WEST:
                    return EnergySide.EAST;
                default:
                    return side;
            }
        }
        if (yaw == Rotation.TwoSeventy) {
            switch (side) {
                case NORTH:
                    return EnergySide.EAST;
                case EAST:
                    return EnergySide.SOUTH;
                case SOUTH:
                    return EnergySide.WEST;
                case WEST:
                    return EnergySide.NORTH;
                default:
                    return side;
            }
        }
        return side;
    }

    private boolean shouldTransfer(int x, int y, int z, int nx, int ny, int nz) {
        if (x != nx) {
            return x < nx;
        }
        if (y != ny) {
            return y < ny;
        }
        return z < nz;
    }

    private boolean isNeighborConnectable(
            EnergyNodeComponent node,
            EnergyNodeComponent neighbor,
            EnergySide side,
            EnergySide neighborSide) {
        if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                || neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            if (node.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                    && neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                    && !sameCableColor(node, neighbor)) {
                return false;
            }
            return canCableConnect(node, side) && canCableConnect(neighbor, neighborSide);
        }

        boolean inputAllowed = allowsInput(node, side) && allowsOutput(neighbor, neighborSide);
        boolean outputAllowed = allowsOutput(node, side) && allowsInput(neighbor, neighborSide);
        return inputAllowed || outputAllowed;
    }

    private boolean canCableConnect(EnergyNodeComponent node, EnergySide side) {
        return node.allowsInput(side) || node.allowsOutput(side);
    }

    private boolean sameCableColor(EnergyNodeComponent first, EnergyNodeComponent second) {
        return first.getCableColor() == second.getCableColor();
    }

    private boolean allowsInput(EnergyNodeComponent node, EnergySide side) {
        if (!node.allowsInput(side)) {
            return false;
        }
        EnergyNodeComponent.NodeType type = node.getNodeType();
        return type != EnergyNodeComponent.NodeType.SOLAR
                && type != EnergyNodeComponent.NodeType.WIND;
    }

    private boolean allowsOutput(EnergyNodeComponent node, EnergySide side) {
        if (!node.allowsOutput(side)) {
            return false;
        }
        return node.getNodeType() != EnergyNodeComponent.NodeType.FURNACE;
    }

    private boolean transferEnergy(
            EnergyNodeComponent first,
            EnergySide firstSide,
            EnergyNodeComponent second,
            EnergySide secondSide,
            float deltaSeconds) {
        if (deltaSeconds <= 0f) {
            return false;
        }

        boolean firstToSecondAllowed = canTransferDirectional(first, firstSide, second, secondSide);
        boolean secondToFirstAllowed = canTransferDirectional(second, secondSide, first, firstSide);
        if (!firstToSecondAllowed && !secondToFirstAllowed) {
            return false;
        }

        int direction = chooseTransferDirection(first, second);
        if (direction > 0 && !firstToSecondAllowed) {
            if (secondToFirstAllowed) {
                direction = -1;
            } else {
                return false;
            }
        } else if (direction < 0 && !secondToFirstAllowed) {
            if (firstToSecondAllowed) {
                direction = 1;
            } else {
                return false;
            }
        }
        if (direction == 0) {
            return false;
        }

        EnergyNodeComponent from = direction > 0 ? first : second;
        EnergyNodeComponent to = direction > 0 ? second : first;

        int demand = to.getCapacity() - to.getEnergy();
        if (demand <= 0) {
            return false;
        }

        int transferPerSecond = Math.min(from.getMaxTransfer(), to.getMaxTransfer());
        if (transferPerSecond <= 0) {
            return false;
        }

        int transferLimit = (int) Math.round(transferPerSecond * deltaSeconds);
        if (transferLimit <= 0) {
            return false;
        }

        int available = from.getEnergy();
        if (available <= 0) {
            return false;
        }

        int amount = Math.min(transferLimit, available);
        amount = Math.min(amount, demand);
        amount = Math.min(amount, from.getEnergy());
        amount = Math.min(amount, to.getCapacity() - to.getEnergy());
        if (amount <= 0) {
            return false;
        }

        from.setEnergy(from.getEnergy() - amount);
        to.setEnergy(to.getEnergy() + amount);
        return true;
    }

    private boolean transferEnergyExternal(
            BlockComponentChunk components,
            EnergyNodeComponent node,
            EnergySide side,
            EnergyStorage external,
            float deltaSeconds) {
        if (deltaSeconds <= 0f || external == null) {
            return false;
        }

        boolean inputAllowed = allowsInput(node, side) && external.supportsExtraction();
        boolean outputAllowed = allowsOutput(node, side) && external.supportsInsertion();
        if (!inputAllowed && !outputAllowed) {
            return false;
        }

        int transferPerSecond = Math.min(node.getMaxTransfer(), getExternalMaxTransfer(external));
        if (transferPerSecond <= 0) {
            return false;
        }
        int transferLimit = (int) Math.round(transferPerSecond * deltaSeconds);
        if (transferLimit <= 0) {
            return false;
        }

        EnergyStorageSnapshot snapshot = snapshotStorage(external);
        double nodeRatio = energyRatio(node);
        double storageRatio = energyRatio(snapshot.amount, snapshot.capacity);

        int direction;
        if (outputAllowed && !inputAllowed) {
            direction = 1;
        } else if (!outputAllowed && inputAllowed) {
            direction = -1;
        } else {
            double diff = nodeRatio - storageRatio;
            if (Math.abs(diff) < EXTERNAL_RATIO_EPSILON) {
                return false;
            }
            direction = diff > 0 ? 1 : -1;
        }

        EnergyStorage nodeStorage = node.getStorage(components::markNeedsSaving);
        TransactionContext context = TransactionContext.current();
        long moved;
        if (direction > 0) {
            moved = EnergyStorageUtil.move(nodeStorage, external, () -> true, transferLimit, context);
        } else {
            moved = EnergyStorageUtil.move(external, nodeStorage, () -> true, transferLimit, context);
        }
        return moved > 0;
    }

    private boolean canTransferDirectional(
            EnergyNodeComponent from,
            EnergySide fromSide,
            EnergyNodeComponent to,
            EnergySide toSide) {
        if (!canProvide(from) || !canReceive(to)) {
            return false;
        }
        return allowsOutput(from, fromSide) && allowsInput(to, toSide);
    }

    private int chooseTransferDirection(EnergyNodeComponent first, EnergyNodeComponent second) {
        boolean firstCanReceive = canReceive(first);
        boolean secondCanReceive = canReceive(second);
        boolean firstCanProvide = canProvide(first);
        boolean secondCanProvide = canProvide(second);

        if (!firstCanReceive && !secondCanReceive) {
            return 0;
        }
        if (!firstCanProvide && !secondCanProvide) {
            return 0;
        }

        if (firstCanProvide && !secondCanProvide) {
            return secondCanReceive ? 1 : 0;
        }
        if (secondCanProvide && !firstCanProvide) {
            return firstCanReceive ? -1 : 0;
        }

        if (firstCanReceive && !secondCanReceive) {
            return secondCanProvide ? -1 : 0;
        }
        if (secondCanReceive && !firstCanReceive) {
            return firstCanProvide ? 1 : 0;
        }

        int firstPriority = receivePriority(first);
        int secondPriority = receivePriority(second);
        if (firstPriority != secondPriority) {
            return firstPriority > secondPriority ? -1 : 1;
        }

        double firstRatio = energyRatio(first);
        double secondRatio = energyRatio(second);
        double diff = firstRatio - secondRatio;
        if (Math.abs(diff) < 0.01) {
            return 0;
        }
        return diff > 0 ? 1 : -1;
    }

    private boolean canReceive(EnergyNodeComponent node) {
        EnergyNodeComponent.NodeType type = node.getNodeType();
        if (type == EnergyNodeComponent.NodeType.SOLAR
                || type == EnergyNodeComponent.NodeType.WIND) {
            return false;
        }
        return node.getCapacity() > node.getEnergy();
    }

    private boolean canProvide(EnergyNodeComponent node) {
        if (node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
            return false;
        }
        return node.getEnergy() > 0;
    }

    private int receivePriority(EnergyNodeComponent node) {
        switch (node.getNodeType()) {
            case FURNACE:
                return isFurnaceActive(node) ? 3 : 1;
            case BATTERY:
            case MACHINE:
            case QUARRY:
                return 2;
            case CABLE:
            case SOLAR:
            case WIND:
            default:
                return 0;
        }
    }

    private boolean isFurnaceActive(EnergyNodeComponent node) {
        return node.isEnabled() && node.getProgress() > 0 && node.getProgressMax() > 0;
    }

    private double energyRatio(EnergyNodeComponent node) {
        int capacity = node.getCapacity();
        if (capacity <= 0) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (double) node.getEnergy() / capacity));
    }

    private double energyRatio(long amount, long capacity) {
        if (capacity <= 0) {
            return amount > 0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (double) amount / capacity));
    }

    private CableState getCableState(World world) {
        CableState state = cableStates.get(world);
        if (state == null) {
            state = new CableState();
            cableStates.put(world, state);
        }
        return state;
    }

    private boolean markVisited(CableState state, long chunkIndex, int blockIndex) {
        IntOpenHashSet visited = state.visitedByChunk.get(chunkIndex);
        if (visited == null) {
            visited = new IntOpenHashSet();
            state.visitedByChunk.put(chunkIndex, visited);
        }
        return visited.add(blockIndex);
    }

    private static final class CableState {
        private long lastTick = Long.MIN_VALUE;
        private final Long2ObjectMap<IntOpenHashSet> visitedByChunk = new Long2ObjectOpenHashMap<>();
    }

    private static final class CableNetwork {
        private final List<CableNode> cables = new ArrayList<>();
        private final List<NeighborNode> neighbors = new ArrayList<>();
        private final List<ExternalNeighbor> externals = new ArrayList<>();
        private int totalEnergy;
        private int totalCapacity;
        private int maxTransfer;

        private void addCable(
                EnergyNodeComponent node,
                BlockComponentChunk components,
                int x,
                int y,
                int z,
                long chunkIndex) {
            boolean firstCable = cables.isEmpty();
            CableNode cable = new CableNode(node, components, x, y, z, chunkIndex);
            cables.add(cable);
            totalEnergy += cable.energy;
            totalCapacity += cable.capacity;
            if (firstCable) {
                maxTransfer = cable.maxTransfer;
            } else {
                maxTransfer = Math.min(maxTransfer, cable.maxTransfer);
            }
        }
    }

    private static final class CableNode {
        private final EnergyNodeComponent node;
        private final BlockComponentChunk components;
        private final int energy;
        private final int capacity;
        private final int maxTransfer;
        private int connectedMask;
        private int cableMask;
        private final int x;
        private final int y;
        private final int z;
        private final long chunkIndex;

        private CableNode(
                EnergyNodeComponent node,
                BlockComponentChunk components,
                int x,
                int y,
                int z,
                long chunkIndex) {
            this.node = node;
            this.components = components;
            this.energy = node.getEnergy();
            this.capacity = node.getCapacity();
            this.maxTransfer = node.getMaxTransfer();
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkIndex = chunkIndex;
        }
    }

    private static final class NeighborNode {
        private final EnergyNodeComponent node;
        private final BlockComponentChunk components;
        private int inputTransfer;
        private int outputTransfer;

        private NeighborNode(EnergyNodeComponent node, BlockComponentChunk components) {
            this.node = node;
            this.components = components;
        }
    }

    private static final class ExternalNeighbor {
        private final EnergyStorage storage;
        private final int maxTransfer;
        private int inputTransfer;
        private int outputTransfer;
        private int inputBudget;
        private int outputBudget;

        private ExternalNeighbor(EnergyStorage storage, int maxTransfer) {
            this.storage = storage;
            this.maxTransfer = maxTransfer;
        }
    }

    private static final class EnergyStorageSnapshot {
        private final long amount;
        private final long capacity;

        private EnergyStorageSnapshot(long amount, long capacity) {
            this.amount = amount;
            this.capacity = capacity;
        }
    }

    private static final class CablePos {
        private final int x;
        private final int y;
        private final int z;

        private CablePos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class TransferBudget {
        private int remaining;

        private TransferBudget(int remaining) {
            this.remaining = remaining;
        }
    }

    private static String[] buildCableStateNames() {
        String[] names = new String[EnergySide.ALL_MASK + 1];
        for (int mask = 0; mask < names.length; mask++) {
            names[mask] = String.format("Cable_%02d", mask);
        }
        return names;
    }

    private static String cableStateName(int mask, int tier) {
        int normalized = mask & EnergySide.ALL_MASK;
        return CABLE_STATE_NAMES[normalized];
    }

    private static String[] buildTierStateNames(String prefix, int maxTier) {
        String[] names = new String[maxTier + 1];
        for (int tier = 0; tier <= maxTier; tier++) {
            names[tier] = prefix + tier;
        }
        return names;
    }
}
