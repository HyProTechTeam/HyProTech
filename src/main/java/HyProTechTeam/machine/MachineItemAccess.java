package HyProTechTeam.machine;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;

/** Finds machine inventories and repairs their slot capacities when needed. */
public final class MachineItemAccess {
    private static final short QUARRY_STORAGE_CAPACITY = 10;
    private static final short ORE_CRUSHER_STORAGE_CAPACITY = 7;
    private static final short ALLOY_SMELTER_STORAGE_CAPACITY =
            (short) (AlloySmelterConfig.INPUT_SLOT_COUNT + AlloySmelterConfig.OUTPUT_SLOT_COUNT);

    private MachineItemAccess() {
    }

    /** Finds a processing bench or item-container component at the machine position. */
    public static Object getContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

        // Crusher and smelter blocks use item-container components; schedule a retry if absent.
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType != null && blockType != BlockType.EMPTY) {
            String blockId = blockType.getId();
            if (blockId != null && isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)) {
                ItemContainerBlock containerBlock = BlockModule.get().getComponent(
                        ItemContainerBlock.getComponentType(),
                        world,
                        x,
                        y,
                        z);
                if (containerBlock == null) {
                    scheduleContainerState(world, x, y, z, blockType);
                    return null;
                }
                ensureMachineContainers(world, x, y, z, containerBlock);
                return containerBlock;
            }
            if (blockId != null && isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)) {
                ItemContainerBlock containerBlock = BlockModule.get().getComponent(
                        ItemContainerBlock.getComponentType(),
                        world,
                        x,
                        y,
                        z);
                if (containerBlock == null) {
                    scheduleContainerState(world, x, y, z, blockType);
                    return null;
                }
                ensureMachineContainers(world, x, y, z, containerBlock);
                return containerBlock;
            }
        }

        // Other blocks may expose a processing bench or a generic item container.
        ProcessingBenchBlock benchBlock = BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                x,
                y,
                z);
        if (benchBlock != null) {
            return benchBlock;
        }

        ItemContainerBlock containerBlock = BlockModule.get().getComponent(
                ItemContainerBlock.getComponentType(),
                world,
                x,
                y,
                z);
        if (containerBlock != null) {
            ensureMachineContainers(world, x, y, z, containerBlock);
            return containerBlock;
        }
        if (blockType == null || blockType == BlockType.EMPTY) {
            return null;
        }
        String blockId = blockType.getId();
        if (blockId == null || MachineRegistry.findByBlockId(blockId) == null) {
            return null;
        }

        // A recognized machine with no component yet is checked again on the world executor.
        scheduleContainerState(world, x, y, z, blockType);
        return null;
    }

    /** Returns the inventory from the located component, or null when unavailable. */
    public static ItemContainer getContainer(World world, int x, int y, int z) {
        Object state = getContainerState(world, x, y, z);
        return getItemContainerFromState(state);
    }

    /** Extracts the inventory from either supported block component type. */
    public static ItemContainer getItemContainerFromState(Object state) {
        if (state instanceof ProcessingBenchBlock) return ((ProcessingBenchBlock) state).getItemContainer();
        if (state instanceof ItemContainerBlock) return ((ItemContainerBlock) state).getItemContainer();
        return null;
    }

    /** Compatibility hook: the current container API persists changes automatically. */
    public static void markContainerDirty(World world, int x, int y, int z) {
        // container changes are automatically persisted in the new API
    }

    /** Schedules a world-thread check that repairs the component if it exists by then. */
    private static void scheduleContainerState(
            World world,
            int x,
            int y,
            int z,
            BlockType blockType) {
        if (world == null) {
            return;
        }

        world.execute(() -> {
            ItemContainerBlock state = BlockModule.get().getComponent(
                    ItemContainerBlock.getComponentType(),
                    world,
                    x,
                    y,
                    z);
            if (state != null) {
                ensureMachineContainers(world, x, y, z, state);
            }
        });
    }

    /** Repairs an existing item-container component and returns it, or null when absent. */
    public static Object ensureContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

        ItemContainerBlock state = BlockModule.get().getComponent(
                ItemContainerBlock.getComponentType(),
                world,
                x,
                y,
                z);
        if (state == null) {
            return null;
        }
        ensureMachineContainers(world, x, y, z, state);
        return state;
    }

    /** Selects the appropriate capacity repair based on the current machine block ID. */
    private static void ensureMachineContainers(
            World world,
            int x,
            int y,
            int z,
            ItemContainerBlock state) {
        if (world == null || state == null) {
            return;
        }
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        String blockId = blockType.getId();
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        if (isQuarryBorder(blockId)) {
            return;
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_QUARRY)) {
            ensureQuarryContainer(world, x, y, z, state);
            return;
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)) {
            ensureOreCrusherContainer(world, x, y, z, state);
            return;
        }
        if (isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)) {
            ensureAlloySmelterContainer(world, x, y, z, state);
        }
    }

    /** Replaces an incorrectly sized quarry inventory and copies slots that fit. */
    private static void ensureQuarryContainer(
            World world,
            int x,
            int y,
            int z,
            ItemContainerBlock state) {
        if (world == null || state == null) {
            return;
        }
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        String blockId = blockType.getId();
        if (blockId == null || isQuarryBorder(blockId) || !isIdOrState(blockId, HyProTechIds.BLOCK_QUARRY)) {
            return;
        }
        ItemContainer container = state.getItemContainer();
        if (container != null && container.getCapacity() == QUARRY_STORAGE_CAPACITY) {
            return;
        }

        SimpleItemContainer replacement = new SimpleItemContainer(QUARRY_STORAGE_CAPACITY);
        if (container != null) {
            copyContainerItems(world, x, y, z, container, replacement, QUARRY_STORAGE_CAPACITY);
        }
        state.setItemContainer(replacement);
    }

    /** Replaces an incorrectly sized ore-crusher inventory and copies slots that fit. */
    private static void ensureOreCrusherContainer(
            World world,
            int x,
            int y,
            int z,
            ItemContainerBlock state) {
        if (world == null || state == null) {
            return;
        }
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        String blockId = blockType.getId();
        if (blockId == null || !isIdOrState(blockId, HyProTechIds.BLOCK_ORE_CRUSHER)) {
            return;
        }
        ItemContainer container = state.getItemContainer();
        if (container != null && container.getCapacity() == ORE_CRUSHER_STORAGE_CAPACITY) {
            return;
        }

        SimpleItemContainer replacement = new SimpleItemContainer(ORE_CRUSHER_STORAGE_CAPACITY);
        if (container != null) {
            copyContainerItems(world, x, y, z, container, replacement, ORE_CRUSHER_STORAGE_CAPACITY);
        }
        state.setItemContainer(replacement);
    }

    /** Replaces an incorrectly sized smelter inventory and copies slots that fit. */
    private static void ensureAlloySmelterContainer(
            World world,
            int x,
            int y,
            int z,
            ItemContainerBlock state) {
        if (world == null || state == null) {
            return;
        }
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        String blockId = blockType.getId();
        if (blockId == null || !isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)) {
            return;
        }
        ItemContainer container = state.getItemContainer();
        if (container != null && container.getCapacity() == ALLOY_SMELTER_STORAGE_CAPACITY) {
            return;
        }

        SimpleItemContainer replacement = new SimpleItemContainer(ALLOY_SMELTER_STORAGE_CAPACITY);
        if (container != null) {
            copyContainerItems(world, x, y, z, container, replacement, ALLOY_SMELTER_STORAGE_CAPACITY);
        }
        state.setItemContainer(replacement);
    }


    /** Delegates base-ID and state matching to the shared block-ID helper. */
    private static boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }

    /** Recognizes quarry border IDs so border blocks do not receive inventories. */
    private static boolean isQuarryBorder(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_QUARRY_BORDER;
        return blockId.equalsIgnoreCase(baseId)
                || blockId.regionMatches(true, 0, baseId, 0, baseId.length())
                || containsIgnoreCase(blockId, baseId);
    }

    /** Searches for a nonempty substring without regard to case. */
    private static boolean containsIgnoreCase(String value, String needle) {
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

    /** Copies occupied slots up to the smaller source or target capacity, retaining metadata. */
    private static void copyContainerItems(
            World world,
            int x,
            int y,
            int z,
            ItemContainer source,
            SimpleItemContainer target,
            short maxCapacity) {
        if (source == null || target == null) {
            return;
        }
        short capacity = source.getCapacity();
        short limit = (short) Math.min(capacity, maxCapacity);
        for (short slot = 0; slot < limit; slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            ItemStack copy = new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata());
            target.addItemStackToSlot(slot, copy);
        }
    }

}
