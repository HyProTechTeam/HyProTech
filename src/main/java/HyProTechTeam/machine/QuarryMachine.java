package HyProTechTeam.machine;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.energy.EnergyNodeComponent;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.List;

/** Mines blocks in a configured area, collecting drops and optionally replacing non-ore blocks. */
public final class QuarryMachine extends MasterMachine {
    public static final String ID = "HyProTech:quarry";
    private static final int MINING_OFFSET_Z = -2;
    // Keep replacement progress separately for each world and quarry origin.
    private static final java.util.Map<World, java.util.Map<String, BackfillState>> BACKFILL_STATE =
            new java.util.HashMap<>();

    /** Returns the registry ID for the quarry definition. */
    @Override
    public String id() {
        return ID;
    }

    /** Matches the quarry base ID and its tiered or state variants. */
    @Override
    public boolean matchesBlockId(String blockId) {
        return TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_QUARRY)
                || isIdOrState(blockId, HyProTechIds.BLOCK_QUARRY);
    }

    /** Applies base defaults and fills an absent machine ID or non-positive dimensions. */
    @Override
    public void configureDefaults(MachineComponent machine, EnergyNodeComponent energy) {
        super.configureDefaults(machine, energy);
        if (machine != null && (machine.getMachineId() == null || machine.getMachineId().isEmpty())) {
            machine.setMachineId(ID);
        }
        if (machine != null) {
            if (machine.getAreaWidth() <= 0) {
                machine.setAreaWidth(QuarryConfig.BASE_AREA);
            }
            if (machine.getAreaDepth() <= 0) {
                machine.setAreaDepth(QuarryConfig.BASE_AREA);
            }
        }
    }

    /** Uses the minimum tier's configured energy capacity as the initial capacity. */
    @Override
    protected int defaultCapacity() {
        return QuarryConfig.getCapacityForTier(QuarryConfig.MIN_TIER);
    }

    /** Uses the minimum tier's configured transfer limit initially. */
    @Override
    protected int defaultMaxTransfer() {
        return QuarryConfig.getMaxTransferForTier(QuarryConfig.MIN_TIER);
    }

    /** Advances mining or a pending backfill operation for one quarry tick. */
    @Override
    public boolean tick(MachineContext context, float deltaSeconds) {
        if (context == null) {
            return false;
        }
        MachineComponent machine = context.getMachine();
        EnergyNodeComponent energy = context.getEnergyNode();
        if (machine == null || energy == null) {
            return false;
        }
        if (!machine.isEnabled()) {
            if (machine.getProgress() != 0) {
                machine.setProgress(0);
                context.markDirty();
            }
            return false;
        }
        World world = context.getWorld();
        if (world == null) {
            return false;
        }

        // Torch bounds override the dimensions normally selected for the quarry.
        Vector3i originPos = new Vector3i(context.getX(), context.getY(), context.getZ());
        QuarryAreaManager.TorchBounds torchBounds =
                QuarryAreaManager.getTorchBounds(world, originPos);
        if (torchBounds != null) {
            machine.setAreaWidth(torchBounds.getWidth());
            machine.setAreaDepth(torchBounds.getDepth());
        }

        boolean changed = applyTierSettings(machine, energy, torchBounds != null);
        if (changed) {
            context.markDirty();
        }

        int width = Math.max(QuarryConfig.MIN_AREA, machine.getAreaWidth());
        int depth = Math.max(QuarryConfig.MIN_AREA, machine.getAreaDepth());
        int originX = context.getX();
        int originY = context.getY();
        int originZ = context.getZ();

        EnergyStorage storage = context.getEnergyStorage();
        ItemContainer container = context.getItemContainer();
        Rotation yaw = getBlockYaw(world, originX, originY, originZ);
        Bounds bounds = computeMiningBounds(width, depth);

        // Keep backfill progress only while replacement mode is active.
        boolean replaceMode = QuarryConfig.isForceReplaceBlocks() || machine.isReplaceMinedBlocks();
        BackfillState backfillState = replaceMode ? getBackfillState(world, originPos, true) : null;
        if (!replaceMode) {
            clearBackfillState(world, originPos);
        }

        long tick = world.getTick();
        int backfillPlaceDelay = QuarryConfig.getBackfillPlaceDelayTicks();
        int backfillPostDelay = QuarryConfig.getBackfillPostDelayTicks();
        // Finish a pending replacement before searching for another block.
        if (backfillState != null) {
            if (backfillState.pending != null) {
                if (tick < backfillState.pending.placeAtTick) {
                    return changed;
                }
                if (!backfillState.pending.consumed) {
                    if (!consumeItemFromContainer(container, backfillState.pending.replacement)) {
                        backfillState.pending = null;
                        backfillState.resumeAtTick = tick + backfillPostDelay;
                        return changed;
                    }
                    backfillState.pending.consumed = true;
                }
                scheduleBlockReplace(
                        world,
                        backfillState.pending.pos,
                        backfillState.pending.replacement.getItemId(),
                        backfillState.pending.fallbackBlockType,
                        backfillState.pending.rotationIndex);
                backfillState.pending = null;
                backfillState.resumeAtTick = tick + backfillPostDelay;
                return true;
            }
            if (backfillState.resumeAtTick > tick) {
                return changed;
            }
        }

        // Search torch-defined world bounds or the quarry's rotated local bounds.
        BlockTarget target;
        Vector3i startAfter = backfillState == null ? null : backfillState.lastPos;
        if (torchBounds != null) {
            target = findNextTargetInBounds(
                    world,
                    torchBounds.minX,
                    torchBounds.maxX,
                    torchBounds.minZ,
                    torchBounds.maxZ,
                    originY,
                    startAfter,
                    replaceMode);
        } else {
            target = findNextTarget(world, originX, originY, originZ, bounds, yaw, startAfter, replaceMode);
        }
        if (target == null) {
            if (machine.getProgress() != 0) {
                machine.setProgress(0);
                context.markDirty();
                return true;
            }
            return changed;
        }

        int progressMax = Math.max(1, machine.getProgressMax());
        int currentProgress = machine.getProgress();

        // Replacement mode backfills non-ore blocks using a suitable drop or block ID.
        boolean backfill = replaceMode && !isOreBlockId(target.blockType.getId());
        List<ItemStack> drops = resolveDrops(target.blockType);
        ItemStack replacement = backfill ? pickReplacementStack(drops, target.blockType) : null;
        boolean preConsumed = false;

        // A full inventory stops normal mining; backfill can proceed if a replacement can be consumed.
        if (container != null && isContainerFull(container)) {
            if (!backfill) {
                return changed;
            }
            if (replacement == null) {
                return changed;
            }
            if (!container.canAddItemStack(replacement)) {
                if (!consumeItemFromContainer(container, replacement)) {
                    return changed;
                }
                preConsumed = true;
            }
        }

        // Convert per-second consumption to a cost for this tick and extract it transactionally.
        int consumptionPerSecond = Math.max(0, energy.getConsumption());
        int cost = 0;
        if (consumptionPerSecond > 0 && deltaSeconds > 0f) {
            cost = (int) Math.round(consumptionPerSecond * deltaSeconds);
            if (cost < 1) {
                cost = 1;
            }
        }
        if (cost > 0 && !consumeEnergy(storage, cost)) {
            return changed;
        }

        // Advance one step and wait until the configured mining threshold is reached.
        int progress = currentProgress + 1;
        if (progress < progressMax) {
            machine.setProgress(progress);
            context.markDirty();
            return true;
        }
        machine.setProgress(0);

        // Store drops where possible and spawn the remainder before scheduling the block break.
        if (drops != null && !drops.isEmpty()) {
            if (container != null) {
                addDropsToContainerOrWorld(container, world, drops, target.pos);
            } else {
                spawnDrops(world, drops, target.pos);
            }
        }

        scheduleBlockBreak(world, target.pos);

        // Record replacement state and delay placement until a later world tick.
        if (backfill && container != null && replacement != null && backfillState != null) {
            boolean consumed = preConsumed || consumeItemFromContainer(container, replacement);
            backfillState.lastPos = target.pos;
            backfillState.pending = new PendingBackfill(
                    target.pos,
                    replacement,
                    target.blockType,
                    target.rotationIndex,
                    tick + backfillPlaceDelay,
                    consumed);
            context.markDirty();
            return true;
        }
        if (backfillState != null) {
            backfillState.lastPos = target.pos;
        }

        context.markDirty();
        return true;
    }

    /** Applies tier bounds, dimensions, capacity, consumption, transfer, and mining delay. */
    private boolean applyTierSettings(
            MachineComponent machine,
            EnergyNodeComponent energy,
            boolean allowLargeArea) {
        if (machine == null || energy == null) {
            return false;
        }
        boolean changed = false;
        int tier = QuarryConfig.clampTier(machine.getTier());
        if (machine.getTier() != tier) {
            machine.setTier(tier);
            changed = true;
        }

        // Torch-defined dimensions are bounded by the minimum but not the tier maximum.
        int width = allowLargeArea
                ? Math.max(QuarryConfig.MIN_AREA, machine.getAreaWidth())
                : QuarryConfig.clampAreaForTier(tier, machine.getAreaWidth());
        int depth = allowLargeArea
                ? Math.max(QuarryConfig.MIN_AREA, machine.getAreaDepth())
                : QuarryConfig.clampAreaForTier(tier, machine.getAreaDepth());
        if (width != machine.getAreaWidth()) {
            machine.setAreaWidth(width);
            changed = true;
        }
        if (depth != machine.getAreaDepth()) {
            machine.setAreaDepth(depth);
            changed = true;
        }

        int capacity = QuarryConfig.getCapacityForTier(tier);
        if (energy.getCapacity() != capacity) {
            energy.setCapacity(capacity);
            if (energy.getEnergy() > capacity) {
                energy.setEnergy(capacity);
            }
            changed = true;
        }

        int consumption = QuarryConfig.computeConsumptionForArea(tier, width, depth);
        if (energy.getConsumption() != consumption) {
            energy.setConsumption(consumption);
            changed = true;
        }

        int maxTransfer = QuarryConfig.getMaxTransferForTier(tier);
        if (energy.getMaxTransfer() != maxTransfer) {
            energy.setMaxTransfer(maxTransfer);
            changed = true;
        }

        int progressMax = QuarryConfig.getMiningDelayTicks(tier);
        if (machine.getProgressMax() != progressMax) {
            machine.setProgressMax(progressMax);
            if (machine.getProgress() > progressMax) {
                machine.setProgress(progressMax);
            }
            changed = true;
        }

        return changed;
    }

    /** Searches explicit world bounds after the previous position, then wraps if necessary. */
    private BlockTarget findNextTargetInBounds(
            World world,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int originY,
            Vector3i startAfter,
            boolean replaceMode) {
        BlockTarget target = findNextTargetInBoundsInternal(
                world, minX, maxX, minZ, maxZ, originY, startAfter, replaceMode, true);
        if (target != null || startAfter == null) {
            return target;
        }
        return findNextTargetInBoundsInternal(
                world, minX, maxX, minZ, maxZ, originY, startAfter, replaceMode, false);
    }

    /** Scans loaded blocks downward through the explicit world-coordinate bounds. */
    private BlockTarget findNextTargetInBoundsInternal(
            World world,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int originY,
            Vector3i startAfter,
            boolean replaceMode,
            boolean skipUntilStart) {
        int startY = originY - 1;
        int minY = ChunkUtil.MIN_Y;
        boolean skipping = skipUntilStart && startAfter != null;
        for (int y = startY; y >= minY; y--) {
            for (int z = maxZ; z >= minZ; z--) {
                for (int x = minX; x <= maxX; x++) {
                    if (startAfter != null) {
                        if (skipping) {
                            if (startAfter.getX() == x && startAfter.getY() == y && startAfter.getZ() == z) {
                                skipping = false;
                            }
                            continue;
                        }
                        if (!skipUntilStart
                                && startAfter.getX() == x && startAfter.getY() == y && startAfter.getZ() == z) {
                            return null;
                        }
                    }
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                    BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
                    if (accessor == null) {
                        continue;
                    }
                    BlockType blockType = accessor.getBlockType(x, y, z);
                    if (!isMineable(blockType, replaceMode)) {
                        continue;
                    }
                    int rotationIndex = world.getBlockRotationIndex(x, y, z);
                    return new BlockTarget(new Vector3i(x, y, z), blockType, rotationIndex);
                }
            }
        }
        return null;
    }

    /** Searches rotated local bounds after the previous position, then wraps if necessary. */
    private BlockTarget findNextTarget(
            World world,
            int originX,
            int originY,
            int originZ,
            Bounds bounds,
            Rotation yaw,
            Vector3i startAfter,
            boolean replaceMode) {
        BlockTarget target = findNextTargetInternal(
                world, originX, originY, originZ, bounds, yaw, startAfter, replaceMode, true);
        if (target != null || startAfter == null) {
            return target;
        }
        return findNextTargetInternal(
                world, originX, originY, originZ, bounds, yaw, startAfter, replaceMode, false);
    }

    /** Scans loaded blocks downward, rotating local offsets into world coordinates. */
    private BlockTarget findNextTargetInternal(
            World world,
            int originX,
            int originY,
            int originZ,
            Bounds bounds,
            Rotation yaw,
            Vector3i startAfter,
            boolean replaceMode,
            boolean skipUntilStart) {
        int startY = originY - 1;
        int minY = ChunkUtil.MIN_Y;
        boolean skipping = skipUntilStart && startAfter != null;
        for (int y = startY; y >= minY; y--) {
            for (int localZ = bounds.maxZ; localZ >= bounds.minZ; localZ--) {
                for (int localX = bounds.minX; localX <= bounds.maxX; localX++) {
                    Vector3i offset = rotateLocalOffset(localX, localZ, yaw);
                    int worldX = originX + offset.getX();
                    int worldZ = originZ + offset.getZ();
                    if (startAfter != null) {
                        if (skipping) {
                            if (startAfter.getX() == worldX
                                    && startAfter.getY() == y
                                    && startAfter.getZ() == worldZ) {
                                skipping = false;
                            }
                            continue;
                        }
                        if (!skipUntilStart
                                && startAfter.getX() == worldX
                                && startAfter.getY() == y
                                && startAfter.getZ() == worldZ) {
                            return null;
                        }
                    }
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
                    BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
                    if (accessor == null) {
                        continue;
                    }
                    BlockType blockType = accessor.getBlockType(worldX, y, worldZ);
                    if (!isMineable(blockType, replaceMode)) {
                        continue;
                    }
                    int rotationIndex = world.getBlockRotationIndex(worldX, y, worldZ);
                    return new BlockTarget(new Vector3i(worldX, y, worldZ), blockType, rotationIndex);
                }
            }
        }
        return null;
    }

    /** Uses the shared transaction helper to consume the full requested energy amount. */
    private boolean consumeEnergy(EnergyStorage storage, int amount) {
        return MachineCommonUtil.consumeEnergy(storage, amount);
    }

    /** Returns true only when the container has positive capacity and no empty slots. */
    private boolean isContainerFull(ItemContainer container) {
        if (container == null) {
            return false;
        }
        short capacity = container.getCapacity();
        if (capacity <= 0) {
            return false;
        }
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                return false;
            }
        }
        return true;
    }

    /** Resolves drops using the block's first applicable gathering rule, or a generic fallback. */
    private List<ItemStack> resolveDrops(BlockType blockType) {
        if (blockType == null) {
            return List.of();
        }
        BlockGathering gathering = blockType.getGathering();
        if (gathering != null) {
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null) {
                return resolveDropType(
                        blockType,
                        Math.max(1, breaking.getQuantity()),
                        breaking.getItemId(),
                        breaking.getDropListId());
            }
            HarvestingDropType harvest = gathering.getHarvest();
            if (harvest != null) {
                return resolveDropType(blockType, 1, harvest.getItemId(), harvest.getDropListId());
            }
            SoftBlockDropType soft = gathering.getSoft();
            if (soft != null) {
                return resolveDropType(blockType, 1, soft.getItemId(), soft.getDropListId());
            }
            PhysicsDropType physics = gathering.getPhysics();
            if (physics != null) {
                return resolveDropType(blockType, 1, physics.getItemId(), physics.getDropListId());
            }
        }
        return BlockHarvestUtils.getDrops(blockType, 1, null, null);
    }

    /** Passes normalized quantity and optional item/drop-list IDs to the harvesting API. */
    private List<ItemStack> resolveDropType(
            BlockType blockType,
            int quantity,
            String itemId,
            String dropListId) {
        int safeQuantity = Math.max(1, quantity);
        String resolvedItemId = emptyToNull(itemId);
        String resolvedDropListId = emptyToNull(dropListId);
        return BlockHarvestUtils.getDrops(blockType, safeQuantity, resolvedItemId, resolvedDropListId);
    }

    /** Converts a missing or empty string to null for optional API parameters. */
    private String emptyToNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /** Adds each drop to the inventory when it fits; otherwise spawns that drop in the world. */
    private void addDropsToContainerOrWorld(
            ItemContainer container,
            World world,
            List<ItemStack> drops,
            Vector3i pos) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        for (ItemStack drop : drops) {
            if (drop == null || ItemStack.isEmpty(drop)) {
                continue;
            }
            if (container.canAddItemStack(drop)) {
                container.addItemStack(drop);
            } else {
                spawnDrops(world, List.of(drop), pos);
            }
        }
    }

    /** Spawns nonempty item stacks at the block center with a small upward velocity. */
    private void spawnDrops(World world, List<ItemStack> stacks, Vector3i pos) {
        if (world == null || stacks == null || stacks.isEmpty() || pos == null) {
            return;
        }
        List<ItemStack> filtered = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            filtered.add(stack);
        }
        if (filtered.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d position = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vector3f velocity = new Vector3f(0f, 0.1f, 0f);
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                store,
                filtered,
                position,
                velocity);
        store.addEntities(holders, AddReason.SPAWN);
    }

    /** Rejects empty blocks, bedrock, quarry border blocks, and quarry blocks. */
    private boolean isMineable(BlockType blockType, boolean replaceMode) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (containsIgnoreCase(id, "bedrock")) {
            return false;
        }
        if (isBorderId(id) || TieredIdUtil.isTieredId(id, HyProTechIds.BLOCK_QUARRY)) {
            return false;
        }
        return true;
    }

    /** Recognizes ore IDs after stripping any namespace and path prefix. */
    private boolean isOreBlockId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        String normalized = id;
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.startsWith("Ore_")) {
            return true;
        }
        if (normalized.endsWith("_Ore")) {
            return true;
        }
        return false;
    }

    /** Matches quarry border IDs and variants without regard to case. */
    private boolean isBorderId(String id) {
        if (id == null) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_QUARRY_BORDER;
        return id.equalsIgnoreCase(baseId)
                || id.regionMatches(true, 0, baseId, 0, baseId.length())
                || containsIgnoreCase(id, baseId);
    }

    /** Searches for a nonempty substring without regard to case. */
    private boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null || needle.isEmpty()) {
            return false;
        }
        int limit = value.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    /** Reads a loaded block's yaw, defaulting to no rotation when unavailable. */
    private Rotation getBlockYaw(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            return Rotation.None;
        }
        RotationTuple rotation = RotationTuple.get(world.getBlockRotationIndex(x, y, z));
        if (rotation == null || rotation.yaw() == null) {
            return Rotation.None;
        }
        return rotation.yaw();
    }

    /** Rotates a horizontal local offset by the block's quarter-turn yaw. */
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

    /** Builds centered local bounds for a width and depth. */
    private Bounds computeBounds(int width, int depth) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int minX = -halfWidth;
        int maxX = width - 1 - halfWidth;
        int minZ = -halfDepth;
        int maxZ = depth - 1 - halfDepth;
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    /** Builds local mining bounds behind the machine using the configured offset. */
    private Bounds computeMiningBounds(int width, int depth) {
        int halfWidth = width / 2;
        int minX = -halfWidth;
        int maxX = width - 1 - halfWidth;
        int maxZ = MINING_OFFSET_Z;
        int minZ = maxZ - (Math.max(1, depth) - 1);
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    /** Schedules removal of block components and the block itself on the world executor. */
    private void scheduleBlockBreak(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor != null) {
                clearBlockComponents(world, x, y, z);
                accessor.setBlock(x, y, z, BlockType.EMPTY);
            }
        });
    }

    /** Schedules replacement, using the old block type as a fallback if lookup fails. */
    private void scheduleBlockReplace(
            World world,
            Vector3i pos,
            String replacementItemId,
            BlockType fallbackBlockType,
            int rotationIndex) {
        if (world == null || pos == null) {
            return;
        }
        String blockId = replacementItemId;
        if (blockId == null || blockId.isEmpty()) {
            blockId = fallbackBlockType == null ? null : fallbackBlockType.getId();
        }
        if (blockId == null || blockId.isEmpty()) {
            scheduleBlockBreak(world, pos);
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        final String finalBlockId = blockId;
        final BlockType finalFallbackBlockType = fallbackBlockType;
        final int finalRotationIndex = rotationIndex;
        world.execute(() -> {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor == null) {
                return;
            }
            clearBlockComponents(world, x, y, z);
            int blockIndex = BlockType.getAssetMap().getIndex(finalBlockId);
            if (blockIndex == Integer.MIN_VALUE) {
                if (finalFallbackBlockType != null) {
                    accessor.setBlock(x, y, z, finalFallbackBlockType);
                }
                return;
            }
            BlockType blockType = BlockType.getAssetMap().getAsset(blockIndex);
            if (blockType == null) {
                if (finalFallbackBlockType != null) {
                    accessor.setBlock(x, y, z, finalFallbackBlockType);
                }
                return;
            }
            accessor.setBlock(x, y, z, blockIndex, blockType, finalRotationIndex, 0, 0);
        });
    }

    /** Chooses one item from the first valid drop, or falls back to the block ID. */
    private ItemStack pickReplacementStack(List<ItemStack> drops, BlockType blockType) {
        if (drops != null) {
            for (ItemStack drop : drops) {
                if (drop == null || ItemStack.isEmpty(drop)) {
                    continue;
                }
                String itemId = drop.getItemId();
                if (itemId == null || itemId.isEmpty()) {
                    continue;
                }
                return new ItemStack(itemId, 1, drop.getMetadata());
            }
        }
        if (blockType != null && blockType.getId() != null && !blockType.getId().isEmpty()) {
            return new ItemStack(blockType.getId(), 1);
        }
        return null;
    }

    /** Removes any block entity reference or holder and marks the chunk for saving. */
    private void clearBlockComponents(World world, int x, int y, int z) {
        if (world == null) {
            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
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

    /** Removes one matching item with matching metadata from the inventory. */
    private boolean consumeItemFromContainer(ItemContainer container, ItemStack stack) {
        if (container == null || stack == null || ItemStack.isEmpty(stack)) {
            return false;
        }
        String itemId = stack.getItemId();
        Object metadata = stack.getMetadata();
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || ItemStack.isEmpty(existing)) {
                continue;
            }
            if (!existing.getItemId().equalsIgnoreCase(itemId)) {
                continue;
            }
            if (!java.util.Objects.equals(existing.getMetadata(), metadata)) {
                continue;
            }
            container.removeItemStackFromSlot(slot, existing, 1);
            return true;
        }
        return false;
    }

    /** Stores the replacement, location, original rotation, and scheduled placement tick. */
    private static final class PendingBackfill {
        private final Vector3i pos;
        private final ItemStack replacement;
        private final BlockType fallbackBlockType;
        private final int rotationIndex;
        private final long placeAtTick;
        private boolean consumed;

        private PendingBackfill(
                Vector3i pos,
                ItemStack replacement,
                BlockType fallbackBlockType,
                int rotationIndex,
                long placeAtTick,
                boolean consumed) {
            this.pos = pos;
            this.replacement = replacement;
            this.fallbackBlockType = fallbackBlockType;
            this.rotationIndex = rotationIndex;
            this.placeAtTick = placeAtTick;
            this.consumed = consumed;
        }
    }

    /** Tracks the last target and pending or delayed backfill for a quarry. */
    private static final class BackfillState {
        private Vector3i lastPos;
        private PendingBackfill pending;
        private long resumeAtTick;
    }

    /** Looks up per-world backfill state and optionally creates missing map entries. */
    private BackfillState getBackfillState(World world, Vector3i origin, boolean create) {
        if (world == null || origin == null) {
            return null;
        }
        java.util.Map<String, BackfillState> byWorld = BACKFILL_STATE.get(world);
        if (byWorld == null) {
            if (!create) {
                return null;
            }
            byWorld = new java.util.HashMap<>();
            BACKFILL_STATE.put(world, byWorld);
        }
        String key = originKey(origin);
        BackfillState state = byWorld.get(key);
        if (state == null && create) {
            state = new BackfillState();
            byWorld.put(key, state);
        }
        return state;
    }

    /** Removes the quarry's backfill state and prunes an empty world map. */
    private void clearBackfillState(World world, Vector3i origin) {
        if (world == null || origin == null) {
            return;
        }
        java.util.Map<String, BackfillState> byWorld = BACKFILL_STATE.get(world);
        if (byWorld == null) {
            return;
        }
        byWorld.remove(originKey(origin));
        if (byWorld.isEmpty()) {
            BACKFILL_STATE.remove(world);
        }
    }

    /** Encodes the quarry's block coordinates as the per-world lookup key. */
    private static String originKey(Vector3i origin) {
        return origin.getX() + ":" + origin.getY() + ":" + origin.getZ();
    }

    /** Carries a selected block position, type, and rotation for mining and backfill. */
    private static final class BlockTarget {
        private final Vector3i pos;
        private final BlockType blockType;
        private final int rotationIndex;

        private BlockTarget(Vector3i pos, BlockType blockType, int rotationIndex) {
            this.pos = pos;
            this.blockType = blockType;
            this.rotationIndex = rotationIndex;
        }
    }

    /** Inclusive horizontal bounds used for local mining scans. */
    private static final class Bounds {
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private Bounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    /** Delegates block base and state matching to the shared ID helper. */
    private static boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }
}
