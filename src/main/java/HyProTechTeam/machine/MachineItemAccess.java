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

public final class MachineItemAccess {
    private static final short QUARRY_STORAGE_CAPACITY = 10;
    private static final short ORE_CRUSHER_STORAGE_CAPACITY = 7;
    private static final short ALLOY_SMELTER_STORAGE_CAPACITY =
            (short) (AlloySmelterConfig.INPUT_SLOT_COUNT + AlloySmelterConfig.OUTPUT_SLOT_COUNT);

    private MachineItemAccess() {
    }

    public static Object getContainerState(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

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

        scheduleContainerState(world, x, y, z, blockType);
        return null;
    }

    public static ItemContainer getContainer(World world, int x, int y, int z) {
        Object state = getContainerState(world, x, y, z);
        return getItemContainerFromState(state);
    }

    public static ItemContainer getItemContainerFromState(Object state) {
        if (state instanceof ProcessingBenchBlock) return ((ProcessingBenchBlock) state).getItemContainer();
        if (state instanceof ItemContainerBlock) return ((ItemContainerBlock) state).getItemContainer();
        return null;
    }

    public static void markContainerDirty(World world, int x, int y, int z) {
        // container changes are automatically persisted in the new API
    }

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


    private static boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }

    private static boolean isQuarryBorder(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_QUARRY_BORDER;
        return blockId.equalsIgnoreCase(baseId)
                || blockId.regionMatches(true, 0, baseId, 0, baseId.length())
                || containsIgnoreCase(blockId, baseId);
    }

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
