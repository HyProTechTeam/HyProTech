package HyProTechTeam.item;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.HyProTechComponents;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.UpgradePersistence;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.CableUpgradeConfig;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import HyProTechTeam.machine.AlloySmelterConfig;
import HyProTechTeam.machine.MachineItemAccess;
import HyProTechTeam.machine.OreCrusherConfig;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemNetworkSystem extends EntityTickingSystem<ChunkStore> {
    private static final int DEFAULT_MAX_TRANSFER = 16;
    private static final int MAX_BUDGET_PER_TICK = 256;
    private static final int MAX_MOVES_PER_SLOT = 8;
    private static final int MAX_NETWORKS_PER_TICK = 64;
    private static final long MAX_TICK_NANOS = 100_000_000L;
    private static final String[] CABLE_STATE_NAMES = buildCableStateNames();

    private final ComponentType<ChunkStore, ItemNodeComponent> itemType;
    private final Map<World, CableState> cableStates = new IdentityHashMap<>();

    public ItemNetworkSystem(ComponentType<ChunkStore, ItemNodeComponent> itemType) {
        this.itemType = itemType;
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
        if (world == null) {
            return;
        }
        UpgradePersistence.cleanupExpired(world);

        CableState cableState = getCableState(world);
        long worldTick = world.getTick();
        if (worldTick != cableState.lastTick) {
            cableState.visitedByChunk.clear();
            cableState.lastTick = worldTick;
            cableState.tickStartNanos = System.nanoTime();
            cableState.networksProcessed = 0;
        }

        int chunkX = worldChunk.getX();
        int chunkZ = worldChunk.getZ();
        IntArrayList invalidReferences = null;

        for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<Holder<ChunkStore>> entry
                : blockComponents.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            Holder<ChunkStore> holder = entry.getValue();
            ItemNodeComponent node = blockComponents.getComponent(blockIndex, itemType);
            if (node == null) {
                node = ensureItemCableNode(
                        blockComponents, commandBuffer, world, chunkX, chunkZ, blockIndex, holder, null);
            }
            if (node != null) {
                processNode(blockComponents, world, chunkX, chunkZ, blockIndex, node, delta, cableState);
            }
        }

        for (it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry<Ref<ChunkStore>> entry
                : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            int blockIndex = entry.getIntKey();
            Ref<ChunkStore> ref = entry.getValue();
            if (ref == null || !ref.isValid()) {
                if (invalidReferences == null) {
                    invalidReferences = new IntArrayList();
                }
                invalidReferences.add(blockIndex);
                continue;
            }
            ItemNodeComponent node = blockComponents.getComponent(blockIndex, itemType);
            if (node == null) {
                node = ensureItemCableNode(
                        blockComponents, commandBuffer, world, chunkX, chunkZ, blockIndex, null, ref);
            }
            if (node != null) {
                processNode(blockComponents, world, chunkX, chunkZ, blockIndex, node, delta, cableState);
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

    private ItemNodeComponent ensureItemCableNode(
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
        if (!TieredIdUtil.isTieredId(blockType.getId(), HyProTechIds.BLOCK_ITEM_CABLE)) {
            return null;
        }

        ItemNodeComponent node = new ItemNodeComponent();
        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, ChunkUtil.xFromBlockInColumn(blockIndex));
        int worldY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, ChunkUtil.zFromBlockInColumn(blockIndex));
        int tier = getCableTierFromBlockId(world, worldX, worldY, worldZ, HyProTechIds.BLOCK_ITEM_CABLE);
        if (tier >= 0) {
            node.setCableTier(tier);
            node.setMaxTransfer(CableUpgradeConfig.getItemMaxTransferForTier(tier));
        }

        if (holder != null) {
            holder.putComponent(itemType, node);
        } else if (commandBuffer != null && ref != null) {
            commandBuffer.putComponent(ref, itemType, node);
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
            // Best-effort only.
        }
    }

    private void processNode(
            BlockComponentChunk blockComponents,
            World world,
            int chunkX,
            int chunkZ,
            int blockIndex,
            ItemNodeComponent node,
            float deltaSeconds,
            CableState cableState) {
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        int worldY = localY;

        // Keep cable blocks out of vanilla ticking queues to avoid stale refs on break.
        disableTickingAt(world, chunkX, chunkZ, blockIndex);

        boolean changed = UpgradePersistence.applyItemUpgrade(world, worldX, worldY, worldZ, node);
        changed |= syncCableTierFromBlockId(world, worldX, worldY, worldZ, node);
        changed |= ensureDefaults(node);
        changed |= syncCableBlockId(world, worldX, worldY, worldZ, node);
        if (changed) {
            blockComponents.markNeedsSaving();
        }

        processCableNetwork(world.getChunkStore(), world, worldX, worldY, worldZ, deltaSeconds, cableState);
    }

    private boolean ensureDefaults(ItemNodeComponent node) {
        boolean changed = false;
        int priority = ItemNodeComponent.clampPriority(node.getPriority());
        if (node.getPriority() != priority) {
            node.setPriority(priority);
            changed = true;
        }
        if (node.getDistributionMode() == null) {
            node.setDistributionMode(ItemDistributionMode.ROUND_ROBIN);
            changed = true;
        }
        int tier = CableUpgradeConfig.clampTier(node.getCableTier());
        if (node.getCableTier() != tier) {
            node.setCableTier(tier);
            changed = true;
        }
        int maxTransfer = CableUpgradeConfig.getItemMaxTransferForTier(tier);
        if (node.getMaxTransfer() != maxTransfer) {
            node.setMaxTransfer(maxTransfer);
            changed = true;
        }
        return changed;
    }

    private boolean syncCableTierFromBlockId(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            ItemNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        int idTier = getCableTierFromBlockId(world, worldX, worldY, worldZ, HyProTechIds.BLOCK_ITEM_CABLE);
        if (idTier < 0 || idTier <= node.getCableTier()) {
            return false;
        }
        node.setCableTier(idTier);
        node.setMaxTransfer(CableUpgradeConfig.getItemMaxTransferForTier(idTier));
        return true;
    }

    private boolean syncCableBlockId(
            World world,
            int worldX,
            int worldY,
            int worldZ,
            ItemNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        BlockType blockType = world.getBlockType(worldX, worldY, worldZ);
        if (blockType == null || blockType.getId() == null) {
            return false;
        }
        String blockId = blockType.getId();
        if (!isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE)) {
            return false;
        }
        int desiredTier = CableUpgradeConfig.clampTier(node.getCableTier());
        if (desiredTier <= 0) {
            return false;
        }
        String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ITEM_CABLE, desiredTier);
        upgradedId = TieredIdUtil.applyNamespace(blockId, HyProTechIds.BLOCK_ITEM_CABLE, upgradedId);
        if (isIdOrState(blockId, upgradedId)) {
            return false;
        }
        UpgradePersistence.queueBlockSwap(world, new Vector3i(worldX, worldY, worldZ), upgradedId, null, node);
        return true;
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

    private void processCableNetwork(
            ChunkStore chunkStore,
            World world,
            int worldX,
            int worldY,
            int worldZ,
            float deltaSeconds,
            CableState cableState) {
        if (cableState == null) {
            return;
        }
        if (cableState.networksProcessed >= MAX_NETWORKS_PER_TICK) {
            return;
        }
        if (cableState.tickStartNanos > 0L
                && System.nanoTime() - cableState.tickStartNanos > MAX_TICK_NANOS) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        int localX = ChunkUtil.localCoordinate((long) worldX);
        int localZ = ChunkUtil.localCoordinate((long) worldZ);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, worldY, localZ);

        if (!markVisited(cableState, chunkIndex, blockIndex)) {
            return;
        }
        cableState.networksProcessed++;

        CableNetwork network = buildCableNetwork(chunkStore, worldX, worldY, worldZ, cableState);
        if (network.cables.isEmpty()) {
            return;
        }

        collectEndpoints(world, chunkStore, network);
        updateCableStates(world, chunkStore, network);
        applyTransfers(world, network, deltaSeconds, cableState);
    }

    private CableNetwork buildCableNetwork(
            ChunkStore chunkStore,
            int startX,
            int startY,
            int startZ,
            CableState cableState) {
        CableNetwork network = new CableNetwork();
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
            ItemNodeComponent node = components.getComponent(blockIndex, itemType);
            if (node == null) {
                continue;
            }

            network.addCable(node, pos.x, pos.y, pos.z, chunkIndex, blockIndex, DEFAULT_MAX_TRANSFER);

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
                ItemNodeComponent neighbor = neighborComponents.getComponent(neighborIndex, itemType);
                if (neighbor == null) {
                    continue;
                }

                if (markVisited(cableState, neighborChunkIndex, neighborIndex)) {
                    queue.add(new CablePos(neighborX, neighborY, neighborZ));
                }
            }
        }

        return network;
    }

    private void collectEndpoints(World world, ChunkStore chunkStore, CableNetwork network) {
        List<SourceEndpoint> sources = new ArrayList<>();
        List<SinkEndpoint> sinks = new ArrayList<>();

        for (int cableIndex = 0; cableIndex < network.cables.size(); cableIndex++) {
            CableNode cable = network.cables.get(cableIndex);
            ItemMode mode = cable.node.getMode();
            if (mode == ItemMode.OFF) {
                continue;
            }

            ItemTarget target = cable.node.getTarget();
            boolean allowTake = mode.allowsTake();
            boolean allowPut = mode.allowsPut();

            for (EnergySide side : EnergySide.VALUES) {
                boolean sideAllowsTake = allowTake && cable.node.allowsTake(side);
                boolean sideAllowsPut = allowPut && cable.node.allowsPut(side);
                if (!sideAllowsTake && !sideAllowsPut) {
                    continue;
                }

                int neighborX = cable.x + side.dx();
                int neighborY = cable.y + side.dy();
                int neighborZ = cable.z + side.dz();
                if (neighborY < ChunkUtil.MIN_Y || neighborY >= ChunkUtil.HEIGHT) {
                    continue;
                }

                if (isItemCable(chunkStore, neighborX, neighborY, neighborZ)) {
                    continue;
                }

                ContainerLookup lookup = resolveContainerState(world, chunkStore, neighborX, neighborY, neighborZ);
                if (lookup == null) {
                    continue;
                }
                Vector3i pos = lookup.position;

                boolean isMachine = lookup.isMachine;
                SlotRange sourceRange = null;
                SlotRange sinkRange = null;
                if (lookup.machineSlots != null) {
                    sourceRange = lookup.machineSlots.outputRange;
                    sinkRange = lookup.machineSlots.inputRange;
                }
                if (sideAllowsTake) {
                    ItemContainer sourceContainer = resolveContainer(world, pos, lookup, target, true);
                    addSource(
                            sources,
                            sourceContainer,
                            pos,
                            isMachine,
                            sourceRange,
                            cable.node,
                            cable,
                            cableIndex,
                            side);
                }
                if (sideAllowsPut) {
                    ItemContainer sinkContainer = resolveContainer(world, pos, lookup, target, false);
                    addSink(
                            sinks,
                            sinkContainer,
                            pos,
                            isMachine,
                            sinkRange,
                            cable.node,
                            cable,
                            cableIndex,
                            side,
                            world,
                            chunkStore);
                }
            }
        }

        network.sources.addAll(sources);
        network.sinks.addAll(sinks);
    }

    private void updateCableStates(World world, ChunkStore chunkStore, CableNetwork network) {
        for (CableNode cable : network.cables) {
            BlockAccessor accessor = world.getChunkIfLoaded(cable.chunkIndex);
            if (accessor == null) {
                continue;
            }

            BlockType blockType = accessor.getBlockType(cable.x, cable.y, cable.z);
            if (blockType == null) {
                continue;
            }

            int connectedMask = computeConnectedMask(world, chunkStore, cable);
            RotationTuple rotation = RotationTuple.get(
                    world.getBlockRotationIndex(cable.x, cable.y, cable.z));
            int localBaseMask = rotateMaskToLocal(connectedMask, rotation);
            int tier = CableUpgradeConfig.clampTier(cable.node.getCableTier());
            String stateName = cableStateName(localBaseMask, tier);
            if (stateName.equals(cable.node.getLastCableState())) {
                continue;
            }

            try {
                accessor.setBlockInteractionState(cable.x, cable.y, cable.z, blockType, stateName, false);
                cable.node.setLastCableState(stateName);
            } catch (Exception e) {
                System.out.println("[HyProTech] Item cable state '" + stateName
                        + "' not found for blockType=" + blockType.getId());
                cable.node.setLastCableState("");
            }
        }
    }

    private int computeConnectedMask(World world, ChunkStore chunkStore, CableNode cable) {
        int mask = 0;
        for (EnergySide side : EnergySide.VALUES) {
            int nx = cable.x + side.dx();
            int ny = cable.y + side.dy();
            int nz = cable.z + side.dz();
            if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                continue;
            }

            if (isItemCable(chunkStore, nx, ny, nz)) {
                mask |= side.mask();
                continue;
            }

            ContainerLookup lookup = resolveContainerState(world, chunkStore, nx, ny, nz);
            if (lookup != null) {
                mask |= side.mask();
            }
        }
        return mask;
    }

    private ItemContainer resolveContainer(
            World world,
            Vector3i pos,
            ContainerLookup lookup,
            ItemTarget target,
            boolean forExtract) {
        if (lookup == null) {
            return null;
        }

        Object state = lookup.state;
        if (state == null) {
            return null;
        }
        ItemContainer fallback = MachineItemAccess.getItemContainerFromState(state);
        if (state instanceof ProcessingBenchBlock) {
            ProcessingBenchBlock benchBlock = (ProcessingBenchBlock) state;
            ItemContainer input = benchBlock.getInputContainer();
            ItemContainer fuel = benchBlock.getFuelContainer();
            ItemContainer output = benchBlock.getOutputContainer();

            ItemTarget resolved = target == null ? ItemTarget.AUTO : target;
            switch (resolved) {
                case INPUT:
                    return forExtract ? null : input;
                case FUEL:
                    return forExtract ? null : fuel;
                case OUTPUT:
                    return forExtract ? output : null;
                case AUTO:
                default:
                    if (forExtract) {
                        return output;
                    }
                    if (input != null) {
                        return input;
                    }
                    if (fuel != null) {
                        return fuel;
                    }
                    return fallback;
            }
        }

        return fallback;
    }


    private void addSource(
            List<SourceEndpoint> sources,
            ItemContainer container,
            Vector3i pos,
            boolean isMachine,
            SlotRange slotRange,
            ItemNodeComponent node,
            CableNode cable,
            int cableIndex,
            EnergySide side) {
        if (container == null || pos == null || cable == null || side == null) {
            return;
        }
        if (slotRange != null && slotRange.isEmpty()) {
            return;
        }
        Set<String> filters = node == null ? null : node.getFilters(side);
        FilterMode filterMode = node == null ? FilterMode.WHITELIST : node.getFilterMode(side);
        ItemDistributionMode distributionMode = node == null
                ? ItemDistributionMode.ROUND_ROBIN
                : node.getDistributionMode();
        sources.add(new SourceEndpoint(
                container,
                pos,
                isMachine,
                slotRange,
                filters,
                filterMode,
                distributionMode,
                cableIndex,
                cable.x,
                cable.y,
                cable.z,
                side));
    }

    private void addSink(
            List<SinkEndpoint> sinks,
            ItemContainer container,
            Vector3i pos,
            boolean isMachine,
            SlotRange slotRange,
            ItemNodeComponent node,
            CableNode cable,
            int cableIndex,
            EnergySide side,
            World world,
            ChunkStore chunkStore) {
        if (container == null || pos == null || cable == null || side == null) {
            return;
        }
        if (slotRange != null && slotRange.isEmpty()) {
            return;
        }
        Set<String> filters = node == null ? null : node.getFilters(side);
        FilterMode filterMode = node == null ? FilterMode.WHITELIST : node.getFilterMode(side);
        int priority = resolveStoragePriority(world, chunkStore, pos, node);
        sinks.add(new SinkEndpoint(
                container,
                pos,
                isMachine,
                slotRange,
                filters,
                filterMode,
                priority,
                cableIndex,
                cable.x,
                cable.y,
                cable.z,
                side));
    }

    private void applyTransfers(World world, CableNetwork network, float deltaSeconds, CableState cableState) {
        if (deltaSeconds <= 0f) {
            return;
        }
        if (network.sources.isEmpty() || network.sinks.isEmpty()) {
            return;
        }

        int maxTransfer = network.maxTransfer == Integer.MAX_VALUE
                ? DEFAULT_MAX_TRANSFER
                : network.maxTransfer;
        if (maxTransfer <= 0) {
            return;
        }

        int budget = (int) Math.round(maxTransfer * deltaSeconds);
        if (budget <= 0) {
            budget = 1;
        }
        if (budget > maxTransfer) {
            budget = maxTransfer;
        }
        if (budget > MAX_BUDGET_PER_TICK) {
            budget = MAX_BUDGET_PER_TICK;
        }

        sortEndpoints(network);
        transferItems(world, network, budget, cableState);
    }

    private int transferItems(World world, CableNetwork network, int budget, CableState cableState) {
        if (budget <= 0 || network.sinks.isEmpty()) {
            return 0;
        }
        if (shouldAbortTick(cableState)) {
            return 0;
        }

        int maxPriority = ItemNodeComponent.MIN_PRIORITY - 1;
        for (SinkEndpoint sink : network.sinks) {
            if (sink != null) {
                maxPriority = Math.max(maxPriority, sink.priority);
            }
        }
        if (maxPriority < ItemNodeComponent.MIN_PRIORITY) {
            return 0;
        }

        List<SinkEndpoint>[] sinksByPriority = groupSinksByPriority(network.sinks);
        Map<Integer, int[]> distanceCache = new HashMap<>();

        int moved = 0;
        for (SourceEndpoint source : network.sources) {
            if (moved >= budget) {
                break;
            }
            if (shouldAbortTick(cableState)) {
                break;
            }
            moved += transferFromSource(
                    world,
                    source,
                    network,
                    sinksByPriority,
                    maxPriority,
                    budget - moved,
                    cableState,
                    distanceCache);
        }
        return moved;
    }

    private int transferFromSource(
            World world,
            SourceEndpoint source,
            CableNetwork network,
            List<SinkEndpoint>[] sinksByPriority,
            int maxPriority,
            int budget,
            CableState cableState,
            Map<Integer, int[]> distanceCache) {
        ItemContainer sourceContainer = source == null ? null : source.container;
        if (sourceContainer == null || budget <= 0) {
            return 0;
        }

        ItemDistributionMode mode = source.distributionMode == null
                ? ItemDistributionMode.ROUND_ROBIN
                : source.distributionMode;
        short capacity = sourceContainer.getCapacity();
        int moved = 0;

        for (short slot = 0; slot < capacity && moved < budget; slot++) {
            if (shouldAbortTick(cableState)) {
                break;
            }
            if (!source.allowsSlot(slot)) {
                continue;
            }
            ItemStack stack = sourceContainer.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (!source.allows(stack.getItemId())) {
                continue;
            }

            ItemStack single = stack.getQuantity() == 1 ? stack : stack.withQuantity(1);
            boolean movedThisSlot;
            int attempts = 0;
            do {
                movedThisSlot = false;
                if (shouldAbortTick(cableState)) {
                    break;
                }
                boolean movedItem;
                if (mode == ItemDistributionMode.ROUND_ROBIN) {
                    movedItem = transferRoundRobin(
                            world,
                            source,
                            single,
                            sourceContainer,
                            slot,
                            sinksByPriority,
                            maxPriority,
                            network,
                            cableState);
                } else {
                    boolean furthest = mode == ItemDistributionMode.FURTHEST;
                    movedItem = transferByDistance(
                            world,
                            source,
                            single,
                            sourceContainer,
                            slot,
                            sinksByPriority,
                            maxPriority,
                            network,
                            distanceCache,
                            furthest,
                            cableState);
                }
                if (movedItem) {
                    moved++;
                    movedThisSlot = true;
                }
                attempts++;
            } while (movedThisSlot && moved < budget && attempts < MAX_MOVES_PER_SLOT);
        }

        return moved;
    }

    private boolean transferRoundRobin(
            World world,
            SourceEndpoint source,
            ItemStack stack,
            ItemContainer sourceContainer,
            short slot,
            List<SinkEndpoint>[] sinksByPriority,
            int maxPriority,
            CableNetwork network,
            CableState cableState) {
        if (source == null || stack == null || sourceContainer == null || sinksByPriority == null) {
            return false;
        }
        NetworkKey networkKey = network == null ? null : network.getNetworkKey();
        for (int priority = maxPriority; priority >= ItemNodeComponent.MIN_PRIORITY; priority--) {
            if (shouldAbortTick(cableState)) {
                return false;
            }
            List<SinkEndpoint> sinks = sinksByPriority[priority];
            if (sinks == null || sinks.isEmpty()) {
                continue;
            }
            int startIndex = getRoundRobinIndex(cableState, networkKey, priority);
            if (startIndex >= sinks.size()) {
                startIndex = startIndex % sinks.size();
            }
            for (int i = 0; i < sinks.size(); i++) {
                if (shouldAbortTick(cableState)) {
                    return false;
                }
                int index = (startIndex + i) % sinks.size();
                SinkEndpoint sink = sinks.get(index);
                if (!isEligibleSink(source, sink, stack)) {
                    continue;
                }
                if (!canAccept(sink, stack)) {
                    continue;
                }
                if (tryMove(source, sourceContainer, slot, sink, stack)) {
                    cacheAfterMove(world, source, sink);
                    int nextIndex = (index + 1) % sinks.size();
                    setRoundRobinIndex(cableState, networkKey, priority, nextIndex);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean transferByDistance(
            World world,
            SourceEndpoint source,
            ItemStack stack,
            ItemContainer sourceContainer,
            short slot,
            List<SinkEndpoint>[] sinksByPriority,
            int maxPriority,
            CableNetwork network,
            Map<Integer, int[]> distanceCache,
            boolean furthest,
            CableState cableState) {
        if (source == null || stack == null || sourceContainer == null || sinksByPriority == null) {
            return false;
        }
        int[] distances = getDistances(network, source.cableIndex, distanceCache);
        for (int priority = maxPriority; priority >= ItemNodeComponent.MIN_PRIORITY; priority--) {
            if (shouldAbortTick(cableState)) {
                return false;
            }
            List<SinkEndpoint> sinks = sinksByPriority[priority];
            if (sinks == null || sinks.isEmpty()) {
                continue;
            }
            boolean[] tried = null;
            while (true) {
                if (shouldAbortTick(cableState)) {
                    return false;
                }
                int bestIndex = selectBestSinkIndex(source, stack, sinks, distances, furthest, tried);
                if (bestIndex < 0) {
                    break;
                }
                SinkEndpoint sink = sinks.get(bestIndex);
                if (tryMove(source, sourceContainer, slot, sink, stack)) {
                    cacheAfterMove(world, source, sink);
                    return true;
                }
                if (tried == null) {
                    tried = new boolean[sinks.size()];
                }
                tried[bestIndex] = true;
            }
        }
        return false;
    }

    private int selectBestSinkIndex(
            SourceEndpoint source,
            ItemStack stack,
            List<SinkEndpoint> sinks,
            int[] distances,
            boolean furthest,
            boolean[] tried) {
        if (sinks == null || sinks.isEmpty() || distances == null) {
            return -1;
        }
        int bestIndex = -1;
        int bestDistance = furthest ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        SinkEndpoint bestSink = null;
        for (int i = 0; i < sinks.size(); i++) {
            if (tried != null && tried[i]) {
                continue;
            }
            SinkEndpoint sink = sinks.get(i);
            if (!isEligibleSink(source, sink, stack)) {
                continue;
            }
            if (!canAccept(sink, stack)) {
                continue;
            }
            int cableIndex = sink.cableIndex;
            if (cableIndex < 0 || cableIndex >= distances.length) {
                continue;
            }
            int distance = distances[cableIndex];
            if (distance < 0) {
                continue;
            }
            if (bestIndex < 0) {
                bestIndex = i;
                bestDistance = distance;
                bestSink = sink;
                continue;
            }
            if (furthest) {
                if (distance > bestDistance
                        || (distance == bestDistance && ENDPOINT_ORDER.compare(sink, bestSink) < 0)) {
                    bestIndex = i;
                    bestDistance = distance;
                    bestSink = sink;
                }
            } else {
                if (distance < bestDistance
                        || (distance == bestDistance && ENDPOINT_ORDER.compare(sink, bestSink) < 0)) {
                    bestIndex = i;
                    bestDistance = distance;
                    bestSink = sink;
                }
            }
        }
        return bestIndex;
    }

    private int[] getDistances(
            CableNetwork network,
            int sourceCableIndex,
            Map<Integer, int[]> distanceCache) {
        if (distanceCache == null) {
            return computeDistances(network, sourceCableIndex);
        }
        int[] cached = distanceCache.get(sourceCableIndex);
        if (cached != null && network != null && cached.length == network.cables.size()) {
            return cached;
        }
        int[] distances = computeDistances(network, sourceCableIndex);
        distanceCache.put(sourceCableIndex, distances);
        return distances;
    }

    private int[] computeDistances(CableNetwork network, int sourceCableIndex) {
        int size = network == null ? 0 : network.cables.size();
        int[] distances = new int[size];
        Arrays.fill(distances, -1);
        if (network == null || sourceCableIndex < 0 || sourceCableIndex >= size) {
            return distances;
        }

        int[] queue = new int[size];
        int head = 0;
        int tail = 0;
        distances[sourceCableIndex] = 0;
        queue[tail++] = sourceCableIndex;

        while (head < tail) {
            int index = queue[head++];
            CableNode cable = network.cables.get(index);
            int baseDistance = distances[index];
            for (EnergySide side : EnergySide.VALUES) {
                int nx = cable.x + side.dx();
                int ny = cable.y + side.dy();
                int nz = cable.z + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }
                int neighborIndex = network.getCableIndex(nx, ny, nz);
                if (neighborIndex < 0 || neighborIndex >= size) {
                    continue;
                }
                if (distances[neighborIndex] >= 0) {
                    continue;
                }
                distances[neighborIndex] = baseDistance + 1;
                queue[tail++] = neighborIndex;
            }
        }

        return distances;
    }

    private boolean canAccept(SinkEndpoint sink, ItemStack stack) {
        if (sink == null || sink.container == null || stack == null) {
            return false;
        }
        SlotRange range = sink.slotRange;
        if (range == null) {
            return sink.container.canAddItemStack(stack);
        }
        short capacity = sink.container.getCapacity();
        short start = (short) Math.max(0, range.start);
        short end = (short) Math.min(capacity, range.start + range.count);
        for (short slot = start; slot < end; slot++) {
            if (sink.container.canAddItemStackToSlot(slot, stack, false, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldAbortTick(CableState cableState) {
        if (cableState == null) {
            return false;
        }
        if (cableState.tickStartNanos <= 0L) {
            return false;
        }
        return System.nanoTime() - cableState.tickStartNanos > MAX_TICK_NANOS;
    }

    private void cacheAfterMove(World world, Endpoint source, Endpoint sink) {
        // Machine inventory is removed; no cache behavior needed here.
    }

    private boolean tryMove(
            SourceEndpoint source,
            ItemContainer sourceContainer,
            short slot,
            SinkEndpoint sink,
            ItemStack stack) {
        if (sourceContainer == null || sink == null || sink.container == null || stack == null) {
            return false;
        }
        return tryMoveSafely(sourceContainer, slot, sink, stack);
    }

    private boolean tryMoveSafely(
            ItemContainer sourceContainer,
            short sourceSlot,
            SinkEndpoint sink,
            ItemStack stack) {
        if (sourceContainer == null || sink == null || sink.container == null || stack == null) {
            return false;
        }

        SlotRange range = sink.slotRange;
        if (range == null) {
            return tryMoveOneAnySlotWithRollback(sourceContainer, sourceSlot, sink.container, stack);
        }

        short capacity = sink.container.getCapacity();
        short start = (short) Math.max(0, range.start);
        short end = (short) Math.min(capacity, range.start + range.count);
        for (short targetSlot = start; targetSlot < end; targetSlot++) {
            if (!sink.container.canAddItemStackToSlot(targetSlot, stack, false, false)) {
                continue;
            }
            if (tryMoveOneToSlotWithRollback(sourceContainer, sourceSlot, sink.container, targetSlot)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryMoveOneAnySlotWithRollback(
            ItemContainer sourceContainer,
            short sourceSlot,
            ItemContainer sinkContainer,
            ItemStack stack) {
        if (sourceContainer == null || sinkContainer == null || stack == null) {
            return false;
        }
        ItemStack sourceStack = sourceContainer.getItemStack(sourceSlot);
        if (sourceStack == null || ItemStack.isEmpty(sourceStack)) {
            return false;
        }
        String itemId = sourceStack.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        ItemStack moveStack = new ItemStack(sourceStack.getItemId(), 1, sourceStack.getMetadata());
        if (!sinkContainer.canAddItemStack(moveStack)) {
            return false;
        }
        int sourceCountBefore = countItemQuantity(sourceContainer, itemId);
        int sinkCountBefore = countItemQuantity(sinkContainer, itemId);

        ItemStackTransaction addTx = sinkContainer.addItemStack(moveStack);
        if (addTx == null
                || !addTx.succeeded()
                || countItemQuantity(sinkContainer, itemId) < sinkCountBefore + 1) {
            return false;
        }

        ItemStackSlotTransaction removeTx = sourceContainer.removeItemStackFromSlot(sourceSlot, 1);
        if (removeTx == null
                || !removeTx.succeeded()
                || countItemQuantity(sourceContainer, itemId) > sourceCountBefore - 1) {
            // Best-effort rollback; prevents source duplication if removal fails.
            sinkContainer.removeItemStack(moveStack);
            return false;
        }
        return true;
    }

    private boolean tryMoveOneToSlotWithRollback(
            ItemContainer sourceContainer,
            short sourceSlot,
            ItemContainer sinkContainer,
            short targetSlot) {
        if (sourceContainer == null || sinkContainer == null) {
            return false;
        }
        ItemStack sourceStack = sourceContainer.getItemStack(sourceSlot);
        if (sourceStack == null || ItemStack.isEmpty(sourceStack)) {
            return false;
        }
        String itemId = sourceStack.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        int sourceCountBefore = countItemQuantity(sourceContainer, itemId);
        int sinkCountBefore = countItemQuantity(sinkContainer, itemId);

        ItemStack moveStack = new ItemStack(sourceStack.getItemId(), 1, sourceStack.getMetadata());
        if (!sinkContainer.canAddItemStackToSlot(targetSlot, moveStack, false, false)) {
            return false;
        }

        ItemStackSlotTransaction addTx = sinkContainer.addItemStackToSlot(targetSlot, moveStack);
        if (addTx == null
                || !addTx.succeeded()
                || countItemQuantity(sinkContainer, itemId) < sinkCountBefore + 1) {
            return false;
        }

        ItemStackSlotTransaction removeTx = sourceContainer.removeItemStackFromSlot(sourceSlot, 1);
        if (removeTx == null
                || !removeTx.succeeded()
                || countItemQuantity(sourceContainer, itemId) > sourceCountBefore - 1) {
            // Best-effort rollback; prevents source duplication if removal fails.
            sinkContainer.removeItemStackFromSlot(targetSlot, moveStack, 1, false, false);
            return false;
        }
        return true;
    }

    private int countItemQuantity(ItemContainer container, String itemId) {
        if (container == null || itemId == null || itemId.isEmpty()) {
            return 0;
        }
        int total = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack slotStack = container.getItemStack(slot);
            if (slotStack == null || ItemStack.isEmpty(slotStack)) {
                continue;
            }
            if (itemId.equals(slotStack.getItemId())) {
                total += Math.max(0, slotStack.getQuantity());
            }
        }
        return total;
    }

    private void sortEndpoints(CableNetwork network) {
        if (network == null) {
            return;
        }
        network.sources.sort(ENDPOINT_ORDER);
        network.sinks.sort(ENDPOINT_ORDER);
    }

    private List<SinkEndpoint>[] groupSinksByPriority(List<SinkEndpoint> sinks) {
        @SuppressWarnings("unchecked")
        List<SinkEndpoint>[] grouped = new List[ItemNodeComponent.MAX_PRIORITY + 1];
        if (sinks == null || sinks.isEmpty()) {
            return grouped;
        }
        for (SinkEndpoint sink : sinks) {
            if (sink == null) {
                continue;
            }
            int priority = ItemNodeComponent.clampPriority(sink.priority);
            List<SinkEndpoint> group = grouped[priority];
            if (group == null) {
                group = new ArrayList<>();
                grouped[priority] = group;
            }
            group.add(sink);
        }
        return grouped;
    }

    private int getRoundRobinIndex(CableState state, NetworkKey key, int priority) {
        if (state == null || key == null) {
            return 0;
        }
        Int2IntOpenHashMap priorities = state.roundRobinByNetwork.get(key);
        if (priorities == null) {
            priorities = new Int2IntOpenHashMap();
            state.roundRobinByNetwork.put(key, priorities);
        }
        return priorities.get(priority);
    }

    private void setRoundRobinIndex(CableState state, NetworkKey key, int priority, int index) {
        if (state == null || key == null) {
            return;
        }
        Int2IntOpenHashMap priorities = state.roundRobinByNetwork.get(key);
        if (priorities == null) {
            priorities = new Int2IntOpenHashMap();
            state.roundRobinByNetwork.put(key, priorities);
        }
        priorities.put(priority, index);
    }

    private boolean isEligibleSink(SourceEndpoint source, SinkEndpoint sink, ItemStack stack) {
        if (source == null || sink == null || stack == null) {
            return false;
        }
        if (sink.container == null) {
            return false;
        }
        if (!sink.allows(stack.getItemId())) {
            return false;
        }
        if (source.container == null || source.container == sink.container) {
            return false;
        }
        return !sameOwner(source, sink);
    }

    private boolean sameOwner(SourceEndpoint first, SinkEndpoint second) {
        if (first == null || second == null) {
            return false;
        }
        Vector3i a = first.position;
        Vector3i b = second.position;
        return a != null && a.equals(b);
    }

    private int resolveStoragePriority(
            World world,
            ChunkStore chunkStore,
            Vector3i pos,
            ItemNodeComponent fallbackNode) {
        int fallback = fallbackNode == null ? ItemNodeComponent.DEFAULT_PRIORITY : fallbackNode.getPriority();
        if (pos == null) {
            return fallback;
        }
        boolean hasBlockConfig = HyProTechComponents.ITEM_STORAGE != null;
        boolean hasChunkConfig = HyProTechComponents.ITEM_STORAGE_CHUNK != null;
        if (!hasBlockConfig && !hasChunkConfig) {
            return fallback;
        }
        Vector3i basePos = resolveBasePosition(world, pos);
        int x = basePos.getX();
        int y = basePos.getY();
        int z = basePos.getZ();
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return fallback;
        }
        boolean allowBlockConfig = false;
        if (world != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            BlockType blockType = world.getBlockType(x, y, z);
            String blockId = blockType == null ? null : blockType.getId();
            allowBlockConfig = isHyProTechBlockId(blockId);
            if (accessor != null) {
                if (!allowBlockConfig) {
                    cleanupStorageConfig(world, chunkStore, x, y, z);
                }
                Holder<ChunkStore> holder = accessor.getBlockComponentHolder(x, y, z);
                if (holder != null && hasBlockConfig && allowBlockConfig) {
                    ItemStorageConfigComponent config = holder.getComponent(HyProTechComponents.ITEM_STORAGE);
                    if (config != null) {
                        return config.getPriority();
                    }
                }
            }
        }
        if (chunkStore == null) {
            return fallback;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentChunk components =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (components != null && hasBlockConfig && allowBlockConfig) {
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            ItemStorageConfigComponent config = components.getComponent(
                    blockIndex,
                    HyProTechComponents.ITEM_STORAGE);
            if (config != null) {
                return config.getPriority();
            }
        }
        return resolveChunkStoragePriority(chunkStore, chunkIndex, x, y, z, fallback);
    }

    @SuppressWarnings("removal")
    private void cleanupStorageConfig(World world, ChunkStore chunkStore, int x, int y, int z) {
        if (world == null || chunkStore == null || HyProTechComponents.ITEM_STORAGE == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentChunk components =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (components == null) {
            return;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> ref = components.getEntityReference(blockIndex);
        if (ref != null) {
            if (store != null) {
                ItemStorageConfigComponent config = store.getComponent(ref, HyProTechComponents.ITEM_STORAGE);
                if (config != null) {
                    storeChunkStorageConfig(chunkStore, x, y, z, config);
                }
            }
            return;
        }
        Holder<ChunkStore> holder = components.getEntityHolder(blockIndex);
        if (holder == null) {
            return;
        }
        ItemStorageConfigComponent config = holder.getComponent(HyProTechComponents.ITEM_STORAGE);
        if (config == null) {
            return;
        }
        storeChunkStorageConfig(chunkStore, x, y, z, config);
    }

    private void storeChunkStorageConfig(
            ChunkStore chunkStore,
            int x,
            int y,
            int z,
            ItemStorageConfigComponent config) {
        if (chunkStore == null || HyProTechComponents.ITEM_STORAGE_CHUNK == null || config == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        ItemStorageConfigChunk chunkConfig =
                chunkStore.getChunkComponent(chunkIndex, HyProTechComponents.ITEM_STORAGE_CHUNK);
        if (chunkConfig == null) {
            chunkConfig = new ItemStorageConfigChunk();
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        chunkConfig.setConfig(blockIndex, config);
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (store != null && chunkRef != null) {
            store.putComponent(chunkRef, HyProTechComponents.ITEM_STORAGE_CHUNK, chunkConfig);
        }
    }

    private boolean isHyProTechBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        int colonIndex = blockId.indexOf(':');
        String normalized = colonIndex >= 0 ? blockId.substring(colonIndex + 1) : blockId;
        return normalized.regionMatches(true, 0, "HyProTech_", 0, "HyProTech_".length());
    }

    private int resolveChunkStoragePriority(
            ChunkStore chunkStore,
            long chunkIndex,
            int x,
            int y,
            int z,
            int fallback) {
        if (chunkStore == null || HyProTechComponents.ITEM_STORAGE_CHUNK == null) {
            return fallback;
        }
        ItemStorageConfigChunk chunkConfig =
                chunkStore.getChunkComponent(chunkIndex, HyProTechComponents.ITEM_STORAGE_CHUNK);
        if (chunkConfig == null) {
            return fallback;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        ItemStorageConfigComponent config = chunkConfig.getConfig(blockIndex);
        return config == null ? fallback : config.getPriority();
    }

    @SuppressWarnings("removal")
    private Vector3i resolveBasePosition(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return pos;
        }
        BlockPosition base = world.getBaseBlock(new BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
        if (base != null) {
            return new Vector3i(base.x, base.y, base.z);
        }
        return pos;
    }

    private static final Comparator<Endpoint> ENDPOINT_ORDER = ItemNetworkSystem::compareEndpoints;

    private static int compareEndpoints(Endpoint first, Endpoint second) {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        int cmp = Integer.compare(first.cableX, second.cableX);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(first.cableY, second.cableY);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(first.cableZ, second.cableZ);
        if (cmp != 0) {
            return cmp;
        }
        int firstSide = first.side == null ? -1 : first.side.ordinal();
        int secondSide = second.side == null ? -1 : second.side.ordinal();
        cmp = Integer.compare(firstSide, secondSide);
        if (cmp != 0) {
            return cmp;
        }
        Vector3i firstPos = first.position;
        Vector3i secondPos = second.position;
        if (firstPos == secondPos) {
            return 0;
        }
        if (firstPos == null) {
            return -1;
        }
        if (secondPos == null) {
            return 1;
        }
        cmp = Integer.compare(firstPos.getX(), secondPos.getX());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(firstPos.getY(), secondPos.getY());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(firstPos.getZ(), secondPos.getZ());
    }

    private static int compareCablePos(
            int firstX,
            int firstY,
            int firstZ,
            int secondX,
            int secondY,
            int secondZ) {
        int cmp = Integer.compare(firstX, secondX);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(firstY, secondY);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(firstZ, secondZ);
    }

    private boolean isItemCable(ChunkStore chunkStore, int x, int y, int z) {
        if (chunkStore == null || y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return false;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return false;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        return blockComponents.getComponent(blockIndex, itemType) != null;
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

    private ContainerLookup resolveContainerState(World world, ChunkStore chunkStore, int x, int y, int z) {
        Object state = getItemContainerState(world, x, y, z);
        if (state != null) {
            MachineSlotLayout layout = resolveMachineSlots(world, x, y, z, state);
            return new ContainerLookup(
                    state,
                    null,
                    null,
                    layout,
                    new Vector3i(x, y, z),
                    layout != null);
        }

        BlockType blockType = getBlockTypeIfLoaded(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return null;
        }
        String blockId = blockType.getId();
        if (blockId == null || blockId.isEmpty() || BlockType.EMPTY_KEY.equals(blockId)) {
            return null;
        }

        for (EnergySide side : EnergySide.VALUES) {
            if (side == EnergySide.UP || side == EnergySide.DOWN) {
                continue;
            }
            int nx = x + side.dx();
            int ny = y + side.dy();
            int nz = z + side.dz();
            if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                continue;
            }

            BlockType neighborType = getBlockTypeIfLoaded(world, nx, ny, nz);
            if (neighborType == null || neighborType == BlockType.EMPTY) {
                continue;
            }
            if (!sameBlockId(blockId, neighborType.getId())) {
                continue;
            }

            Object neighborState = getItemContainerState(world, nx, ny, nz);
            if (neighborState != null) {
                MachineSlotLayout layout = resolveMachineSlots(world, nx, ny, nz, neighborState);
                return new ContainerLookup(
                        neighborState,
                        null,
                        null,
                        layout,
                        new Vector3i(nx, ny, nz),
                        layout != null);
            }
        }

        return null;
    }

    private MachineSlotLayout resolveMachineSlots(
            World world,
            int x,
            int y,
            int z,
            Object state) {
        if (world == null || state == null) {
            return null;
        }
        BlockType blockType = getBlockTypeIfLoaded(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return null;
        }
        String blockId = blockType.getId();
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        ItemContainer container = MachineItemAccess.getItemContainerFromState(state);
        short capacity = container == null ? 0 : container.getCapacity();
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)) {
            int outputCount = Math.min(OreCrusherConfig.OUTPUT_SLOT_COUNT, Math.max(0, capacity - 1));
            SlotRange input = new SlotRange(0, capacity > 0 ? 1 : 0);
            SlotRange output = new SlotRange(1, outputCount);
            return new MachineSlotLayout(input, output);
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)) {
            int inputCount = Math.min(AlloySmelterConfig.INPUT_SLOT_COUNT, Math.max(0, capacity));
            int outputStart = inputCount;
            int outputCount = Math.min(AlloySmelterConfig.OUTPUT_SLOT_COUNT, Math.max(0, capacity - outputStart));
            SlotRange input = new SlotRange(0, inputCount);
            SlotRange output = new SlotRange(outputStart, outputCount);
            return new MachineSlotLayout(input, output);
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_QUARRY) && !isQuarryBorder(blockId)) {
            SlotRange input = new SlotRange(0, 0);
            SlotRange output = new SlotRange(0, capacity);
            return new MachineSlotLayout(input, output);
        }
        return null;
    }

    private BlockType getBlockTypeIfLoaded(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return null;
        }
        return accessor.getBlockType(x, y, z);
    }

    private boolean sameBlockId(String firstId, String secondId) {
        if (firstId == null || secondId == null) {
            return false;
        }
        return firstId.equalsIgnoreCase(secondId);
    }

    private boolean isQuarryBorder(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_QUARRY_BORDER;
        return blockId.equalsIgnoreCase(baseId)
                || blockId.regionMatches(true, 0, baseId, 0, baseId.length());
    }

    private Object getItemContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        return MachineItemAccess.getContainerState(world, x, y, z);
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

    private static final class ContainerLookup {
        private final Object state;
        private final ItemContainer inputContainer;
        private final ItemContainer outputContainer;
        private final MachineSlotLayout machineSlots;
        private final Vector3i position;
        private final boolean isMachine;

        private ContainerLookup(
                Object state,
                ItemContainer inputContainer,
                ItemContainer outputContainer,
                MachineSlotLayout machineSlots,
                Vector3i position,
                boolean isMachine) {
            this.state = state;
            this.inputContainer = inputContainer;
            this.outputContainer = outputContainer;
            this.machineSlots = machineSlots;
            this.position = position;
            this.isMachine = isMachine;
        }
    }

    private static final class CableState {
        private long lastTick = Long.MIN_VALUE;
        private long tickStartNanos = 0L;
        private int networksProcessed = 0;
        private final Long2ObjectMap<IntOpenHashSet> visitedByChunk = new Long2ObjectOpenHashMap<>();
        private final Map<NetworkKey, Int2IntOpenHashMap> roundRobinByNetwork = new HashMap<>();
    }

    private static final class CableNetwork {
        private final List<CableNode> cables = new ArrayList<>();
        private final List<SourceEndpoint> sources = new ArrayList<>();
        private final List<SinkEndpoint> sinks = new ArrayList<>();
        private final Long2ObjectMap<Int2IntOpenHashMap> cableIndexByChunk = new Long2ObjectOpenHashMap<>();
        private int maxTransfer = Integer.MAX_VALUE;
        private int anchorX;
        private int anchorY;
        private int anchorZ;
        private boolean hasAnchor;
        private NetworkKey networkKey;

        private void addCable(
                ItemNodeComponent node,
                int x,
                int y,
                int z,
                long chunkIndex,
                int blockIndex,
                int defaultMaxTransfer) {
            int cableIndex = cables.size();
            cables.add(new CableNode(node, x, y, z, chunkIndex));
            Int2IntOpenHashMap indices = cableIndexByChunk.get(chunkIndex);
            if (indices == null) {
                indices = new Int2IntOpenHashMap();
                cableIndexByChunk.put(chunkIndex, indices);
            }
            indices.put(blockIndex, cableIndex);
            int max = node.getMaxTransfer();
            if (max <= 0) {
                max = defaultMaxTransfer;
            }
            maxTransfer = Math.min(maxTransfer, max);
            if (!hasAnchor || compareCablePos(x, y, z, anchorX, anchorY, anchorZ) < 0) {
                anchorX = x;
                anchorY = y;
                anchorZ = z;
                hasAnchor = true;
            }
        }

        private int getCableIndex(int x, int y, int z) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            Int2IntMap indices = cableIndexByChunk.get(chunkIndex);
            if (indices == null) {
                return -1;
            }
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            if (!indices.containsKey(blockIndex)) {
                return -1;
            }
            return indices.get(blockIndex);
        }

        private NetworkKey getNetworkKey() {
            if (networkKey == null) {
                networkKey = new NetworkKey(anchorX, anchorY, anchorZ);
            }
            return networkKey;
        }
    }

    private static final class CableNode {
        private final ItemNodeComponent node;
        private final int x;
        private final int y;
        private final int z;
        private final long chunkIndex;

        private CableNode(ItemNodeComponent node, int x, int y, int z, long chunkIndex) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkIndex = chunkIndex;
        }
    }

    private static class Endpoint {
        final ItemContainer container;
        final Vector3i position;
        final boolean isMachine;
        final SlotRange slotRange;
        final Set<String> filters;
        final FilterMode filterMode;
        final int cableIndex;
        final int cableX;
        final int cableY;
        final int cableZ;
        final EnergySide side;

        private Endpoint(
                ItemContainer container,
                Vector3i position,
                boolean isMachine,
                SlotRange slotRange,
                Set<String> filters,
                FilterMode filterMode,
                int cableIndex,
                int cableX,
                int cableY,
                int cableZ,
                EnergySide side) {
            this.container = container;
            this.position = position;
            this.isMachine = isMachine;
            this.slotRange = slotRange;
            this.filters = filters;
            this.filterMode = filterMode == null ? FilterMode.WHITELIST : filterMode;
            this.cableIndex = cableIndex;
            this.cableX = cableX;
            this.cableY = cableY;
            this.cableZ = cableZ;
            this.side = side;
        }

        boolean allows(String itemId) {
            if (itemId == null || itemId.isEmpty()) {
                return false;
            }
            if (filters == null || filters.isEmpty()) {
                return true;
            }
            boolean contains = filters.contains(itemId);
            return filterMode == FilterMode.BLACKLIST ? !contains : contains;
        }

        boolean allowsSlot(short slot) {
            return slotRange == null || slotRange.contains(slot);
        }
    }

    private static final class SourceEndpoint extends Endpoint {
        private final ItemDistributionMode distributionMode;

        private SourceEndpoint(
                ItemContainer container,
                Vector3i position,
                boolean isMachine,
                SlotRange slotRange,
                Set<String> filters,
                FilterMode filterMode,
                ItemDistributionMode distributionMode,
                int cableIndex,
                int cableX,
                int cableY,
                int cableZ,
                EnergySide side) {
            super(container, position, isMachine, slotRange, filters, filterMode, cableIndex, cableX, cableY, cableZ, side);
            this.distributionMode = distributionMode == null
                    ? ItemDistributionMode.ROUND_ROBIN
                    : distributionMode;
        }
    }

    private static final class SinkEndpoint extends Endpoint {
        private final int priority;

        private SinkEndpoint(
                ItemContainer container,
                Vector3i position,
                boolean isMachine,
                SlotRange slotRange,
                Set<String> filters,
                FilterMode filterMode,
                int priority,
                int cableIndex,
                int cableX,
                int cableY,
                int cableZ,
                EnergySide side) {
            super(container, position, isMachine, slotRange, filters, filterMode, cableIndex, cableX, cableY, cableZ, side);
            this.priority = ItemNodeComponent.clampPriority(priority);
        }
    }

    private static final class SlotRange {
        private final int start;
        private final int count;

        private SlotRange(int start, int count) {
            this.start = Math.max(0, start);
            this.count = Math.max(0, count);
        }

        private boolean contains(short slot) {
            int value = slot;
            return value >= start && value < start + count;
        }

        private boolean isEmpty() {
            return count <= 0;
        }
    }

    private static final class MachineSlotLayout {
        private final SlotRange inputRange;
        private final SlotRange outputRange;

        private MachineSlotLayout(SlotRange inputRange, SlotRange outputRange) {
            this.inputRange = inputRange;
            this.outputRange = outputRange;
        }
    }

    private static final class NetworkKey {
        private final int x;
        private final int y;
        private final int z;
        private final int hash;

        private NetworkKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            int result = 17;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NetworkKey)) {
                return false;
            }
            NetworkKey other = (NetworkKey) obj;
            return x == other.x && y == other.y && z == other.z;
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
}
