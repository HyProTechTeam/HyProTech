package HyProTechTeam;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.energy.CableUpgradeConfig;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.SolarUpgradeConfig;
import HyProTechTeam.energy.WindUpgradeConfig;
import HyProTechTeam.furnace.FurnaceConfig;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.HyProTechComponents;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Preserves upgrade state and inventories across block drops, placement and tier swaps. */
public final class UpgradePersistence {
    public static final String META_ENERGY = "HyProTechEnergyNode";
    public static final String META_ITEM = "HyProTechItemNode";
    private static final boolean DEBUG_CABLE_UPGRADES = false;
    private static final boolean DEBUG_PERSISTENCE = false;
    private static final int PENDING_TTL_TICKS = 20;
    private static final int PENDING_DROP_TTL_TICKS = 20 * 60 * 60;
    // Keep pending state separate per world instance; copied positions are stable map keys.
    private static final Map<World, Map<Vector3i, PendingUpgrade>> PENDING = new IdentityHashMap<>();
    private static final Map<World, Long> LAST_CLEANUP = new IdentityHashMap<>();
    private static final Map<World, Map<Vector3i, PendingDrops>> PENDING_DROPS = new IdentityHashMap<>();
    private static final Map<World, Long> LAST_DROP_CLEANUP = new IdentityHashMap<>();

    private UpgradePersistence() {
    }

    /** Recognizes the supported base block IDs as well as their state variants. */
    public static boolean isUpgradeableBlockId(String blockId) {
        if (blockId == null) {
            return false;
        }
        return isIdOrState(blockId, HyProTechIds.BLOCK_SOLAR_PANEL)
                || isIdOrState(blockId, HyProTechIds.BLOCK_WIND_TURBINE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_BATTERY)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER);
    }

    /** Builds the appropriate tiered block item and attaches serializable node snapshots. */
    public static ItemStack buildDropStack(World world, Vector3i pos, BlockType blockType, String blockId) {
        EnergyNodeComponent energyNode = null;
        ItemNodeComponent itemNode = null;
        int furnaceTierIndex = -1;
        if (world != null && pos != null) {
            if (usesEnergyNode(blockId)) {
                energyNode = getEnergyNode(world, pos);
            }
            if (isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE)) {
                itemNode = getItemNode(world, pos);
            }
            furnaceTierIndex = getFurnaceTierIndex(world, pos, blockId);
        }

        // Prefer the item corresponding to the installed tier; fall back to the block's item.
        String itemId = resolveTieredItemId(blockId, energyNode, itemNode, furnaceTierIndex);
        if (itemId == null || itemId.isEmpty()) {
            itemId = resolveItemId(blockType, blockId);
        }
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }

        ItemStack stack = new ItemStack(itemId, 1);
        if (world == null || pos == null) {
            return stack;
        }

        // Store detached snapshots so later changes to the live block do not alter the drop.
        EnergyNodeComponent energySnapshot = snapshotEnergy(energyNode);
        if (energySnapshot != null) {
            stack = stack.withMetadata(META_ENERGY, EnergyNodeComponent.CODEC, energySnapshot);
        }
        ItemNodeComponent itemSnapshot = snapshotItem(itemNode);
        if (itemSnapshot != null) {
            stack = stack.withMetadata(META_ITEM, ItemNodeComponent.CODEC, itemSnapshot);
        }
        return stack;
    }

    /** Packages supplied node state into the metadata used during a block replacement. */
    public static ItemStack buildUpgradeStack(
            String itemId,
            EnergyNodeComponent energyNode,
            ItemNodeComponent itemNode) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ItemStack stack = new ItemStack(itemId, 1);
        if (energyNode != null) {
            EnergyNodeComponent snapshot = (EnergyNodeComponent) energyNode.cloneSerializable();
            stack = stack.withMetadata(META_ENERGY, EnergyNodeComponent.CODEC, snapshot);
        }
        if (itemNode != null) {
            ItemNodeComponent snapshot = (ItemNodeComponent) itemNode.cloneSerializable();
            stack = stack.withMetadata(META_ITEM, ItemNodeComponent.CODEC, snapshot);
        }
        return stack;
    }

    /** Schedules a block replacement on the world thread while carrying node metadata forward. */
    public static void queueBlockSwap(
            World world,
            Vector3i pos,
            String newBlockId,
            EnergyNodeComponent energyNode,
            ItemNodeComponent itemNode) {
        if (world == null || pos == null || newBlockId == null || newBlockId.isEmpty()) {
            return;
        }
        boolean debug = DEBUG_CABLE_UPGRADES && isCableUpgradeId(newBlockId);
        if (debug) {
            System.out.println("[HyProTech] queueBlockSwap cable pos=" + pos
                    + " newId=" + newBlockId);
        }
        ItemStack stack = buildUpgradeStack(newBlockId, energyNode, itemNode);
        if (stack != null) {
            storePending(world, pos, newBlockId, stack);
        }

        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                if (debug) {
                    System.out.println("[HyProTech] queueBlockSwap skipped (chunk not loaded) pos=" + pos);
                }
                return;
            }
            BlockType current = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String currentId = current == null ? null : current.getId();
            if (debug) {
                System.out.println("[HyProTech] queueBlockSwap currentId=" + currentId
                        + " newId=" + newBlockId + " pos=" + pos);
            }
            if (currentId != null
                    && (currentId.equalsIgnoreCase(newBlockId)
                            || isIdOrState(currentId, newBlockId)
                            || isCableTierMatch(currentId, newBlockId))) {
                if (debug) {
                    System.out.println("[HyProTech] queueBlockSwap skipped (already same) pos=" + pos);
                }
                return;
            }
            // Remove old components before installing the new block, then restore its state.
            clearBlockComponents(world, pos);
            int rotationIndex = world.getBlockRotationIndex(pos.getX(), pos.getY(), pos.getZ());
            setBlockWithRotation(accessor, pos.getX(), pos.getY(), pos.getZ(), newBlockId, rotationIndex);
            ensureBlockState(world, pos.getX(), pos.getY(), pos.getZ());
            applyPendingComponents(world, pos);
            cleanupInvalidChunkReferences(world, pos);
            if (debug) {
                System.out.println("[HyProTech] queueBlockSwap setBlock done pos=" + pos
                        + " newId=" + newBlockId);
            }
        });
    }

    /** Replaces a block while preserving the contents of its item container. */
    public static void queueBlockSwapWithContainer(
            World world,
            Vector3i pos,
            String newBlockId,
            EnergyNodeComponent energyNode) {
        if (world == null || pos == null || newBlockId == null || newBlockId.isEmpty()) {
            return;
        }
        ItemStack stack = buildUpgradeStack(newBlockId, energyNode, null);
        if (stack != null) {
            storePending(world, pos, newBlockId, stack);
        }

        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                return;
            }
            BlockType current = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String currentId = current == null ? null : current.getId();
            if (currentId != null && currentId.equalsIgnoreCase(newBlockId)) {
                return;
            }

            // Capture contents before clearing components; restore them after the new state exists.
            List<ItemStack> items = snapshotContainerItems(world, pos.getX(), pos.getY(), pos.getZ());
            clearBlockComponents(world, pos);
            int rotationIndex = world.getBlockRotationIndex(pos.getX(), pos.getY(), pos.getZ());
            setBlockWithRotation(accessor, pos.getX(), pos.getY(), pos.getZ(), newBlockId, rotationIndex);
            ensureBlockState(world, pos.getX(), pos.getY(), pos.getZ());
            applyPendingComponents(world, pos);
            cleanupInvalidChunkReferences(world, pos);
            if (items != null && !items.isEmpty()) {
                restoreContainerItems(world, pos.getX(), pos.getY(), pos.getZ(), items);
            }
        });
    }

    /** Replaces a crafting bench and restores the inventory held by its bench components. */
    public static void queueBlockSwapWithBench(
            World world,
            Vector3i pos,
            String newBlockId,
            EnergyNodeComponent energyNode) {
        if (world == null || pos == null || newBlockId == null || newBlockId.isEmpty()) {
            return;
        }
        ItemStack stack = buildUpgradeStack(newBlockId, energyNode, null);
        if (stack != null) {
            storePending(world, pos, newBlockId, stack);
        }

        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                return;
            }
            BlockType current = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String currentId = current == null ? null : current.getId();
            if (currentId != null && currentId.equalsIgnoreCase(newBlockId)) {
                return;
            }

            // Bench inventories are separate from ordinary block containers.
            List<ItemStack> items = snapshotBenchItems(world, pos.getX(), pos.getY(), pos.getZ());
            clearBlockComponents(world, pos);
            int rotationIndex = world.getBlockRotationIndex(pos.getX(), pos.getY(), pos.getZ());
            setBlockWithRotation(accessor, pos.getX(), pos.getY(), pos.getZ(), newBlockId, rotationIndex);
            ensureBlockState(world, pos.getX(), pos.getY(), pos.getZ());
            applyPendingComponents(world, pos);
            cleanupInvalidChunkReferences(world, pos);
            if (items != null && !items.isEmpty()) {
                restoreBenchItems(world, pos.getX(), pos.getY(), pos.getZ(), items);
            }
        });
    }

    /** Schedules a break using inventories captured at execution time. */
    public static void queueBreakAndDrop(World world, Vector3i pos, BlockType expectedType, ItemStack drop) {
        queueBreakAndDrop(world, pos, expectedType, drop, null);
    }

    /** Collects machine and block-container contents before their components disappear. */
    public static List<ItemStack> snapshotBreakDrops(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        List<ItemStack> machineDrops = snapshotMachineItems(world, pos);
        if (machineDrops != null && !machineDrops.isEmpty()) {
            drops.addAll(machineDrops);
        }
        List<ItemStack> blockDrops = snapshotContainerItems(world, pos.getX(), pos.getY(), pos.getZ());
        if (blockDrops != null && !blockDrops.isEmpty()) {
            drops.addAll(blockDrops);
        }
        return drops;
    }

    /** Breaks the expected block on the world thread and spawns its item and contents. */
    public static void queueBreakAndDrop(
            World world,
            Vector3i pos,
            BlockType expectedType,
            ItemStack drop,
            List<ItemStack> precomputedDrops) {
        if (world == null || pos == null || drop == null) {
            return;
        }
        final List<ItemStack> finalDrops = precomputedDrops == null ? null : new ArrayList<>(precomputedDrops);
        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                return;
            }
            // Refuse to remove a different nonempty block if the position changed in the meantime.
            BlockType actual = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            if (expectedType != null && !sameBlockId(actual, expectedType)) {
                if (actual != null && actual != BlockType.EMPTY) {
                    return;
                }
            }
            List<ItemStack> drops = finalDrops;
            if (drops == null || drops.isEmpty()) {
                drops = snapshotBreakDrops(world, pos);
            }
            if (actual != null && actual != BlockType.EMPTY) {
                accessor.setBlock(pos.getX(), pos.getY(), pos.getZ(), BlockType.EMPTY);
            }
            clearBlockComponents(world, pos);
            cleanupInvalidChunkReferences(world, pos);
            spawnDrop(world, drop, pos);
            spawnDropsAt(world, drops, pos);
        });
    }

    /** Retains nonempty inventory stacks temporarily for the matching block-break event. */
    public static void cacheBreakDrops(World world, Vector3i pos, List<ItemStack> drops) {
        if (world == null || pos == null || drops == null || drops.isEmpty()) {
            return;
        }
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            filtered.add(stack);
        }
        if (filtered.isEmpty()) {
            return;
        }
        long tick = world.getTick();
        synchronized (PENDING_DROPS) {
            cleanupDropCache(world, tick);
            PENDING_DROPS.computeIfAbsent(world, key -> new HashMap<>())
                    .put(new Vector3i(pos), new PendingDrops(tick, filtered));
        }
    }

    /** Snapshots both machine containers, clearing stale cached drops when they are empty. */
    public static void cacheBreakDropsFromContainers(
            World world,
            Vector3i pos,
            ItemContainer input,
            ItemContainer output) {
        if (world == null || pos == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        collectContainerStacks(input, stacks);
        collectContainerStacks(output, stacks);
        if (stacks.isEmpty()) {
            clearBreakDrops(world, pos);
            return;
        }
        cacheBreakDrops(world, pos, stacks);
    }

    /** Discards any cached inventory drops for this location. */
    public static void clearBreakDrops(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        synchronized (PENDING_DROPS) {
            Map<Vector3i, PendingDrops> byPos = PENDING_DROPS.get(world);
            if (byPos == null || byPos.isEmpty()) {
                return;
            }
            byPos.remove(new Vector3i(pos));
            if (byPos.isEmpty()) {
                PENDING_DROPS.remove(world);
            }
        }
    }

    /** Removes and returns unexpired cached drops, so the same contents cannot be consumed twice. */
    public static List<ItemStack> consumeBreakDrops(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return null;
        }
        long tick = world.getTick();
        synchronized (PENDING_DROPS) {
            cleanupDropCache(world, tick);
            Map<Vector3i, PendingDrops> byPos = PENDING_DROPS.get(world);
            if (byPos == null || byPos.isEmpty()) {
                return null;
            }
            PendingDrops pending = byPos.remove(new Vector3i(pos));
            if (byPos.isEmpty()) {
                PENDING_DROPS.remove(world);
            }
            if (pending == null || tick - pending.createdTick > PENDING_DROP_TTL_TICKS) {
                return null;
            }
            return pending.drops;
        }
    }

    /** Records metadata from an item until the corresponding block components can be restored. */
    public static void storePending(World world, Vector3i pos, String blockId, ItemStack stack) {
        if (world == null || pos == null || stack == null) {
            return;
        }
        EnergyNodeComponent energy = stack.getFromMetadataOrNull(META_ENERGY, EnergyNodeComponent.CODEC);
        ItemNodeComponent item = stack.getFromMetadataOrNull(META_ITEM, ItemNodeComponent.CODEC);
        if (energy == null && item == null) {
            return;
        }
        PendingUpgrade pending = new PendingUpgrade(blockId, energy, item, world.getTick());
        synchronized (PENDING) {
            PENDING.computeIfAbsent(world, key -> new HashMap<>())
                    .put(new Vector3i(pos), pending);
        }
    }

    /** Applies unexpired energy metadata only after the expected block appears at this position. */
    public static boolean applyEnergyUpgrade(
            World world,
            int x,
            int y,
            int z,
            EnergyNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        PendingUpgrade pending = getPending(world, x, y, z);
        if (pending == null || pending.energy == null) {
            return false;
        }
        if (isExpired(world, pending)) {
            removePending(world, x, y, z);
            return false;
        }
        if (pending.blockId != null && !blockIdMatches(world, x, y, z, pending.blockId)) {
            return false;
        }

        // Consume each component independently; another component may still need restoration.
        if (applyEnergy(node, pending.energy)) {
            pending.energy = null;
            if (pending.item == null) {
                removePending(world, x, y, z);
            }
            return true;
        }
        return false;
    }

    /** Restores the pending item-node settings and consumes that portion of the snapshot. */
    public static boolean applyItemUpgrade(
            World world,
            int x,
            int y,
            int z,
            ItemNodeComponent node) {
        if (world == null || node == null) {
            return false;
        }
        PendingUpgrade pending = getPending(world, x, y, z);
        if (pending == null || pending.item == null) {
            return false;
        }
        if (isExpired(world, pending)) {
            removePending(world, x, y, z);
            return false;
        }
        if (pending.blockId != null && !blockIdMatches(world, x, y, z, pending.blockId)) {
            return false;
        }

        node.applyFrom(pending.item);
        pending.item = null;
        if (pending.energy == null) {
            removePending(world, x, y, z);
        }
        return true;
    }

    /** Finds the first world with a loaded position matching the optional expected block type. */
    public static World findWorld(Vector3i pos, BlockType expectedType) {
        if (pos == null) {
            return null;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }
        for (World world : universe.getWorlds().values()) {
            if (world == null) {
                continue;
            }
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                continue;
            }
            if (expectedType != null) {
                BlockType actual = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
                if (!sameBlockId(actual, expectedType)) {
                    continue;
                }
            }
            return world;
        }
        return null;
    }

    /** Selects block types whose dropped items can carry energy-node settings. */
    private static boolean usesEnergyNode(String blockId) {
        if (blockId == null) {
            return false;
        }
        return isIdOrState(blockId, HyProTechIds.BLOCK_SOLAR_PANEL)
                || isIdOrState(blockId, HyProTechIds.BLOCK_WIND_TURBINE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_BATTERY)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER);
    }

    /** Uses the block's associated item ID when available, otherwise the block ID itself. */
    private static String resolveItemId(BlockType blockType, String blockId) {
        Item item = blockType == null ? null : blockType.getItem();
        if (item != null && item.getId() != null && !item.getId().isEmpty()) {
            return item.getId();
        }
        return blockId;
    }

    /** Resolves the dropped tier from the component when available, or from the block ID. */
    private static String resolveTieredItemId(
            String blockId,
            EnergyNodeComponent energyNode,
            ItemNodeComponent itemNode,
            int furnaceTierIndex) {
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_SOLAR_PANEL)) {
            int tier = energyNode != null
                    ? SolarUpgradeConfig.clampTier(energyNode.getSolarTier())
                    : TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_SOLAR_PANEL);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_SOLAR_PANEL, tier);
            }
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_WIND_TURBINE)) {
            int tier = energyNode != null
                    ? WindUpgradeConfig.clampTier(energyNode.getWindTier())
                    : TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_WIND_TURBINE);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_WIND_TURBINE, tier);
            }
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)) {
            int tier = energyNode != null
                    ? CableUpgradeConfig.clampTier(energyNode.getCableTier())
                    : parseCableTierFromBlockId(blockId, HyProTechIds.BLOCK_ENERGY_CABLE);
            if (tier >= 0) {
                return buildEnergyCableTieredId(tier);
            }
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE)) {
            int tier = itemNode != null
                    ? CableUpgradeConfig.clampTier(itemNode.getCableTier())
                    : parseCableTierFromBlockId(blockId, HyProTechIds.BLOCK_ITEM_CABLE);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ITEM_CABLE, tier);
            }
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            int tier = furnaceTierIndex >= 0
                    ? furnaceTierIndex
                    : TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ELECTRIC_FURNACE, tier);
            }
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)) {
            int tier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ORE_CRUSHER);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ORE_CRUSHER, tier);
            }
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)) {
            int tier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ALLOY_SMELTER, tier);
            }
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_BATTERY)) {
            int tier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_BATTERY);
            if (tier >= 0) {
                return TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_BATTERY, tier);
            }
        }
        return null;
    }

    /** Reads cable tiers from either a block ID or one of its state IDs. */
    private static int parseCableTierFromBlockId(String blockId, String baseId) {
        return BlockIdUtil.parseCableTierFromIdOrState(blockId, baseId);
    }

    /** Converts the furnace bench's one-based tier to a zero-based item tier. */
    private static int getFurnaceTierIndex(World world, Vector3i pos, String blockId) {
        if (world == null || pos == null) {
            return -1;
        }
        if (!TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            return -1;
        }
        ProcessingBenchBlock benchBlock = BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ());
        if (benchBlock == null) {
            return -1;
        }
        BenchBlock benchMeta = BlockModule.get().getComponent(
                BenchBlock.getComponentType(),
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ());
        int tier = benchMeta != null ? FurnaceConfig.clampTier(benchMeta.getTierLevel()) : 1;
        return Math.max(0, tier - 1);
    }

    /** Looks up the energy component through the block's local index in its chunk column. */
    private static EnergyNodeComponent getEnergyNode(World world, Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        return blockComponents.getComponent(blockIndex, HyProTechComponents.ENERGY);
    }

    /** Looks up the item-network component at the block's local chunk index. */
    private static ItemNodeComponent getItemNode(World world, Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        return blockComponents.getComponent(blockIndex, HyProTechComponents.ITEM);
    }

    /** Captures the settings that belong on a dropped block for its particular node type. */
    private static EnergyNodeComponent snapshotEnergy(EnergyNodeComponent node) {
        if (node == null) {
            return null;
        }
        EnergyNodeComponent snapshot = new EnergyNodeComponent();
        snapshot.setNodeType(node.getNodeType());
        switch (node.getNodeType()) {
            case CABLE:
                snapshot.setCableTier(node.getCableTier());
                snapshot.setCableColor(node.getCableColor());
                snapshot.setInputMask(node.getInputMask());
                snapshot.setOutputMask(node.getOutputMask());
                break;
            case SOLAR:
                snapshot.setSolarTier(node.getSolarTier());
                break;
            case WIND:
                snapshot.setWindTier(node.getWindTier());
                break;
            case BATTERY:
            case MACHINE:
            case QUARRY:
                snapshot.setInputMask(node.getInputMask());
                snapshot.setOutputMask(node.getOutputMask());
                break;
            default:
                break;
        }
        return snapshot;
    }

    /** Copies serializable item-network settings without retaining the live component. */
    private static ItemNodeComponent snapshotItem(ItemNodeComponent node) {
        return node == null ? null : (ItemNodeComponent) node.cloneSerializable();
    }

    /** Restores a full upgrade snapshot or the smaller type-specific drop snapshot. */
    private static boolean applyEnergy(EnergyNodeComponent target, EnergyNodeComponent snapshot) {
        if (target == null || snapshot == null) {
            return false;
        }
        if (hasFullSnapshot(snapshot)) {
            applyFullSnapshot(target, snapshot);
            return true;
        }
        switch (target.getNodeType()) {
            case CABLE:
                target.setCableTier(snapshot.getCableTier());
                target.setCableColor(snapshot.getCableColor());
                target.setInputMask(snapshot.getInputMask());
                target.setOutputMask(snapshot.getOutputMask());
                return true;
            case SOLAR:
                target.setSolarTier(snapshot.getSolarTier());
                return true;
            case WIND:
                target.setWindTier(snapshot.getWindTier());
                return true;
            case BATTERY:
            case MACHINE:
            case QUARRY:
                target.setInputMask(snapshot.getInputMask());
                target.setOutputMask(snapshot.getOutputMask());
                return true;
            default:
                return false;
        }
    }

    /** Distinguishes snapshots carrying runtime energy fields from settings-only snapshots. */
    private static boolean hasFullSnapshot(EnergyNodeComponent snapshot) {
        return snapshot.getCapacity() > 0 || snapshot.getMaxTransfer() > 0 || snapshot.getEnergy() > 0;
    }

    /** Copies energy, machine progress and configuration into the replacement component. */
    private static void applyFullSnapshot(EnergyNodeComponent target, EnergyNodeComponent snapshot) {
        target.setEnergy(snapshot.getEnergy());
        target.setCapacity(snapshot.getCapacity());
        target.setMaxTransfer(snapshot.getMaxTransfer());
        target.setGeneration(snapshot.getGeneration());
        target.setConsumption(snapshot.getConsumption());
        target.setFurnaceBaseConsumption(snapshot.getFurnaceBaseConsumption());
        target.setProgress(snapshot.getProgress());
        target.setProgressMax(snapshot.getProgressMax());
        target.setInputMask(snapshot.getInputMask());
        target.setOutputMask(snapshot.getOutputMask());
        target.setCableColor(snapshot.getCableColor());
        target.setCableTier(snapshot.getCableTier());
        target.setSolarTier(snapshot.getSolarTier());
        target.setWindTier(snapshot.getWindTier());
        target.setEnabled(snapshot.isEnabled());
    }

    /** Spawns a single item entity at the center of the broken block. */
    private static void spawnDrop(World world, ItemStack stack, Vector3i pos) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vector3f velocity = new Vector3f(0f, 0.1f, 0f);
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                store,
                List.of(stack),
                position,
                velocity);
        store.addEntities(holders, AddReason.SPAWN);
    }

    /** Filters empty stacks and spawns the remaining inventory contents at the block position. */
    public static void spawnDropsAt(World world, List<ItemStack> stacks, Vector3i pos) {
        if (world == null || pos == null || stacks == null || stacks.isEmpty()) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            drops.add(stack);
        }
        if (drops.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vector3f velocity = new Vector3f(0f, 0.1f, 0f);
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                store,
                drops,
                position,
                velocity);
        store.addEntities(holders, AddReason.SPAWN);
    }

    /** Returns the available machine-container snapshot, or an empty list if absent. */
    private static List<ItemStack> snapshotMachineItems(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return List.of();
        }
        List<ItemStack> fallback = snapshotMachineContainerState(world, pos.getX(), pos.getY(), pos.getZ());
        return fallback == null ? List.of() : fallback;
    }

    /** Reads the machine's current block-container state. */
    private static List<ItemStack> snapshotMachineContainerState(World world, int x, int y, int z) {
        return snapshotContainerItems(world, x, y, z);
    }

    /** Copies nonempty stacks, including their metadata, into a drop list. */
    private static void collectContainerStacks(ItemContainer container, List<ItemStack> out) {
        if (container == null || out == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            out.add(new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata()));
        }
    }

    /** Emits persistence diagnostics only when the local debug flag is enabled. */
    private static void logPersistence(String message) {
        if (!DEBUG_PERSISTENCE) {
            return;
        }
        System.out.println("[HyProTech] PERSIST " + message);
    }

    /** Treats a missing container or one containing only empty stacks as empty. */
    private static boolean isContainerEmpty(ItemContainer container) {
        if (container == null) {
            return true;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !ItemStack.isEmpty(stack)) {
                return false;
            }
        }
        return true;
    }

    /** Clears an existing container. */
    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        container.clear();
    }

    /** Copies occupied slots shared by both container capacities. */
    private static void copyContainer(ItemContainer source, ItemContainer target) {
        if (source == null || target == null) {
            return;
        }
        short limit = (short) Math.min(source.getCapacity(), target.getCapacity());
        for (short slot = 0; slot < limit; slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            ItemStack copy = new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata());
            target.addItemStackToSlot(slot, copy);
        }
    }

    /** Resolves a block asset and installs it with the previous block's rotation. */
    private static void setBlockWithRotation(
            BlockAccessor accessor,
            int x,
            int y,
            int z,
            String blockId,
            int rotationIndex) {
        if (accessor == null || blockId == null || blockId.isEmpty()) {
            return;
        }
        int blockIndex = BlockType.getAssetMap().getIndex(blockId);
        if (blockIndex == Integer.MIN_VALUE) {
            return;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockIndex);
        if (blockType == null) {
            return;
        }
        accessor.setBlock(x, y, z, blockIndex, blockType, rotationIndex, 0, 0);
    }

    /** Reads the pending entry for a world and block position under the map lock. */
    private static PendingUpgrade getPending(World world, int x, int y, int z) {
        synchronized (PENDING) {
            Map<Vector3i, PendingUpgrade> byPos = PENDING.get(world);
            if (byPos == null) {
                return null;
            }
            return byPos.get(new Vector3i(x, y, z));
        }
    }

    /** Removes a pending entry and its world map when no entries remain. */
    private static void removePending(World world, int x, int y, int z) {
        synchronized (PENDING) {
            Map<Vector3i, PendingUpgrade> byPos = PENDING.get(world);
            if (byPos == null) {
                return;
            }
            byPos.remove(new Vector3i(x, y, z));
            if (byPos.isEmpty()) {
                PENDING.remove(world);
            }
        }
    }

    /** Limits how long placement metadata can be reused after it was queued. */
    private static boolean isExpired(World world, PendingUpgrade pending) {
        long tick = world.getTick();
        return tick - pending.createdTick > PENDING_TTL_TICKS;
    }

    /** Retains the initialization hook; the server now initializes block state automatically. */
    private static void ensureBlockState(World world, int x, int y, int z) {
        // no-op: block state initialization is automatic in the new server API
    }

    /** Detaches the old entity reference or holder before replacing the block. */
    private static void clearBlockComponents(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
        if (ref != null) {
            blockComponents.removeEntityReference(blockIndex, ref);
        }
        Holder<ChunkStore> holder = blockComponents.getEntityHolder(blockIndex);
        if (holder != null) {
            blockComponents.removeEntityHolder(blockIndex);
        }
        if (ref != null || holder != null) {
            blockComponents.markNeedsSaving();
        }
    }

    /** Cleans invalid component references in the chunk containing this position. */
    public static void cleanupInvalidChunkReferences(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        cleanupInvalidChunkReferences(world, chunkIndex);
    }

    /** Collects invalid references before removing them, avoiding mutation during iteration. */
    public static void cleanupInvalidChunkReferences(World world, long chunkIndex) {
        if (world == null) {
            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return;
        }
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return;
        }

        IntArrayList invalid = null;
        for (it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry<Ref<ChunkStore>> entry : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            Ref<ChunkStore> ref = entry.getValue();
            if (ref == null || !ref.isValid()) {
                if (invalid == null) {
                    invalid = new IntArrayList();
                }
                invalid.add(entry.getIntKey());
            }
        }

        if (invalid == null || invalid.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < invalid.size(); i++) {
            int blockIndex = invalid.getInt(i);
            Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
            if (ref == null || !ref.isValid()) {
                if (ref != null) {
                    blockComponents.removeEntityReference(blockIndex, ref);
                }
                changed = true;
            }
        }
        if (changed) {
            blockComponents.markNeedsSaving();
        }
    }

    /** Installs pending components into an existing entity or a newly created chunk holder. */
    private static void applyPendingComponents(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        PendingUpgrade pending = getPending(world, pos.getX(), pos.getY(), pos.getZ());
        if (pending == null) {
            return;
        }
        if (isExpired(world, pending)) {
            removePending(world, pos.getX(), pos.getY(), pos.getZ());
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
        if (ref != null && !ref.isValid()) {
            blockComponents.removeEntityReference(blockIndex, ref);
            ref = null;
        }
        // A chunk may hold either a live entity reference or a serialized holder.
        Holder<ChunkStore> holder = ref == null ? blockComponents.getEntityHolder(blockIndex) : null;
        boolean holderFromChunk = holder != null;
        if (ref == null && holder == null) {
            holder = ChunkStore.REGISTRY.newHolder();
        }
        boolean changed = false;
        if (pending.energy != null && HyProTechComponents.ENERGY != null) {
            if (ref != null) {
                Store<ChunkStore> store = world.getChunkStore().getStore();
                if (store != null) {
                    store.putComponent(ref, HyProTechComponents.ENERGY, pending.energy);
                }
            } else if (holder != null) {
                holder.putComponent(HyProTechComponents.ENERGY, pending.energy);
            }
            pending.energy = null;
            changed = true;
        }
        if (pending.item != null && HyProTechComponents.ITEM != null) {
            if (ref != null) {
                Store<ChunkStore> store = world.getChunkStore().getStore();
                if (store != null) {
                    store.putComponent(ref, HyProTechComponents.ITEM, pending.item);
                }
            } else if (holder != null) {
                holder.putComponent(HyProTechComponents.ITEM, pending.item);
            }
            pending.item = null;
            changed = true;
        }
        // Promote the holder to a live entity when a store is available; otherwise retain it.
        if (ref == null && changed && holder != null) {
            Store<ChunkStore> store = world.getChunkStore().getStore();
            if (store != null) {
                if (holderFromChunk) {
                    blockComponents.removeEntityHolder(blockIndex);
                }
                Ref<ChunkStore> newRef = store.addEntity(holder, AddReason.SPAWN);
                blockComponents.addEntityReference(blockIndex, newRef);
            } else {
                if (!holderFromChunk) {
                    blockComponents.storeEntityHolder(blockIndex, holder);
                }
                blockComponents.markNeedsSaving();
            }
        }
        if (pending.energy == null && pending.item == null) {
            removePending(world, pos.getX(), pos.getY(), pos.getZ());
        }
        if (changed && ref != null) {
            blockComponents.markNeedsSaving();
        }
    }

    /** Checks the loaded block against the expected ID, accepting its state variants. */
    private static boolean blockIdMatches(World world, int x, int y, int z, String expectedId) {
        if (expectedId == null) {
            return true;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return false;
        }
        BlockType actual = accessor.getBlockType(x, y, z);
        if (actual == null || actual.getId() == null) {
            return false;
        }
        return isIdOrState(actual.getId(), expectedId);
    }

    /** Compares block types by ID, including state variants. */
    private static boolean sameBlockId(BlockType actual, BlockType expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String actualId = actual.getId();
        String expectedId = expected.getId();
        if (actualId == null || expectedId == null) {
            return false;
        }
        return actualId.equalsIgnoreCase(expectedId) || isIdOrState(actualId, expectedId);
    }

    /** Delegates the common base-ID and state-ID comparison. */
    private static boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }

    /** Identifies energy or item cables for optional upgrade diagnostics. */
    private static boolean isCableUpgradeId(String blockId) {
        return isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE);
    }

    /** Avoids replacing a cable when its current and requested tiers already match. */
    private static boolean isCableTierMatch(String currentId, String newBlockId) {
        if (currentId == null || newBlockId == null) {
            return false;
        }
        String baseId;
        if (isIdOrState(newBlockId, HyProTechIds.BLOCK_ENERGY_CABLE)) {
            baseId = HyProTechIds.BLOCK_ENERGY_CABLE;
        } else if (isIdOrState(newBlockId, HyProTechIds.BLOCK_ITEM_CABLE)) {
            baseId = HyProTechIds.BLOCK_ITEM_CABLE;
        } else {
            return false;
        }
        String normalized = TieredIdUtil.stripNamespace(newBlockId, baseId);
        int desiredTier = TieredIdUtil.parseTierSuffix(normalized, baseId);
        if (desiredTier < 0) {
            desiredTier = 0;
        }
        int currentTier = parseCableTierFromBlockId(currentId, baseId);
        return currentTier >= 0 && currentTier == desiredTier;
    }

    /** Uses the base ID for tier zero and the energy-cable suffix for higher tiers. */
    private static String buildEnergyCableTieredId(int tier) {
        if (tier <= 0) {
            return HyProTechIds.BLOCK_ENERGY_CABLE;
        }
        return HyProTechIds.BLOCK_ENERGY_CABLE + "_S" + tier;
    }

    /** Retrieves a container component only when the corresponding chunk is loaded. */
    private static ItemContainerBlock getItemContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            return null;
        }

        return BlockModule.get().getComponent(
                ItemContainerBlock.getComponentType(),
                world,
                x,
                y,
                z);
    }

    /** Returns the existing container component; initialization is handled by the server. */
    private static ItemContainerBlock getOrCreateItemContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        return getItemContainerState(world, x, y, z);
    }

    /** Retrieves a processing-bench component from a loaded chunk. */
    private static ProcessingBenchBlock getProcessingBenchState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            return null;
        }

        return BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                x,
                y,
                z);
    }

    /** Returns the server-initialized processing bench if it is available. */
    private static ProcessingBenchBlock getOrCreateProcessingBenchState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        return getProcessingBenchState(world, x, y, z);
    }

    /** Copies container slots, retaining null placeholders so occupied slot indexes survive. */
    private static List<ItemStack> snapshotContainerItems(World world, int x, int y, int z) {
        ItemContainerBlock state = getItemContainerState(world, x, y, z);
        ItemContainer container = state == null ? null : state.getItemContainer();
        if (container == null) {
            return null;
        }
        short capacity = container.getCapacity();
        List<ItemStack> items = new ArrayList<>(capacity);
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                items.add(null);
                continue;
            }
            ItemStack copy = new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata());
            items.add(copy);
        }
        return items;
    }

    /** Restores saved container slots up to the new container's capacity. */
    private static void restoreContainerItems(World world, int x, int y, int z, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        ItemContainerBlock state = getOrCreateItemContainerState(world, x, y, z);
        ItemContainer container = state == null ? null : state.getItemContainer();
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        int count = Math.min(items.size(), capacity);
        for (short slot = 0; slot < count; slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            placeStackIntoSlot(container, slot, stack);
        }
    }

    /** Copies processing-bench slots and preserves their positions. */
    private static List<ItemStack> snapshotBenchItems(World world, int x, int y, int z) {
        ProcessingBenchBlock state = getProcessingBenchState(world, x, y, z);
        ItemContainer container = state == null ? null : state.getItemContainer();
        if (container == null) {
            return null;
        }
        short capacity = container.getCapacity();
        List<ItemStack> items = new ArrayList<>(capacity);
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                items.add(null);
                continue;
            }
            items.add(new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata()));
        }
        return items;
    }

    /** Restores saved bench slots up to the replacement bench's capacity. */
    private static void restoreBenchItems(World world, int x, int y, int z, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        ProcessingBenchBlock state = getOrCreateProcessingBenchState(world, x, y, z);
        ItemContainer container = state == null ? null : state.getItemContainer();
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        int count = Math.min(items.size(), capacity);
        for (short slot = 0; slot < count; slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            placeStackIntoSlot(container, slot, stack);
        }
    }

    /** Metadata awaiting component initialization, keyed externally by world and position. */
    private static final class PendingUpgrade {
        private final String blockId;
        private final long createdTick;
        private EnergyNodeComponent energy;
        private ItemNodeComponent item;

        private PendingUpgrade(
                String blockId,
                EnergyNodeComponent energy,
                ItemNodeComponent item,
                long createdTick) {
            this.blockId = blockId;
            this.energy = energy;
            this.item = item;
            this.createdTick = createdTick;
        }
    }

    /** Inventory drops retained until consumed or expired. */
    private static final class PendingDrops {
        private final long createdTick;
        private final List<ItemStack> drops;

        private PendingDrops(long createdTick, List<ItemStack> drops) {
            this.createdTick = createdTick;
            this.drops = drops == null ? List.of() : drops;
        }
    }

    /** Replaces the original slot when possible, falling back to general container insertion. */
    private static void placeStackIntoSlot(ItemContainer container, short slot, ItemStack stack) {
        if (container == null || stack == null || ItemStack.isEmpty(stack)) {
            return;
        }
        ItemStack existing = container.getItemStack(slot);
        if (existing != null && !ItemStack.isEmpty(existing)) {
            int quantity = existing.getQuantity();
            if (quantity > 0) {
                container.removeItemStackFromSlot(slot, existing, quantity);
            }
        }
        if (!container.canAddItemStackToSlot(slot, stack, false, false)) {
            container.addItemStack(stack);
            return;
        }
        container.addItemStackToSlot(slot, stack);
    }

    /** Removes expired upgrade entries at most once per world tick. */
    public static void cleanupExpired(World world) {
        if (world == null) {
            return;
        }
        long tick = world.getTick();
        synchronized (PENDING) {
            Long lastTick = LAST_CLEANUP.get(world);
            if (lastTick != null && lastTick == tick) {
                return;
            }
            LAST_CLEANUP.put(world, tick);
            Map<Vector3i, PendingUpgrade> byPos = PENDING.get(world);
            if (byPos == null || byPos.isEmpty()) {
                return;
            }
            byPos.entrySet().removeIf(entry -> tick - entry.getValue().createdTick > PENDING_TTL_TICKS);
            if (byPos.isEmpty()) {
                PENDING.remove(world);
            }
        }
    }

    /** Prunes expired cached drops at most once per tick while the caller holds the drop lock. */
    private static void cleanupDropCache(World world, long tick) {
        if (world == null) {
            return;
        }
        Long lastTick = LAST_DROP_CLEANUP.get(world);
        if (lastTick != null && lastTick == tick) {
            return;
        }
        LAST_DROP_CLEANUP.put(world, tick);
        Map<Vector3i, PendingDrops> byPos = PENDING_DROPS.get(world);
        if (byPos == null || byPos.isEmpty()) {
            return;
        }
        byPos.entrySet().removeIf(entry -> tick - entry.getValue().createdTick > PENDING_DROP_TTL_TICKS);
        if (byPos.isEmpty()) {
            PENDING_DROPS.remove(world);
        }
    }

}
