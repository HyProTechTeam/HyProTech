package HyProTechTeam.ui;

import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.UpgradePersistence;
import HyProTechTeam.energy.BatteryUpgradeConfig;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.EnergyUnits;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;

public class BatteryPage extends InteractiveCustomUIPage<SideToggleEvent> {
    private static final String ACTION_TOGGLE = "Toggle";
    private static final String ACTION_UPGRADE = "Upgrade";
    private static final long UPDATE_INTERVAL_MS = 250L;
    private static final String[] UPGRADE_ROW_IDS = {
            "#UpgradeReqRow1",
            "#UpgradeReqRow2",
            "#UpgradeReqRow3",
            "#UpgradeReqRow4"
    };
    private static final String[] UPGRADE_SLOT_IDS = {
            "#UpgradeReqSlot1",
            "#UpgradeReqSlot2",
            "#UpgradeReqSlot3",
            "#UpgradeReqSlot4"
    };
    private static final String[] UPGRADE_NAME_IDS = {
            "#UpgradeReqName1",
            "#UpgradeReqName2",
            "#UpgradeReqName3",
            "#UpgradeReqName4"
    };
    private static final String[] UPGRADE_QTY_IDS = {
            "#UpgradeReqQty1",
            "#UpgradeReqQty2",
            "#UpgradeReqQty3",
            "#UpgradeReqQty4"
    };

    private Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private Vector3i blockPosition;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastMaxTransfer = Integer.MIN_VALUE;
    private int lastInputMask = Integer.MIN_VALUE;
    private int lastOutputMask = Integer.MIN_VALUE;
    private int lastConnectedMask = Integer.MIN_VALUE;
    private int lastTier = Integer.MIN_VALUE;
    private String lastReqKey = "";
    private String lastButtonText = "";
    private long lastUpdateMs;

    public BatteryPage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, EnergyNodeComponent> energyType) {
        super(playerRef, CustomPageLifetime.CanDismiss, SideToggleEvent.CODEC);
        this.blockRef = blockRef;
        this.energyType = energyType;
    }

    public Ref<ChunkStore> getBlockRef() {
        return blockRef;
    }

    @Override
    public void build(
            Ref<EntityStore> playerRef,
            UICommandBuilder uiCommandBuilder,
            UIEventBuilder uiEventBuilder,
            Store<EntityStore> store) {
        uiCommandBuilder.append("HyProTech_Battery_Rack_S.ui");
        bindSideButtons(uiEventBuilder);
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#UpgradeButton",
                EventData.of("Action", ACTION_UPGRADE));

        World world = getWorld(store);
        ItemContainer inventory = getPlayerInventory(store);
        updateForWorld(uiCommandBuilder, world, inventory);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, SideToggleEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        String action = data.getAction();
        if (ACTION_TOGGLE.equalsIgnoreCase(action)) {
            handleToggle(store, data);
            return;
        }
        if (ACTION_UPGRADE.equalsIgnoreCase(action)) {
            if (!handleUpgrade(playerRef, store)) {
                sendUpdate(new UICommandBuilder());
            }
        }
    }

    private void handleToggle(Store<EntityStore> store, SideToggleEvent data) {
        EnergySide side = EnergySide.fromName(data.getSide());
        if (side == null) {
            return;
        }

        EntityStore entityStore = store.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        if (world == null) {
            return;
        }

        EnergyNodeComponent node = resolveNode(world);
        if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.BATTERY) {
            return;
        }

        node.cycleSideMode(side);
        storeNode(world, node);
        update(node);
    }

    private boolean handleUpgrade(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        World world = getWorld(store);
        if (world == null) {
            return false;
        }

        EnergyNodeComponent node = resolveNode(world);
        if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.BATTERY) {
            return false;
        }

        int currentTier = resolveTier(world);
        if (!BatteryUpgradeConfig.hasNextTier(currentTier)) {
            sendPlayerMessage(playerRef, store, "Battery is already at max tier.");
            return false;
        }

        ItemContainer inventory = getPlayerInventory(store);
        if (inventory == null) {
            return false;
        }

        BatteryUpgradeConfig.Requirement[] requirements =
                BatteryUpgradeConfig.getUpgradeRequirements(currentTier);
        List<ItemStack> stacks = new ArrayList<>(requirements.length);
        for (BatteryUpgradeConfig.Requirement requirement : requirements) {
            stacks.add(new ItemStack(requirement.getItemId(), requirement.getQuantity()));
        }

        if (!stacks.isEmpty() && !inventory.canRemoveItemStacks(stacks)) {
            sendPlayerMessage(playerRef, store, "Missing upgrade materials.");
            return false;
        }

        if (!stacks.isEmpty()) {
            ListTransaction<ItemStackTransaction> transaction = inventory.removeItemStacks(stacks);
            if (transaction == null || !transaction.succeeded()) {
                sendPlayerMessage(playerRef, store, "Upgrade failed.");
                return false;
            }
        }

        int nextTier = currentTier + 1;
        applyBatteryTier(node, nextTier);
        storeNode(world, node);

        Vector3i pos = resolveBlockPosition(world);
        if (pos != null) {
            String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_BATTERY, nextTier);
            BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String blockId = blockType == null ? null : blockType.getId();
            upgradedId = TieredIdUtil.applyNamespace(blockId, HyProTechIds.BLOCK_BATTERY, upgradedId);
            UpgradePersistence.queueBlockSwap(world, pos, upgradedId, node, null);
        }

        sendPlayerMessage(
                playerRef,
                store,
                "Upgraded battery to " + BatteryUpgradeConfig.getTierName(nextTier) + ".");
        update(node, true);
        return true;
    }

    public void update(EnergyNodeComponent node) {
        update(node, false);
    }

    private void update(EnergyNodeComponent node, boolean force) {
        if (node == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastUpdateMs != 0L && now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> store = playerEntityRef == null ? null : playerEntityRef.getStore();
        World world = getWorld(store);
        ItemContainer inventory = store == null ? null : getPlayerInventory(store);

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = updateForNode(update, node, world, inventory);
        if (changed) {
            sendUpdate(update);
        } else if (force) {
            sendUpdate(new UICommandBuilder());
        }
        lastUpdateMs = now;
    }

    private boolean updateForWorld(UICommandBuilder update, World world, ItemContainer inventory) {
        if (world == null) {
            showNoNode(update);
            return true;
        }
        EnergyNodeComponent node = resolveNode(world);
        if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.BATTERY) {
            showNoNode(update);
            return true;
        }
        return updateForNode(update, node, world, inventory);
    }

    private boolean updateForNode(
            UICommandBuilder update,
            EnergyNodeComponent node,
            World world,
            ItemContainer inventory) {
        boolean changed = false;

        int tier = resolveTier(world);
        if (tier != lastTier) {
            update.set("#BatteryTier.Text", "Tier: " + BatteryUpgradeConfig.getTierName(tier));
            lastTier = tier;
            changed = true;
        }

        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#BatteryEnergy.Text",
                    "Energy: " + EnergyUnits.formatEnergyWithCapacity(energy, capacity));
            lastEnergy = energy;
            lastCapacity = capacity;
            changed = true;
        }

        int maxTransfer = node.getMaxTransfer();
        if (maxTransfer != lastMaxTransfer) {
            update.set("#BatteryTransfer.Text",
                    "Max Transfer: " + EnergyUnits.formatJoulesPerSecond(maxTransfer));
            lastMaxTransfer = maxTransfer;
            changed = true;
        }

        String reqKey = updateUpgradePanel(update, tier, inventory);
        if (!reqKey.equals(lastReqKey)) {
            lastReqKey = reqKey;
            changed = true;
        }

        int inputMask = node.getInputMask();
        int outputMask = node.getOutputMask();
        int connectedMask = node.getConnectedMask();
        if (inputMask != lastInputMask || outputMask != lastOutputMask || connectedMask != lastConnectedMask) {
            updateSideButtons(update, node);
            lastInputMask = inputMask;
            lastOutputMask = outputMask;
            lastConnectedMask = connectedMask;
            changed = true;
        }

        return changed;
    }

    private String updateUpgradePanel(UICommandBuilder update, int tier, ItemContainer inventory) {
        boolean hasNextTier = BatteryUpgradeConfig.hasNextTier(tier);
        String nextText = hasNextTier
                ? "Next: " + BatteryUpgradeConfig.getTierName(tier + 1)
                : "Next: Max";
        update.set("#BatteryNextTier.Text", nextText);

        String statsText = hasNextTier
                ? "Capacity: " + EnergyUnits.formatJoules(BatteryUpgradeConfig.getCapacityForTier(tier + 1))
                        + " | Transfer: " + EnergyUnits.formatJoulesPerSecond(
                                BatteryUpgradeConfig.getMaxTransferForTier(tier + 1))
                : "Max tier reached.";
        update.set("#BatteryUpgradeStats.Text", statsText);

        BatteryUpgradeConfig.Requirement[] requirements =
                hasNextTier ? BatteryUpgradeConfig.getUpgradeRequirements(tier)
                        : new BatteryUpgradeConfig.Requirement[0];
        int[] owned = new int[requirements.length];
        boolean canUpgrade = hasNextTier && inventory != null;
        StringBuilder reqKey = new StringBuilder();
        for (int i = 0; i < requirements.length; i++) {
            BatteryUpgradeConfig.Requirement requirement = requirements[i];
            owned[i] = inventory == null ? 0 : countItem(inventory, requirement.getItemId());
            if (owned[i] < requirement.getQuantity()) {
                canUpgrade = false;
            }
            reqKey.append(requirement.getItemId())
                    .append('=')
                    .append(owned[i])
                    .append('/');
        }

        String buttonText = hasNextTier
                ? (canUpgrade ? "Upgrade" : "Missing Items")
                : "Max Tier";
        update.set("#UpgradeButton.Text", buttonText);
        if (!buttonText.equals(lastButtonText)) {
            lastButtonText = buttonText;
        }

        update.set("#UpgradeReqEmpty.Text", "No further upgrades.");
        update.set("#UpgradeReqEmpty.Visible", !hasNextTier);
        for (int i = 0; i < UPGRADE_ROW_IDS.length; i++) {
            boolean visible = hasNextTier && i < requirements.length;
            update.set(UPGRADE_ROW_IDS[i] + ".Visible", visible);
            if (visible) {
                BatteryUpgradeConfig.Requirement requirement = requirements[i];
                update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", UiItemIds.safeItemId(requirement.getItemId()));
                update.set(UPGRADE_NAME_IDS[i] + ".Text", formatRequirementName(requirement.getItemId()));
                update.set(UPGRADE_QTY_IDS[i] + ".Text", owned[i] + "/" + requirement.getQuantity());
            } else {
                update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", "");
                update.set(UPGRADE_NAME_IDS[i] + ".Text", "");
                update.set(UPGRADE_QTY_IDS[i] + ".Text", "");
            }
        }

        reqKey.append("tier=").append(tier).append("|button=").append(buttonText);
        return reqKey.toString();
    }

    private void showNoNode(UICommandBuilder update) {
        update.set("#BatteryTier.Text", "Tier: -");
        update.set("#BatteryEnergy.Text", "Energy: -");
        update.set("#BatteryTransfer.Text", "Max Transfer: -");
        update.set("#BatteryNextTier.Text", "Next: -");
        update.set("#BatteryUpgradeStats.Text", "");
        update.set("#UpgradeButton.Text", "No Battery");
        update.set("#UpgradeReqEmpty.Text", "No battery found.");
        update.set("#UpgradeReqEmpty.Visible", true);
        for (int i = 0; i < UPGRADE_ROW_IDS.length; i++) {
            update.set(UPGRADE_ROW_IDS[i] + ".Visible", false);
            update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", "");
            update.set(UPGRADE_NAME_IDS[i] + ".Text", "");
            update.set(UPGRADE_QTY_IDS[i] + ".Text", "");
        }
    }

    private void applyBatteryTier(EnergyNodeComponent node, int tier) {
        int capacity = BatteryUpgradeConfig.getCapacityForTier(tier);
        node.setCapacity(capacity);
        if (node.getEnergy() > capacity) {
            node.setEnergy(capacity);
        }
        node.setMaxTransfer(BatteryUpgradeConfig.getMaxTransferForTier(tier));
    }

    private void bindSideButtons(UIEventBuilder uiEventBuilder) {
        for (EnergySide side : EnergySide.VALUES) {
            EventData data = EventData.of("Action", ACTION_TOGGLE).append("Side", side.name());
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sideId(side), data);
        }
    }

    private void updateSideButtons(UICommandBuilder update, EnergyNodeComponent node) {
        for (EnergySide side : EnergySide.VALUES) {
            boolean connected = node.isSideConnected(side);
            update.set(sideId(side) + ".Visible", connected);
            if (connected) {
                update.set(sideId(side) + ".Text",
                        side.label() + ": " + node.getSideMode(side).label());
            }
        }
    }

    private String sideId(EnergySide side) {
        return "#Side" + side.label();
    }

    Ref<ChunkStore> resolveBlockRef(World world) {
        if (world == null) {
            return blockRef;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            return blockRef;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return blockRef;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        Ref<ChunkStore> ref = blockComponents.getEntityReferences().get(blockIndex);
        if (ref != null && (blockRef == null || ref.getIndex() != blockRef.getIndex())) {
            blockRef = ref;
        }
        return blockRef;
    }

    private EnergyNodeComponent resolveNode(World world) {
        if (world == null) {
            return null;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockComponentChunk blockComponents =
                    world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (blockComponents != null) {
                int localX = ChunkUtil.localCoordinate((long) pos.getX());
                int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
                int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
                EnergyNodeComponent node = blockComponents.getComponent(blockIndex, energyType);
                if (node != null) {
                    return node;
                }
            }
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> resolvedRef = resolveBlockRef(world);
        try {
            return chunkStore.getComponent(resolvedRef, energyType);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private void storeNode(World world, EnergyNodeComponent node) {
        if (world == null || node == null) {
            return;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockComponentChunk blockComponents =
                    world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (blockComponents != null) {
                int localX = ChunkUtil.localCoordinate((long) pos.getX());
                int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
                int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
                Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
                if (ref != null && !ref.isValid()) {
                    blockComponents.removeEntityReference(blockIndex, ref);
                    ref = null;
                }
                if (ref != null) {
                    Store<ChunkStore> store = world.getChunkStore().getStore();
                    if (store != null) {
                        store.putComponent(ref, energyType, node);
                    }
                } else {
                    Holder<ChunkStore> holder = blockComponents.getEntityHolder(blockIndex);
                    if (holder == null) {
                        holder = ChunkStore.REGISTRY.newHolder();
                        holder.putComponent(energyType, node);
                        blockComponents.storeEntityHolder(blockIndex, holder);
                    } else {
                        holder.putComponent(energyType, node);
                    }
                }
                blockComponents.markNeedsSaving();
                return;
            }
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> resolvedRef = resolveBlockRef(world);
        try {
            chunkStore.putComponent(resolvedRef, energyType, node);
        } catch (IllegalStateException ignored) {
            // Ref may be stale right after a block swap.
        }
    }

    private int resolveTier(World world) {
        if (world == null) {
            return BatteryUpgradeConfig.MIN_TIER;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            return BatteryUpgradeConfig.MIN_TIER;
        }
        BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        String blockId = blockType == null ? null : blockType.getId();
        if (blockId == null || blockId.isEmpty()) {
            return BatteryUpgradeConfig.MIN_TIER;
        }
        int tier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_BATTERY);
        if (tier < 0) {
            tier = BatteryUpgradeConfig.MIN_TIER;
        }
        return BatteryUpgradeConfig.clampTier(tier);
    }

    Vector3i resolveBlockPosition(World world) {
        if (world == null) {
            return null;
        }
        if (blockPosition != null) {
            return blockPosition;
        }

        ChunkStore chunkStoreResource = world.getChunkStore();
        for (long chunkIndex : chunkStoreResource.getChunkIndexes()) {
            BlockComponentChunk blockComponents =
                    chunkStoreResource.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (blockComponents == null) {
                continue;
            }

            int blockIndex = findBlockIndex(blockComponents);
            if (blockIndex == Integer.MIN_VALUE) {
                continue;
            }

            int chunkX = ChunkUtil.xOfChunkIndex(chunkIndex);
            int chunkZ = ChunkUtil.zOfChunkIndex(chunkIndex);
            int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
            int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
            int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

            int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
            int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
            blockPosition = new Vector3i(worldX, localY, worldZ);
            return blockPosition;
        }

        return null;
    }

    private int findBlockIndex(BlockComponentChunk blockComponents) {
        int targetIndex = blockRef.getIndex();
        if (targetIndex == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        for (it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry<Ref<ChunkStore>> entry
                : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            Ref<ChunkStore> entryRef = entry.getValue();
            if (entryRef != null && entryRef.getIndex() == targetIndex) {
                return entry.getIntKey();
            }
        }
        return Integer.MIN_VALUE;
    }

    private ItemContainer getPlayerInventory(Store<EntityStore> store) {
        if (store == null) {
            return null;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return null;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        return inventory == null ? null : inventory.getStorage();
    }

    private int countItem(ItemContainer container, String itemId) {
        if (container == null || itemId == null) {
            return 0;
        }
        short capacity = container.getCapacity();
        int total = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            if (itemId.equals(stack.getItemId())) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    private String formatRequirementName(String itemId) {
        if (itemId == null) {
            return "";
        }
        String name = itemId;
        String ingotPrefix = "Ingredient_Bar_";
        if (name.startsWith(ingotPrefix)) {
            name = name.substring(ingotPrefix.length()) + " Ingot";
        }
        String hidePrefix = "Ingredient_Hide_";
        if (name.startsWith(hidePrefix)) {
            name = name.substring(hidePrefix.length()) + " Hide";
        }
        String leatherPrefix = "Ingredient_Leather_";
        if (name.startsWith(leatherPrefix)) {
            name = name.substring(leatherPrefix.length()) + " Leather";
        }
        String ingredientPrefix = "Ingredient_";
        if (name.startsWith(ingredientPrefix)) {
            name = name.substring(ingredientPrefix.length());
        }
        return name.replace('_', ' ');
    }

    private void sendPlayerMessage(Ref<EntityStore> playerRef, Store<EntityStore> store, String text) {
        if (store == null || playerRef == null) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.sendMessage(Message.raw(text));
    }

    private World getWorld(Store<EntityStore> store) {
        EntityStore entityStore = store == null ? null : store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }
}
