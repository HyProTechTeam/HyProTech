package HyProTechTeam.ui;

import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.UpgradePersistence;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergyUnits;
import HyProTechTeam.machine.QuarryConfig;
import HyProTechTeam.machine.MachineComponent;
import HyProTechTeam.machine.MachineItemAccess;
import HyProTechTeam.machine.QuarryAreaManager;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;

public class QuarryPage extends InteractiveCustomUIPage<SideToggleEvent> {
    private static final String ACTION_UPGRADE = "Upgrade";
    private static final String ACTION_TAKE_ITEMS = "TakeItems";
    private static final String PAGE_LAYOUT = "HyProTech_Quarry.ui";
    private static final long UPDATE_INTERVAL_MS = 250L;
    private static final int STORAGE_SLOT_COUNT = 10;
    private static final String[] STORAGE_SLOT_IDS = buildSlotIds("#StorageSlot");
    private static final String[] STORAGE_QTY_IDS = buildSlotIds("#StorageQty");
    private static final int DEFAULT_AREA = 5;
    private static final int MIN_AREA = 2;
    private static final String GIVE_TORCH_LABEL = "Give Border Torch";
    private static final String ENABLE_LABEL = "TURN ON";
    private static final String DISABLE_LABEL = "TURN OFF";
    private static final String REPLACE_STATUS_ON = "Backfill: ON";
    private static final String REPLACE_STATUS_ON_LOCKED = "Backfill: ON (locked)";
    private static final String REPLACE_STATUS_OFF = "Backfill: OFF";
    private static final String REPLACE_ENABLE_LABEL = "BACKFILL ON";
    private static final String REPLACE_DISABLE_LABEL = "BACKFILL OFF";
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
    private final ComponentType<ChunkStore, MachineComponent> machineType;
    private Vector3i blockPosition;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastConsumption = Integer.MIN_VALUE;
    private int lastTier = Integer.MIN_VALUE;
    private String lastReqKey = "";
    private String lastButtonText = "";
    private String lastStorageKey = "";
    private String lastSlotsText = "";
    private int lastAreaWidth = Integer.MIN_VALUE;
    private int lastAreaDepth = Integer.MIN_VALUE;
    private Boolean lastAreaVisible;
    private Boolean lastEnabled;
    private Boolean lastReplaceToggleVisible;
    private String lastReplaceStatusText = "";
    private String lastReplaceButtonText = "";
    private long lastAreaToggleMs;
    private long lastUpdateMs;
    private long lastBorderParticleMs;

    public QuarryPage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, EnergyNodeComponent> energyType,
            ComponentType<ChunkStore, MachineComponent> machineType) {
        super(playerRef, CustomPageLifetime.CanDismiss, SideToggleEvent.CODEC);
        this.blockRef = blockRef;
        this.energyType = energyType;
        this.machineType = machineType;
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
        uiCommandBuilder.append(PAGE_LAYOUT);
        initStaticUi(uiCommandBuilder);
        bindButtons(uiEventBuilder);

        World world = getWorld(store);
        ItemContainer inventory = getPlayerInventory(store);
        updateForWorld(uiCommandBuilder, world, inventory);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, SideToggleEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        World world = getWorld(store);
        if (world == null) {
            return;
        }

        MachineComponent machine = resolveMachine(world);
        if (machine == null) {
            machine = new MachineComponent();
        }
        EnergyNodeComponent node = resolveNode(world);

        int tier = QuarryConfig.clampTier(machine.getTier());
        int maxArea = QuarryConfig.getMaxAreaForTier(tier);
        int width = clampArea(normalizeArea(machine.getAreaWidth()), maxArea);
        int depth = clampArea(normalizeArea(machine.getAreaDepth()), maxArea);
        boolean visible = machine.isAreaVisible();
        int newWidth = width;
        int newDepth = depth;
        boolean newVisible = visible;
        Vector3i pos = resolveBlockPosition(world);
        QuarryAreaManager.TorchBounds torchBounds =
                pos == null ? null : QuarryAreaManager.getTorchBounds(world, pos);
        boolean hasTorchBounds = torchBounds != null;

        String action = data.getAction();
        if (hasTorchBounds) {
            if ("WidthMinus".equalsIgnoreCase(action)
                    || "WidthPlus".equalsIgnoreCase(action)
                    || "DepthMinus".equalsIgnoreCase(action)
                    || "DepthPlus".equalsIgnoreCase(action)) {
                sendPlayerMessage(playerRef, store, "Area is defined by border torches.");
                return;
            }
        }
        if (ACTION_UPGRADE.equalsIgnoreCase(action)) {
            if (!handleUpgrade(playerRef, store, world, machine, node)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        } else if (ACTION_TAKE_ITEMS.equalsIgnoreCase(action)) {
            if (!handleTakeItems(store)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        } else if ("GiveTorch".equalsIgnoreCase(action)) {
            handleGiveTorch(store);
            return;
        } else if ("WidthMinus".equalsIgnoreCase(action)) {
            newWidth = clampArea(width - 1, maxArea);
        } else if ("WidthPlus".equalsIgnoreCase(action)) {
            newWidth = clampArea(width + 1, maxArea);
        } else if ("DepthMinus".equalsIgnoreCase(action)) {
            newDepth = clampArea(depth - 1, maxArea);
        } else if ("DepthPlus".equalsIgnoreCase(action)) {
            newDepth = clampArea(depth + 1, maxArea);
        } else if ("ToggleArea".equalsIgnoreCase(action)) {
            long now = System.currentTimeMillis();
            if (lastAreaToggleMs != 0L && now - lastAreaToggleMs < 300L) {
                return;
            }
            lastAreaToggleMs = now;
            newVisible = !visible;
        } else if ("ToggleEnabled".equalsIgnoreCase(action)) {
            machine.setEnabled(!machine.isEnabled());
            machine.setProgress(0);
            storeMachine(world, machine);
            UICommandBuilder update = new UICommandBuilder();
            updateControls(update, machine);
            sendUpdate(update);
            return;
        } else if ("ToggleReplace".equalsIgnoreCase(action)) {
            if (QuarryConfig.isForceReplaceBlocks()) {
                sendPlayerMessage(playerRef, store, "Backfill mode is locked by config.");
                return;
            }
            machine.setReplaceMinedBlocks(!machine.isReplaceMinedBlocks());
            storeMachine(world, machine);
            UICommandBuilder update = new UICommandBuilder();
            updateControls(update, machine);
            sendUpdate(update);
            return;
        } else {
            return;
        }

        boolean changed = false;
        if (newWidth != width || newDepth != depth) {
            machine.setAreaWidth(newWidth);
            machine.setAreaDepth(newDepth);
            storeMachine(world, machine);
            changed = true;
            if (visible && pos != null) {
                QuarryAreaManager.updateArea(world, pos, width, depth, newWidth, newDepth);
            }
        }

        if (newVisible != visible) {
            machine.setAreaVisible(newVisible);
            storeMachine(world, machine);
            changed = true;
            if (pos != null) {
                if (newVisible) {
                    QuarryAreaManager.showArea(world, pos, newWidth, newDepth);
                } else {
                    QuarryAreaManager.hideArea(world, pos, newWidth, newDepth);
                }
            }
        }

        if (changed) {
            UICommandBuilder update = new UICommandBuilder();
            updateArea(update, machine);
            updateControls(update, machine);
            String reqKey = updateUpgradePanel(update, machine, getPlayerInventory(store));
            if (!reqKey.equals(lastReqKey)) {
                lastReqKey = reqKey;
            }
            sendUpdate(update);
        } else {
            sendUpdate(new UICommandBuilder());
        }
    }

    public void update(EnergyNodeComponent node) {
        update(node, null, false);
    }

    public void update(EnergyNodeComponent node, MachineComponent machine) {
        update(node, machine, false);
    }

    private void update(EnergyNodeComponent node, MachineComponent machine, boolean force) {
        if (node == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastUpdateMs != 0L && now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> store = playerEntityRef == null ? null : playerEntityRef.getStore();
        ItemContainer inventory = store == null ? null : getPlayerInventory(store);

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = updateEnergy(update, node);
        changed |= updateArea(update, machine);
        changed |= updateControls(update, machine);
        String reqKey = updateUpgradePanel(update, machine, inventory);
        if (!reqKey.equals(lastReqKey)) {
            lastReqKey = reqKey;
            changed = true;
        }
        changed |= updateStorage(update);

        if (changed) {
            sendUpdate(update);
        } else if (force) {
            sendUpdate(new UICommandBuilder());
        }
        lastUpdateMs = now;
    }

    private void initStaticUi(UICommandBuilder update) {
        update.set("#QuarryTier.Text", "Tier: " + QuarryConfig.getTierName(QuarryConfig.MIN_TIER));
        update.set("#QuarryArea.Text", "Area: " + DEFAULT_AREA + " x " + DEFAULT_AREA);
        update.set("#GiveTorchButton.Text", GIVE_TORCH_LABEL);
        update.set("#QuarryStatus.Text", "Status: ON");
        update.set("#QuarryToggleButton.Text", DISABLE_LABEL);
        update.set("#QuarryReplaceStatus.Text", REPLACE_STATUS_OFF);
        update.set("#QuarryReplaceToggleButton.Text", REPLACE_ENABLE_LABEL);
        updateUpgradePanel(update, null, null);
    }

    private boolean updateForWorld(UICommandBuilder update, World world, ItemContainer inventory) {
        if (world == null) {
            return false;
        }
        EnergyNodeComponent node = resolveNode(world);
        MachineComponent machine = resolveMachine(world);
        if (node == null) {
            return false;
        }
        boolean changed = updateEnergy(update, node);
        changed |= updateArea(update, machine);
        changed |= updateControls(update, machine);
        String reqKey = updateUpgradePanel(update, machine, inventory);
        if (!reqKey.equals(lastReqKey)) {
            lastReqKey = reqKey;
            changed = true;
        }
        changed |= updateStorage(update);
        return changed;
    }

    private boolean updateEnergy(UICommandBuilder update, EnergyNodeComponent node) {
        boolean changed = false;

        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#QuarryEnergy.Text",
                    "Energy: " + EnergyUnits.formatEnergyWithCapacity(energy, capacity));
            lastEnergy = energy;
            lastCapacity = capacity;
            changed = true;
        }

        int consumption = node.getConsumption();
        if (consumption != lastConsumption) {
            update.set("#QuarryConsumption.Text",
                    "Consumption: " + EnergyUnits.formatJoulesPerSecond(consumption));
            lastConsumption = consumption;
            changed = true;
        }

        return changed;
    }

    private boolean updateStorage(UICommandBuilder update) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return false;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        World world = getWorld(store);
        Vector3i pos = resolveBlockPosition(world);
        if (world == null || pos == null) {
            return false;
        }

        ItemContainer container = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        if (container == null) {
            return updateEmptyStorage(update);
        }

        short capacity = container.getCapacity();
        int used = 0;
        StringBuilder key = new StringBuilder();
        key.append("cap=").append(capacity).append('|');

        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            String itemId = "";
            String qtyText = "";
            if (i < capacity) {
                ItemStack stack = container.getItemStack((short) i);
                if (stack != null && !ItemStack.isEmpty(stack)) {
                    itemId = stack.getItemId();
                    int qty = stack.getQuantity();
                    qtyText = qty > 1 ? Integer.toString(qty) : "";
                    used++;
                }
            }
            key.append(itemId).append(':').append(qtyText).append('|');
        }

        String slotsText = "Slots: " + used + "/" + capacity;
        boolean changed = false;
        if (!slotsText.equals(lastSlotsText)) {
            update.set("#QuarryStorageSlots.Text", slotsText);
            lastSlotsText = slotsText;
            changed = true;
        }

        String newKey = key.toString();
        if (!newKey.equals(lastStorageKey)) {
            for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
                String itemId = "";
                String qtyText = "";
                if (i < capacity) {
                    ItemStack stack = container.getItemStack((short) i);
                    if (stack != null && !ItemStack.isEmpty(stack)) {
                        itemId = stack.getItemId();
                        int qty = stack.getQuantity();
                        qtyText = qty > 1 ? Integer.toString(qty) : "";
                    }
                }
                update.set(STORAGE_SLOT_IDS[i] + ".ItemId", UiItemIds.safeItemId(itemId));
                update.set(STORAGE_QTY_IDS[i] + ".Text", qtyText);
            }
            lastStorageKey = newKey;
            changed = true;
        }

        return changed;
    }

    private boolean updateEmptyStorage(UICommandBuilder update) {
        boolean changed = false;
        String slotsText = "Slots: 0/0";
        if (!slotsText.equals(lastSlotsText)) {
            update.set("#QuarryStorageSlots.Text", slotsText);
            lastSlotsText = slotsText;
            changed = true;
        }
        if (!"empty".equals(lastStorageKey)) {
            for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
                update.set(STORAGE_SLOT_IDS[i] + ".ItemId", "");
                update.set(STORAGE_QTY_IDS[i] + ".Text", "");
            }
            lastStorageKey = "empty";
            changed = true;
        }
        return changed;
    }

    private boolean updateArea(UICommandBuilder update, MachineComponent machine) {
        int tier = machine == null ? QuarryConfig.MIN_TIER : QuarryConfig.clampTier(machine.getTier());
        int maxArea = QuarryConfig.getMaxAreaForTier(tier);
        int width = clampArea(normalizeArea(machine == null ? 0 : machine.getAreaWidth()), maxArea);
        int depth = clampArea(normalizeArea(machine == null ? 0 : machine.getAreaDepth()), maxArea);
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> store = playerEntityRef == null ? null : playerEntityRef.getStore();
        World world = getWorld(store);
        Vector3i pos = resolveBlockPosition(world);
        QuarryAreaManager.TorchBounds torchBounds =
                world != null && pos != null ? QuarryAreaManager.getTorchBounds(world, pos) : null;
        String torchText = "";
        if (torchBounds != null) {
            width = torchBounds.getWidth();
            depth = torchBounds.getDepth();
            torchText = "Torch bounds: "
                    + torchBounds.getWidth() + " x " + torchBounds.getDepth()
                    + " (" + torchBounds.minX + "," + torchBounds.minZ
                    + " -> " + torchBounds.maxX + "," + torchBounds.maxZ + ")";
        } else if (world != null && pos != null) {
            torchText = "Torch bounds: NOT FOUND";
        }
        boolean visible = machine != null && machine.isAreaVisible();

        boolean changed = false;
        if (width != lastAreaWidth || depth != lastAreaDepth) {
            update.set("#QuarryArea.Text", "Area: " + width + " x " + depth);
            lastAreaWidth = width;
            lastAreaDepth = depth;
            changed = true;
        }
        update.set("#QuarryTorchDebug.Text", torchText);
        if (lastAreaVisible == null || visible != lastAreaVisible) {
            lastAreaVisible = visible;
            changed = true;
        }
        if (visible) {
            long now = System.currentTimeMillis();
            if (lastBorderParticleMs == 0L || now - lastBorderParticleMs > 1000L) {
                if (world != null && pos != null) {
                    QuarryAreaManager.spawnBorderParticles(world, pos, width, depth);
                }
                lastBorderParticleMs = now;
            }
        }
        return changed;
    }

    private boolean updateControls(UICommandBuilder update, MachineComponent machine) {
        boolean changed = false;
        boolean enabled = machine == null || machine.isEnabled();
        if (lastEnabled == null || enabled != lastEnabled) {
            update.set("#QuarryStatus.Text", enabled ? "Status: ON" : "Status: OFF");
            update.set("#QuarryToggleButton.Text", enabled ? DISABLE_LABEL : ENABLE_LABEL);
            lastEnabled = enabled;
            changed = true;
        }

        boolean forceReplace = QuarryConfig.isForceReplaceBlocks();
        boolean replaceMode = forceReplace || (machine != null && machine.isReplaceMinedBlocks());
        String replaceStatus = replaceMode
                ? (forceReplace ? REPLACE_STATUS_ON_LOCKED : REPLACE_STATUS_ON)
                : REPLACE_STATUS_OFF;
        if (!replaceStatus.equals(lastReplaceStatusText)) {
            update.set("#QuarryReplaceStatus.Text", replaceStatus);
            lastReplaceStatusText = replaceStatus;
            changed = true;
        }

        boolean toggleVisible = !forceReplace;
        if (lastReplaceToggleVisible == null || toggleVisible != lastReplaceToggleVisible) {
            update.set("#QuarryReplaceToggleButton.Visible", toggleVisible);
            lastReplaceToggleVisible = toggleVisible;
            changed = true;
        }

        String replaceButtonText = replaceMode ? REPLACE_DISABLE_LABEL : REPLACE_ENABLE_LABEL;
        if (!replaceButtonText.equals(lastReplaceButtonText)) {
            update.set("#QuarryReplaceToggleButton.Text", replaceButtonText);
            lastReplaceButtonText = replaceButtonText;
            changed = true;
        }

        return changed;
    }

    private String updateUpgradePanel(UICommandBuilder update, MachineComponent machine, ItemContainer inventory) {
        int tier = machine == null ? QuarryConfig.MIN_TIER : QuarryConfig.clampTier(machine.getTier());
        if (tier != lastTier) {
            update.set("#QuarryTier.Text", "Tier: " + QuarryConfig.getTierName(tier));
            lastTier = tier;
        }

        boolean hasNextTier = QuarryConfig.hasNextTier(tier);
        String nextText = hasNextTier
                ? "Next: " + QuarryConfig.getTierName(tier + 1)
                : "Next: Max";
        update.set("#QuarryNextTier.Text", nextText);

        String statsText = hasNextTier
                ? "Capacity: " + EnergyUnits.formatJoules(QuarryConfig.getCapacityForTier(tier + 1))
                        + " | Consumption: "
                        + EnergyUnits.formatJoulesPerSecond(QuarryConfig.getConsumptionPerSecond(tier + 1))
                        + " | Speed: " + String.format(java.util.Locale.US, "%.2fs",
                                QuarryConfig.getMiningSecondsForTier(tier + 1))
                        + " | Max Area: " + QuarryConfig.getMaxAreaForTier(tier + 1)
                        + "x" + QuarryConfig.getMaxAreaForTier(tier + 1)
                : "Max tier reached.";
        update.set("#QuarryUpgradeStats.Text", statsText);

        QuarryConfig.Requirement[] requirements =
                hasNextTier ? QuarryConfig.getUpgradeRequirements(tier)
                        : new QuarryConfig.Requirement[0];
        int[] owned = new int[requirements.length];
        boolean canUpgrade = hasNextTier && inventory != null;
        StringBuilder reqKey = new StringBuilder();
        for (int i = 0; i < requirements.length; i++) {
            QuarryConfig.Requirement requirement = requirements[i];
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
                QuarryConfig.Requirement requirement = requirements[i];
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

    private boolean handleUpgrade(
            Ref<EntityStore> playerRef,
            Store<EntityStore> store,
            World world,
            MachineComponent machine,
            EnergyNodeComponent node) {
        if (world == null || machine == null || node == null) {
            return false;
        }
        int currentTier = QuarryConfig.clampTier(machine.getTier());
        if (!QuarryConfig.hasNextTier(currentTier)) {
            sendPlayerMessage(playerRef, store, "Quarry is already at max tier.");
            return false;
        }

        ItemContainer inventory = getPlayerInventory(store);
        if (inventory == null) {
            return false;
        }

        QuarryConfig.Requirement[] requirements =
                QuarryConfig.getUpgradeRequirements(currentTier);
        List<ItemStack> stacks = new ArrayList<>(requirements.length);
        for (QuarryConfig.Requirement requirement : requirements) {
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
        machine.setTier(nextTier);
        applyTier(machine, node, nextTier);

        storeMachine(world, machine);
        storeNode(world, node);

        Vector3i pos = resolveBlockPosition(world);
        if (pos != null) {
            String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_QUARRY, nextTier);
            BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String blockId = blockType == null ? null : blockType.getId();
            upgradedId = TieredIdUtil.applyNamespace(blockId, HyProTechIds.BLOCK_QUARRY, upgradedId);
            UpgradePersistence.queueBlockSwapWithContainer(world, pos, upgradedId, node);
        }

        sendPlayerMessage(
                playerRef,
                store,
                "Upgraded quarry to " + QuarryConfig.getTierName(nextTier) + ".");
        update(node, machine, true);
        return true;
    }

    private boolean handleTakeItems(Store<EntityStore> store) {
        if (store == null) {
            return false;
        }
        World world = getWorld(store);
        if (world == null) {
            return false;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            return false;
        }
        ItemContainer container = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        if (container == null) {
            return false;
        }
        Inventory inventory = getPlayerInventoryFull(store);
        if (inventory == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        boolean movedAny = false;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            ItemContainer target = null;
            if (hotbar != null && hotbar.canAddItemStack(stack)) {
                target = hotbar;
            } else if (storage != null && storage.canAddItemStack(stack)) {
                target = storage;
            }
            if (target == null) {
                continue;
            }
            int quantity = stack.getQuantity();
            MoveTransaction<?> move = container.moveItemStackFromSlot(slot, quantity, target);
            if (move == null || !move.succeeded()) {
                move = container.moveItemStackFromSlot(slot, target);
            }
            if (move != null && move.succeeded()) {
                movedAny = true;
            }
        }
        if (movedAny) {
            MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
            return refreshStorage(store);
        }
        return false;
    }

    private void handleGiveTorch(Store<EntityStore> store) {
        if (store == null) {
            return;
        }
        Inventory inventory = getPlayerInventoryFull(store);
        if (inventory == null) {
            return;
        }
        ItemStack torch = new ItemStack(HyProTechIds.BLOCK_BORDER_TORCH, 2);
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        if (hotbar != null && hotbar.canAddItemStack(torch)) {
            hotbar.addItemStack(torch);
            return;
        }
        if (storage != null && storage.canAddItemStack(torch)) {
            storage.addItemStack(torch);
            return;
        }
        sendPlayerMessage(playerRef.getReference(), store, "No space for Border Torch.");
    }

    private void applyTier(MachineComponent machine, EnergyNodeComponent node, int tier) {
        int capacity = QuarryConfig.getCapacityForTier(tier);
        node.setCapacity(capacity);
        if (node.getEnergy() > capacity) {
            node.setEnergy(capacity);
        }
        node.setConsumption(QuarryConfig.getConsumptionPerSecond(tier));
        node.setMaxTransfer(QuarryConfig.getMaxTransferForTier(tier));

        int progressMax = QuarryConfig.getMiningDelayTicks(tier);
        machine.setProgressMax(progressMax);
        if (machine.getProgress() > progressMax) {
            machine.setProgress(progressMax);
        }
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

    private Inventory getPlayerInventoryFull(Store<EntityStore> store) {
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
        return player.getInventory();
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

    private MachineComponent resolveMachine(World world) {
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
                MachineComponent machine = blockComponents.getComponent(blockIndex, machineType);
                if (machine != null) {
                    return machine;
                }
            }
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> resolvedRef = resolveBlockRef(world);
        try {
            return chunkStore.getComponent(resolvedRef, machineType);
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

    private void storeMachine(World world, MachineComponent machine) {
        if (world == null || machine == null) {
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
                        store.putComponent(ref, machineType, machine);
                    }
                } else {
                    Holder<ChunkStore> holder = blockComponents.getEntityHolder(blockIndex);
                    if (holder == null) {
                        holder = ChunkStore.REGISTRY.newHolder();
                        holder.putComponent(machineType, machine);
                        blockComponents.storeEntityHolder(blockIndex, holder);
                    } else {
                        holder.putComponent(machineType, machine);
                    }
                }
                blockComponents.markNeedsSaving();
                return;
            }
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> resolvedRef = resolveBlockRef(world);
        try {
            chunkStore.putComponent(resolvedRef, machineType, machine);
        } catch (IllegalStateException ignored) {
            // Ref may be stale right after a block swap.
        }
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

    private World getWorld(Store<EntityStore> store) {
        EntityStore entityStore = store == null ? null : store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }

    private static String[] buildSlotIds(String prefix) {
        String[] ids = new String[STORAGE_SLOT_COUNT];
        for (int i = 0; i < STORAGE_SLOT_COUNT; i++) {
            ids[i] = prefix + (i + 1);
        }
        return ids;
    }

    private boolean refreshStorage(Store<EntityStore> store) {
        UICommandBuilder update = new UICommandBuilder();
        if (updateStorage(update)) {
            sendUpdate(update);
            return true;
        }
        return false;
    }

    private void bindButtons(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#GiveTorchButton",
                EventData.of("Action", "GiveTorch"));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#UpgradeButton",
                EventData.of("Action", ACTION_UPGRADE));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TakeItemsButton",
                EventData.of("Action", ACTION_TAKE_ITEMS));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#QuarryToggleButton",
                EventData.of("Action", "ToggleEnabled"));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#QuarryReplaceToggleButton",
                EventData.of("Action", "ToggleReplace"));
    }

    private static int normalizeArea(int value) {
        return value > 0 ? value : DEFAULT_AREA;
    }

    private static int clampArea(int value, int max) {
        if (value < MIN_AREA) {
            return MIN_AREA;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

}
