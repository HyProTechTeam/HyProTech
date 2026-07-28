package HyProTechTeam.ui;

import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.UpgradePersistence;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergyUnits;
import HyProTechTeam.machine.MachineComponent;
import HyProTechTeam.machine.MachineItemAccess;
import HyProTechTeam.machine.OreCrusherConfig;
import HyProTechTeam.machine.OreCrusherMachine;
import HyProTechTeam.machine.OreCrusherRecipes;
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
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
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
import java.util.Locale;

public class OreCrusherPage extends InteractiveCustomUIPage<OreCrusherUiEvent> implements WindowlessPage {
    private static final String ACTION_UPGRADE = "Upgrade";
    private static final String ACTION_TOGGLE = "ToggleEnabled";
    private static final String ACTION_DRAG_START = "DragStart";
    private static final String ACTION_INPUT_CLICK = "InputClick";
    private static final String ACTION_OUTPUT_CLICK = "OutputClick";
    private static final String ACTION_INVENTORY_CLICK = "InventoryClick";
    private static final long CLICK_CURSOR_TIMEOUT_MS = 5000L;
    private static final String ACTION_INPUT_DROP = "InputDrop";
    private static final String ACTION_OUTPUT_DROP = "OutputDrop";
    private static final String ACTION_INVENTORY_DROP = "InventoryDrop";
    private static final String ACTION_TAKE_INPUT = "TakeInput";
    private static final String ACTION_TAKE_OUTPUT = "TakeOutput";
    private static final String ACTION_RECIPE_SELECT = "RecipeSelect";
    private static final String ACTION_RECIPE_LOAD = "RecipeLoad";
    private static final String ACTION_RECIPE_QTY_MINUS = "RecipeQtyMinus";
    private static final String ACTION_RECIPE_QTY_PLUS = "RecipeQtyPlus";
    private static final String ACTION_RECIPE_QTY_PLUS_TEN = "RecipeQtyPlusTen";
    private static final String ACTION_RECIPE_QTY_ALL = "RecipeQtyAll";
    private static final String PAGE_LAYOUT = "HyProTech_OreCrusher_HyUI_v2.ui";
    private static final long UPDATE_INTERVAL_MS = 250L;
    private static final long DRAG_DEDUP_WINDOW_MS = 120L;
    private static final long DRAG_SOURCE_WINDOW_MS = 1500L;
    private static final long INPUT_DROP_SUPPRESS_MS = 250L;
    private static final String ENABLE_LABEL = "TURN ON";
    private static final String DISABLE_LABEL = "TURN OFF";
    private static final short INPUT_SLOT = 0;
    private static final short OUTPUT_SLOT_START = 1;
    private static final int OUTPUT_SLOT_COUNT = OreCrusherConfig.OUTPUT_SLOT_COUNT;
    private static final short OUTPUT_SLOT_END =
            (short) (OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT - 1);
    // ItemGrid index mapping follows UI order in HyProTech_OreCrusher_HyUI.ui
    private static final int GRID_INDEX_RECIPE = 0;
    private static final int GRID_INDEX_INPUT = 1;
    private static final int GRID_INDEX_OUTPUT = 2;
    private static final int GRID_INDEX_PLAYER = 3;
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
    private static final String[] BONUS_ROW_IDS = {
            "#BonusRow1",
            "#BonusRow2",
            "#BonusRow3",
            "#BonusRow4",
            "#BonusRow5"
    };
    private static final String[] BONUS_SLOT_IDS = {
            "#BonusSlot1",
            "#BonusSlot2",
            "#BonusSlot3",
            "#BonusSlot4",
            "#BonusSlot5"
    };
    private static final String[] BONUS_NAME_IDS = {
            "#BonusName1",
            "#BonusName2",
            "#BonusName3",
            "#BonusName4",
            "#BonusName5"
    };
    private static final String[] BONUS_CHANCE_IDS = {
            "#BonusChance1",
            "#BonusChance2",
            "#BonusChance3",
            "#BonusChance4",
            "#BonusChance5"
    };

    private Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private final ComponentType<ChunkStore, MachineComponent> machineType;
    private Vector3i blockPosition;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastConsumption = Integer.MIN_VALUE;
    private int lastTier = Integer.MIN_VALUE;
    private int lastYield = Integer.MIN_VALUE;
    private int lastProgress = Integer.MIN_VALUE;
    private int lastProgressMax = Integer.MIN_VALUE;
    private String lastReqKey = "";
    private String lastButtonText = "";
    private String lastInputKey = "";
    private String lastOutputKey = "";
    private String lastInventoryKey = "";
    private String lastBonusKey = "";
    private String lastRecipeGridKey = "";
    private String lastRecipeDetailsKey = "";
    private String lastDragKey = "";
    private Boolean lastEnabled;
    private long lastUpdateMs;
    private long lastDragMs;
    private DragSnapshot lastDragSource;
    private DragSnapshot clickCursor;
    private boolean suppressNextDrag;
    private long lastInputDropMs;
    private String lastInputDropItemId;
    private long lastInputToInventoryMs;
    private String lastInputToInventoryItemId;
    private int selectedRecipeIndex = -1;
    private int recipeQuantity = 1;

    public OreCrusherPage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, EnergyNodeComponent> energyType,
            ComponentType<ChunkStore, MachineComponent> machineType) {
        super(playerRef, CustomPageLifetime.CanDismiss, OreCrusherUiEvent.CODEC);
        this.blockRef = blockRef;
        this.energyType = energyType;
        this.machineType = machineType;
    }

    public Ref<ChunkStore> getBlockRef() {
        return blockRef;
    }

    public void setBlockPosition(Vector3i blockPosition) {
        this.blockPosition = blockPosition;
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
        bindItemGrids(uiEventBuilder);

        World world = getWorld(store);
        ItemContainer inventory = getPlayerInventory(store);
        updateForWorld(uiCommandBuilder, world, inventory, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, OreCrusherUiEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        String action = data.getAction();
        if (ACTION_DRAG_START.equalsIgnoreCase(action)) {
            captureDragSource(data);
            return;
        }
        if (ACTION_INPUT_CLICK.equalsIgnoreCase(action)) {
            debugClickEvent("InputClick", data);
            if (handleClickDrop(store, data, GridType.INPUT)) {
                return;
            }
            captureClickSource(GridType.INPUT, data);
            sendUpdate(new UICommandBuilder());
            return;
        }
        if (ACTION_OUTPUT_CLICK.equalsIgnoreCase(action)) {
            debugClickEvent("OutputClick", data);
            if (handleClickDrop(store, data, GridType.OUTPUT)) {
                return;
            }
            captureClickSource(GridType.OUTPUT, data);
            sendUpdate(new UICommandBuilder());
            return;
        }
        if (ACTION_INVENTORY_CLICK.equalsIgnoreCase(action)) {
            debugClickEvent("InventoryClick", data);
            if (handleClickDrop(store, data, GridType.PLAYER)) {
                return;
            }
            captureClickSource(GridType.PLAYER, data);
            sendUpdate(new UICommandBuilder());
            return;
        }
        if (ACTION_RECIPE_SELECT.equalsIgnoreCase(action)) {
            handleRecipeSelect(store, data);
            return;
        }
        if (isDragAction(action)) {
            handleDrag(store, data);
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

        if (ACTION_UPGRADE.equalsIgnoreCase(action)) {
            if (!handleUpgrade(playerRef, store, world, machine, node)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        }
        if (ACTION_TOGGLE.equalsIgnoreCase(action)) {
            machine.setEnabled(!machine.isEnabled());
            machine.setProgress(0);
            storeMachine(world, machine);
            UICommandBuilder update = new UICommandBuilder();
            updateControls(update, machine);
            sendUpdate(update);
            return;
        }
        if (ACTION_TAKE_INPUT.equalsIgnoreCase(action)) {
            if (!handleTakeInput(store)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        }
        if (ACTION_TAKE_OUTPUT.equalsIgnoreCase(action)) {
            if (!handleTakeOutput(store)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        }
        if (ACTION_RECIPE_LOAD.equalsIgnoreCase(action)) {
            if (!handleLoadRecipe(store)) {
                sendUpdate(new UICommandBuilder());
            }
            return;
        }
        if (ACTION_RECIPE_QTY_MINUS.equalsIgnoreCase(action)
                || ACTION_RECIPE_QTY_PLUS.equalsIgnoreCase(action)
                || ACTION_RECIPE_QTY_PLUS_TEN.equalsIgnoreCase(action)
                || ACTION_RECIPE_QTY_ALL.equalsIgnoreCase(action)) {
            handleRecipeQuantity(action, store);
            return;
        }
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
        ItemContainer recipeInventory = store == null ? null : getCombinedInventory(store);

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = updateEnergy(update, node);
        changed |= updateProgress(update, machine);
        changed |= updateYield(update, machine);
        changed |= updateControls(update, machine);
        String reqKey = updateUpgradePanel(update, machine, inventory);
        if (!reqKey.equals(lastReqKey)) {
            lastReqKey = reqKey;
            changed = true;
        }
        changed |= updateSlots(update);
        changed |= updateRecipeGrid(update);
        changed |= updateRecipeDetails(update, machine, recipeInventory);
        changed |= updateBonusPanel(update, machine, resolveMachineContainer(store));

        if (changed) {
            sendUpdate(update);
        } else if (force) {
            sendUpdate(new UICommandBuilder());
        }
        lastUpdateMs = now;
    }

    private void initStaticUi(UICommandBuilder update) {
        update.set("#CrusherTier.Text", "Tier: " + OreCrusherConfig.getTierName(OreCrusherConfig.MIN_TIER));
        update.set("#CrusherEnergy.Text", "Energy: 0 / 0");
        update.set("#CrusherConsumption.Text", "Consumption: 0 J/s");
        update.set("#CrusherProgress.Text", "Progress: 0%");
        update.set("#CrusherYield.Text", "Yield: 2x");
        update.set("#CrusherStatus.Text", "Status: ON");
        update.set("#CrusherToggleButton.Text", DISABLE_LABEL);
        update.set("#HiddenMachineGrids.Visible", true);
        updateUpgradePanel(update, null, null);
        update.set("#InputGrid.Slots", buildEmptySlots(1, true));
        update.set("#OutputGrid.Slots", buildEmptySlots(OUTPUT_SLOT_COUNT, true));
        // Player inventory grid removed from this UI.
        updateRecipeGrid(update);
        updateRecipeDetails(update, null, null);
        updateBonusPanel(update, null, null);
    }

    private boolean updateForWorld(
            UICommandBuilder update,
            World world,
            ItemContainer inventory,
            Store<EntityStore> store) {
        if (world == null) {
            return false;
        }
        EnergyNodeComponent node = resolveNode(world);
        MachineComponent machine = resolveMachine(world);
        if (world != null) {
            Vector3i pos = resolveBlockPosition(world);
            if (pos != null) {
                BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
                String blockId = blockType == null ? null : blockType.getId();
                int parsedTier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ORE_CRUSHER);
                if (parsedTier > 0 && machine != null && machine.getTier() != parsedTier && node != null) {
                    machine.setTier(parsedTier);
                    applyTier(machine, node, parsedTier);
                    storeMachine(world, machine);
                    storeNode(world, node);
                }
            }
        }
        if (node == null) {
            return false;
        }
        boolean changed = updateEnergy(update, node);
        changed |= updateProgress(update, machine);
        changed |= updateYield(update, machine);
        changed |= updateControls(update, machine);
        String reqKey = updateUpgradePanel(update, machine, inventory);
        if (!reqKey.equals(lastReqKey)) {
            lastReqKey = reqKey;
            changed = true;
        }
        changed |= updateSlots(update);
        changed |= updateRecipeGrid(update);
        ItemContainer recipeInventory = store == null ? null : getCombinedInventory(store);
        changed |= updateRecipeDetails(update, machine, recipeInventory);
        ItemContainer container = null;
        Vector3i pos = resolveBlockPosition(world);
        if (pos != null) {
            container = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        }
        changed |= updateBonusPanel(update, machine, container);
        return changed;
    }

    private boolean updateEnergy(UICommandBuilder update, EnergyNodeComponent node) {
        boolean changed = false;
        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#CrusherEnergy.Text",
                    "Energy: " + EnergyUnits.formatEnergyWithCapacity(energy, capacity));
            lastEnergy = energy;
            lastCapacity = capacity;
            changed = true;
        }

        int consumption = node.getConsumption();
        if (consumption != lastConsumption) {
            update.set("#CrusherConsumption.Text",
                    "Consumption: " + EnergyUnits.formatJoulesPerSecond(consumption));
            lastConsumption = consumption;
            changed = true;
        }

        return changed;
    }

    private boolean updateProgress(UICommandBuilder update, MachineComponent machine) {
        if (machine == null) {
            return false;
        }
        int progressMax = Math.max(1, machine.getProgressMax());
        int progress = Math.max(0, machine.getProgress());
        if (progress != lastProgress || progressMax != lastProgressMax) {
            int percent = (int) Math.round(100.0 * progress / progressMax);
            update.set("#CrusherProgress.Text", "Progress: " + percent + "%");
            lastProgress = progress;
            lastProgressMax = progressMax;
            return true;
        }
        return false;
    }

    private boolean updateYield(UICommandBuilder update, MachineComponent machine) {
        int tier = machine == null ? OreCrusherConfig.MIN_TIER : OreCrusherConfig.clampTier(machine.getTier());
        int yield = OreCrusherConfig.getOutputMultiplierForTier(tier);
        if (yield != lastYield) {
            update.set("#CrusherYield.Text", "Yield: " + yield + "x");
            lastYield = yield;
            return true;
        }
        return false;
    }

    private boolean updateControls(UICommandBuilder update, MachineComponent machine) {
        boolean enabled = machine == null || machine.isEnabled();
        if (lastEnabled == null || enabled != lastEnabled) {
            update.set("#CrusherStatus.Text", enabled ? "Status: ON" : "Status: OFF");
            update.set("#CrusherToggleButton.Text", enabled ? DISABLE_LABEL : ENABLE_LABEL);
            lastEnabled = enabled;
            return true;
        }
        return false;
    }

    private boolean updateSlots(UICommandBuilder update) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return false;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        World world = getWorld(store);
        Vector3i pos = resolveBlockPosition(world);
        if (world == null || pos == null) {
            return updatePlayerInventorySlots(update, store);
        }
        ItemContainer container = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        boolean changed = updateMachineSlots(update, container);
        changed |= updatePlayerInventorySlots(update, store);
        return changed;
    }

    private boolean updateMachineSlots(UICommandBuilder update, ItemContainer container) {
        if (container == null) {
            return updateEmptyMachineSlots(update);
        }

        ItemStack input = container.getCapacity() > INPUT_SLOT
                ? container.getItemStack(INPUT_SLOT)
                : null;

        String inputKey = buildSlotKey(input);
        String outputKey = buildOutputKey(container);
        boolean changed = false;

        if (!inputKey.equals(lastInputKey)) {
            update.set("#InputGrid.Slots", buildSingleSlotList(input));
            lastInputKey = inputKey;
            changed = true;
        }
        if (!outputKey.equals(lastOutputKey)) {
            update.set("#OutputGrid.Slots", buildOutputSlots(container));
            lastOutputKey = outputKey;
            changed = true;
        }

        return changed;
    }

    private boolean updatePlayerInventorySlots(UICommandBuilder update, Store<EntityStore> store) {
        // Player inventory grid removed from this UI.
        return false;
    }

    private boolean updateEmptyMachineSlots(UICommandBuilder update) {
        boolean changed = false;
        if (!"empty".equals(lastInputKey)) {
            update.set("#InputGrid.Slots", buildEmptySlots(1, true));
            lastInputKey = "empty";
            changed = true;
        }
        if (!"empty".equals(lastOutputKey)) {
            update.set("#OutputGrid.Slots", buildEmptySlots(OUTPUT_SLOT_COUNT, true));
            lastOutputKey = "empty";
            changed = true;
        }
        return changed;
    }

    private String updateUpgradePanel(UICommandBuilder update, MachineComponent machine, ItemContainer inventory) {
        int tier = machine == null ? OreCrusherConfig.MIN_TIER : OreCrusherConfig.clampTier(machine.getTier());
        if (tier != lastTier) {
            update.set("#CrusherTier.Text", "Tier: " + OreCrusherConfig.getTierName(tier));
            lastTier = tier;
        }

        boolean hasNextTier = OreCrusherConfig.hasNextTier(tier);
        String nextText = hasNextTier
                ? "Next: " + OreCrusherConfig.getTierName(tier + 1)
                : "Next: Max";
        update.set("#CrusherNextTier.Text", nextText);

        String statsText = hasNextTier
                ? "Capacity: " + EnergyUnits.formatJoules(OreCrusherConfig.getCapacityForTier(tier + 1))
                        + " | Consumption: "
                        + EnergyUnits.formatJoulesPerSecond(OreCrusherConfig.getConsumptionPerSecond(tier + 1))
                        + " | Speed: " + String.format(
                                Locale.US,
                                "%.2fs",
                                OreCrusherConfig.getProcessingSecondsForTier(tier + 1))
                        + " | Yield: " + OreCrusherConfig.getOutputMultiplierForTier(tier + 1) + "x"
                : "Max tier reached.";
        update.set("#CrusherUpgradeStats.Text", statsText);

        OreCrusherConfig.Requirement[] requirements =
                hasNextTier ? OreCrusherConfig.getUpgradeRequirements(tier)
                        : new OreCrusherConfig.Requirement[0];
        int visibleRequirementCount = Math.min(requirements.length, UPGRADE_ROW_IDS.length);
        int[] owned = new int[visibleRequirementCount];
        boolean canUpgrade = hasNextTier && inventory != null;
        StringBuilder reqKey = new StringBuilder();
        for (int i = 0; i < visibleRequirementCount; i++) {
            OreCrusherConfig.Requirement requirement = requirements[i];
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
            boolean visible = hasNextTier && i < visibleRequirementCount;
            update.set(UPGRADE_ROW_IDS[i] + ".Visible", visible);
            if (visible) {
                OreCrusherConfig.Requirement requirement = requirements[i];
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

    private boolean updateBonusPanel(UICommandBuilder update, MachineComponent machine, ItemContainer container) {
        int tier = machine == null ? OreCrusherConfig.MIN_TIER : OreCrusherConfig.clampTier(machine.getTier());
        ItemStack input = container == null || container.getCapacity() <= INPUT_SLOT
                ? null
                : container.getItemStack(INPUT_SLOT);
        String oreId = input == null || ItemStack.isEmpty(input) ? null : input.getItemId();
        OreCrusherRecipes.RecipeEntry recipe = oreId == null ? null : OreCrusherRecipes.findByInput(oreId);
        String recipeOutputId = recipe == null ? null : recipe.outputItemId;
        List<OreCrusherConfig.BonusDrop> drops = OreCrusherConfig.getBonusDropsForOre(oreId, recipeOutputId);

        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(tier).append('|');
        for (OreCrusherConfig.BonusDrop drop : drops) {
            if (drop == null) {
                continue;
            }
            keyBuilder.append(drop.getItemId())
                    .append(':')
                    .append(drop.getQuantity())
                    .append(':')
                    .append(Math.round(drop.getChance(tier) * 100))
                    .append('|');
        }
        String bonusKey = keyBuilder.toString();
        if (bonusKey.equals(lastBonusKey)) {
            return false;
        }
        lastBonusKey = bonusKey;

        update.set("#BonusHeader.Text", "Bonus Drops");
        boolean hasDrops = !drops.isEmpty();
        update.set("#BonusEmpty.Visible", !hasDrops);
        update.set("#BonusEmpty.Text", hasDrops ? "" : "Insert ore to see bonus drops.");

        int count = Math.min(drops.size(), BONUS_ROW_IDS.length);
        for (int i = 0; i < BONUS_ROW_IDS.length; i++) {
            boolean visible = i < count;
            update.set(BONUS_ROW_IDS[i] + ".Visible", visible);
            if (!visible) {
                continue;
            }
            OreCrusherConfig.BonusDrop drop = drops.get(i);
            if (drop == null) {
                update.set(BONUS_SLOT_IDS[i] + ".ItemId", UiItemIds.safeItemId(null));
                update.set(BONUS_NAME_IDS[i] + ".Text", "");
                update.set(BONUS_CHANCE_IDS[i] + ".Text", "");
                continue;
            }
            String bonusItemId = drop.getItemId();
            if ("__ore_powder__".equals(bonusItemId)) {
                bonusItemId = OreCrusherConfig.resolvePowderId(oreId, recipeOutputId);
            }
            if ("__random_crystal__".equals(bonusItemId)) {
                bonusItemId = "Ingredient_Crystal_Blue";
            }
            update.set(BONUS_SLOT_IDS[i] + ".ItemId", UiItemIds.safeItemId(bonusItemId));
            update.set(BONUS_NAME_IDS[i] + ".Text", formatBonusName(bonusItemId));
            int percent = (int) Math.round(drop.getChance(tier) * 100);
            update.set(BONUS_CHANCE_IDS[i] + ".Text", percent + "%");
        }

        return true;
    }

    private boolean updateRecipeGrid(UICommandBuilder update) {
        List<OreCrusherRecipes.RecipeEntry> recipes = OreCrusherRecipes.getRecipes();
        StringBuilder keyBuilder = new StringBuilder();
        for (OreCrusherRecipes.RecipeEntry entry : recipes) {
            if (entry == null) {
                continue;
            }
            keyBuilder.append(entry.inputItemId).append('|');
        }
        String key = keyBuilder.toString();
        if (key.equals(lastRecipeGridKey)) {
            return false;
        }
        lastRecipeGridKey = key;

        List<ItemGridSlot> slots = new ArrayList<>(recipes.size());
        for (OreCrusherRecipes.RecipeEntry entry : recipes) {
            if (entry == null) {
                continue;
            }
            ItemStack stack = new ItemStack(entry.inputItemId, Math.max(1, entry.inputQuantity));
            slots.add(createSlot(stack, true));
        }
        update.set("#RecipeGrid.Slots", slots);
        return true;
    }

    private boolean updateRecipeDetails(UICommandBuilder update, MachineComponent machine, ItemContainer inventory) {
        OreCrusherRecipes.RecipeEntry entry = getSelectedRecipeEntry();
        if (entry == null) {
            String key = "empty";
            if (key.equals(lastRecipeDetailsKey)) {
                return false;
            }
            lastRecipeDetailsKey = key;
            update.set("#RecipeDetailsEmpty.Visible", true);
            update.set("#RecipeDetails.Visible", false);
            update.set("#RecipeEmptyText.Text", "Select a recipe to see requirements.");
            clearRecipeDetails(update);
            return true;
        }

        int quantity = Math.max(1, recipeQuantity);
        int outputQty = Math.max(1, entry.outputQuantity) * quantity;
        int available = inventory == null ? 0 : countItem(inventory, entry.inputItemId);
        int needed = Math.max(1, entry.inputQuantity) * quantity;
        String detailKey = entry.inputItemId + "|" + quantity + "|" + outputQty + "|" + available;
        if (detailKey.equals(lastRecipeDetailsKey)) {
            return false;
        }
        lastRecipeDetailsKey = detailKey;

        update.set("#RecipeDetailsEmpty.Visible", false);
        update.set("#RecipeDetails.Visible", true);
        update.set("#RecipeOutputSlot.ItemId", UiItemIds.safeItemId(entry.outputItemId));
        update.set("#RecipeOutputName.Text", formatItemName(entry.outputItemId));
        update.set("#RecipeOutputQty.Text", "x" + outputQty);

        MaterialQuantity[] inputs = entry.inputs == null ? MaterialQuantity.EMPTY_ARRAY : entry.inputs;
        int row = 0;
        for (MaterialQuantity input : inputs) {
            if (input == null || input.getItemId() == null || input.getItemId().isEmpty()) {
                continue;
            }
            if (row >= 4) {
                break;
            }
            int qty = Math.max(1, input.getQuantity()) * quantity;
            update.set("#RecipeReqRow" + (row + 1) + ".Visible", true);
            update.set("#RecipeReqSlot" + (row + 1) + ".ItemId", UiItemIds.safeItemId(input.getItemId()));
            update.set("#RecipeReqText" + (row + 1) + ".Text",
                    formatItemName(input.getItemId()) + " x" + qty);
            row++;
        }
        for (int i = row; i < 4; i++) {
            update.set("#RecipeReqRow" + (i + 1) + ".Visible", false);
            update.set("#RecipeReqSlot" + (i + 1) + ".ItemId", "");
            update.set("#RecipeReqText" + (i + 1) + ".Text", "");
        }

        update.set("#RecipeQtyLabel.Text", "x" + quantity);
        update.set("#RecipeCostText.Text", "Need " + needed + " | Have " + available);
        return true;
    }

    private void clearRecipeDetails(UICommandBuilder update) {
        update.set("#RecipeOutputSlot.ItemId", "");
        update.set("#RecipeOutputName.Text", "");
        update.set("#RecipeOutputQty.Text", "");
        for (int i = 1; i <= 4; i++) {
            update.set("#RecipeReqRow" + i + ".Visible", false);
            update.set("#RecipeReqSlot" + i + ".ItemId", "");
            update.set("#RecipeReqText" + i + ".Text", "");
        }
        update.set("#RecipeQtyLabel.Text", "x1");
        update.set("#RecipeCostText.Text", "");
    }

    private void handleRecipeSelect(Store<EntityStore> store, OreCrusherUiEvent data) {
        List<OreCrusherRecipes.RecipeEntry> recipes = OreCrusherRecipes.getRecipes();
        if (recipes.isEmpty()) {
            return;
        }
        Integer slotIndex = data == null ? null : data.getSlotIndex();
        if (slotIndex == null) {
            slotIndex = data == null ? null : data.getSourceSlotId();
        }
        if (slotIndex == null) {
            return;
        }
        int index = slotIndex;
        if (index < 0 || index >= recipes.size()) {
            return;
        }
        selectedRecipeIndex = index;
        recipeQuantity = 1;

        UICommandBuilder update = new UICommandBuilder();
        ItemContainer inventory = store == null ? null : getCombinedInventory(store);
        updateRecipeDetails(update, null, inventory);
        sendUpdate(update);
    }

    private void handleRecipeQuantity(String action, Store<EntityStore> store) {
        OreCrusherRecipes.RecipeEntry entry = getSelectedRecipeEntry();
        if (entry == null) {
            return;
        }
        int max = resolveMaxRecipeQuantity(entry, store);
        int quantity = Math.max(1, recipeQuantity);
        if (ACTION_RECIPE_QTY_MINUS.equalsIgnoreCase(action)) {
            quantity = Math.max(1, quantity - 1);
        } else if (ACTION_RECIPE_QTY_PLUS.equalsIgnoreCase(action)) {
            quantity = Math.min(max, quantity + 1);
        } else if (ACTION_RECIPE_QTY_PLUS_TEN.equalsIgnoreCase(action)) {
            quantity = Math.min(max, quantity + 10);
        } else if (ACTION_RECIPE_QTY_ALL.equalsIgnoreCase(action)) {
            quantity = max;
        }
        if (quantity != recipeQuantity) {
            recipeQuantity = quantity;
            UICommandBuilder update = new UICommandBuilder();
            ItemContainer inventory = store == null ? null : getCombinedInventory(store);
            updateRecipeDetails(update, null, inventory);
            sendUpdate(update);
        }
    }

    private boolean handleLoadRecipe(Store<EntityStore> store) {
        if (store == null) {
            return false;
        }
        OreCrusherRecipes.RecipeEntry entry = getSelectedRecipeEntry();
        if (entry == null) {
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
        ItemContainer machineContainer = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        if (machineContainer == null || machineContainer.getCapacity() <= INPUT_SLOT) {
            return false;
        }

        int quantity = Math.max(1, recipeQuantity);
        int required = Math.max(1, entry.inputQuantity) * quantity;
        ItemStack requiredStack = new ItemStack(entry.inputItemId, required);

        ItemStack existing = machineContainer.getItemStack(INPUT_SLOT);
        boolean slotEmpty = existing == null || ItemStack.isEmpty(existing);
        if (existing != null && !ItemStack.isEmpty(existing)
                && !existing.getItemId().equalsIgnoreCase(entry.inputItemId)) {
            sendPlayerMessage(playerRef.getReference(), store, "Input slot is occupied.");
            return false;
        }
        boolean canAdd = machineContainer.canAddItemStackToSlot(INPUT_SLOT, requiredStack, true, true);
        boolean forceInsert = false;
        if (!canAdd) {
            if (!slotEmpty) {
                sendPlayerMessage(playerRef.getReference(), store, "Input slot is full.");
                return false;
            }
            forceInsert = true;
        }

        Inventory inventoryFull = getPlayerInventoryFull(store);
        if (inventoryFull == null) {
            return false;
        }
        ItemContainer combined = inventoryFull.getCombinedHotbarFirst();
        if (combined == null) {
            combined = inventoryFull.getCombinedStorageFirst();
        }
        if (combined == null) {
            return false;
        }
        if (!combined.canRemoveItemStack(requiredStack)) {
            sendPlayerMessage(playerRef.getReference(), store, "Missing ore.");
            return false;
        }
        ItemStackTransaction remove = combined.removeItemStack(requiredStack);
        if (remove == null || !remove.succeeded()) {
            sendPlayerMessage(playerRef.getReference(), store, "Failed to take ore.");
            return false;
        }

        ItemStackSlotTransaction add = forceInsert
                ? machineContainer.setItemStackForSlot(INPUT_SLOT, requiredStack, true)
                : machineContainer.addItemStackToSlot(INPUT_SLOT, requiredStack);
        if (add == null || !add.succeeded()) {
            ItemStackTransaction refund = combined.addItemStack(requiredStack);
            if (refund == null || !refund.succeeded()) {
                sendPlayerMessage(playerRef.getReference(), store, "Failed to load ore (refund failed).");
            } else {
                sendPlayerMessage(
                        playerRef.getReference(),
                        store,
                        forceInsert ? "Invalid ore for this machine." : "Failed to load ore.");
            }
            return false;
        }
        MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
        refreshSlots(store);

        UICommandBuilder update = new UICommandBuilder();
        ItemContainer inventory = getCombinedInventory(store);
        updateRecipeDetails(update, null, inventory);
        sendUpdate(update);
        return true;
    }

    private int resolveMaxRecipeQuantity(OreCrusherRecipes.RecipeEntry entry, Store<EntityStore> store) {
        if (entry == null || store == null) {
            return 1;
        }
        ItemContainer inventory = getCombinedInventory(store);
        if (inventory == null) {
            return 1;
        }
        int available = countItem(inventory, entry.inputItemId);
        int perRecipe = Math.max(1, entry.inputQuantity);
        int max = available / perRecipe;
        return Math.max(1, max);
    }

    private OreCrusherRecipes.RecipeEntry getSelectedRecipeEntry() {
        List<OreCrusherRecipes.RecipeEntry> recipes = OreCrusherRecipes.getRecipes();
        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= recipes.size()) {
            return null;
        }
        return recipes.get(selectedRecipeIndex);
    }

    private static String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "";
        }
        String raw = itemId;
        int colonIndex = raw.indexOf(':');
        if (colonIndex >= 0) {
            raw = raw.substring(colonIndex + 1);
        }
        boolean isOre = raw.startsWith("Ore_");
        raw = raw.replaceFirst("^HyProTech_", "");
        raw = raw.replaceFirst("^Ingredient_", "");
        if (isOre && raw.length() > 4) {
            raw = raw.substring(4);
        }
        raw = raw.replace('_', ' ');
        String title = toTitleCase(raw);
        if (isOre) {
            return title + " Ore";
        }
        return title;
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] parts = input.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            String word = part;
            if (part.length() > 1) {
                word = part.substring(0, 1).toUpperCase(Locale.ROOT)
                        + part.substring(1).toLowerCase(Locale.ROOT);
            } else {
                word = part.toUpperCase(Locale.ROOT);
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word);
        }
        return out.toString();
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
        int currentTier = OreCrusherConfig.clampTier(machine.getTier());
        if (!OreCrusherConfig.hasNextTier(currentTier)) {
            sendPlayerMessage(playerRef, store, "Ore crusher is already at max tier.");
            return false;
        }

        ItemContainer inventory = getPlayerInventory(store);
        if (inventory == null) {
            return false;
        }

        OreCrusherConfig.Requirement[] requirements =
                OreCrusherConfig.getUpgradeRequirements(currentTier);
        int visibleRequirementCount = Math.min(requirements.length, UPGRADE_ROW_IDS.length);
        List<ItemStack> stacks = new ArrayList<>(visibleRequirementCount);
        for (int i = 0; i < visibleRequirementCount; i++) {
            OreCrusherConfig.Requirement requirement = requirements[i];
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
            String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ORE_CRUSHER, nextTier);
            BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            String blockId = blockType == null ? null : blockType.getId();
            upgradedId = TieredIdUtil.applyNamespace(blockId, HyProTechIds.BLOCK_ORE_CRUSHER, upgradedId);
            UpgradePersistence.queueBlockSwapWithContainer(world, pos, upgradedId, node);
        }

        sendPlayerMessage(
                playerRef,
                store,
                "Upgraded ore crusher to " + OreCrusherConfig.getTierName(nextTier) + ".");
        update(node, machine, true);
        return true;
    }

    private boolean handleTakeInput(Store<EntityStore> store) {
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
        ItemContainer machineContainer = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        if (machineContainer == null || machineContainer.getCapacity() <= INPUT_SLOT) {
            return false;
        }
        ItemStack input = machineContainer.getItemStack(INPUT_SLOT);
        if (input == null || ItemStack.isEmpty(input)) {
            return false;
        }
        Inventory inventory = getPlayerInventoryFull(store);
        if (inventory == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        ItemContainer target = null;
        if (hotbar != null && hotbar.canAddItemStack(input)) {
            target = hotbar;
        } else if (storage != null && storage.canAddItemStack(input)) {
            target = storage;
        }
        if (target == null) {
            return false;
        }
        int quantity = input.getQuantity();
        MoveTransaction<?> move = machineContainer.moveItemStackFromSlot(INPUT_SLOT, quantity, target);
        if (move == null || !move.succeeded()) {
            move = machineContainer.moveItemStackFromSlot(INPUT_SLOT, target);
        }
        if (move != null && move.succeeded()) {
            MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
            return refreshSlots(store);
        }
        return false;
    }

    private boolean handleTakeOutput(Store<EntityStore> store) {
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
        ItemContainer machineContainer = MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
        if (machineContainer == null || machineContainer.getCapacity() <= OUTPUT_SLOT_START) {
            return false;
        }
        Inventory inventory = getPlayerInventoryFull(store);
        if (inventory == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        boolean movedAny = false;
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            short slot = (short) (OUTPUT_SLOT_START + i);
            if (slot >= machineContainer.getCapacity()) {
                continue;
            }
            ItemStack stack = machineContainer.getItemStack(slot);
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
            MoveTransaction<?> move = machineContainer.moveItemStackFromSlot(slot, quantity, target);
            if (move == null || !move.succeeded()) {
                move = machineContainer.moveItemStackFromSlot(slot, target);
            }
            if (move != null && move.succeeded()) {
                movedAny = true;
            }
        }
        if (movedAny) {
            MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
            return refreshSlots(store);
        }
        return false;
    }

    private void applyTier(MachineComponent machine, EnergyNodeComponent node, int tier) {
        int capacity = OreCrusherConfig.getCapacityForTier(tier);
        node.setCapacity(capacity);
        if (node.getEnergy() > capacity) {
            node.setEnergy(capacity);
        }
        node.setConsumption(OreCrusherConfig.getConsumptionPerSecond(tier));
        node.setMaxTransfer(OreCrusherConfig.getMaxTransferForTier(tier));

        int progressMax = OreCrusherConfig.getProcessingDelayTicks(tier);
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

    private ItemContainer getCombinedInventory(Store<EntityStore> store) {
        Inventory inventory = getPlayerInventoryFull(store);
        if (inventory == null) {
            return null;
        }
        ItemContainer combined = inventory.getCombinedHotbarFirst();
        if (combined == null) {
            combined = inventory.getCombinedStorageFirst();
        }
        return combined;
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

    private String formatBonusName(String itemId) {
        if (itemId == null) {
            return "";
        }
        if ("__random_crystal__".equals(itemId)) {
            return "Random Crystal";
        }
        String name = itemId;
        String HyProTechPrefix = "HyProTech_";
        if (name.startsWith(HyProTechPrefix)) {
            name = name.substring(HyProTechPrefix.length());
        }
        String ingredientPrefix = "Ingredient_";
        if (name.startsWith(ingredientPrefix)) {
            name = name.substring(ingredientPrefix.length());
        }
        return name.replace('_', ' ');
    }

    private String buildSlotKey(ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) {
            return "";
        }
        String key = stack.getItemId() + ":" + stack.getQuantity();
        if (stack.getMetadata() != null) {
            key += ":" + stack.getMetadata().hashCode();
        }
        return key;
    }

    private String buildOutputKey(ItemContainer container) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            ItemStack stack = getOutputStack(container, i);
            appendStackKey(key, stack);
        }
        return key.toString();
    }

    private List<ItemGridSlot> buildEmptySlots(int count, boolean skipQualityBackground) {
        List<ItemGridSlot> slots = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            slots.add(createSlot(null, skipQualityBackground));
        }
        return slots;
    }

    private List<ItemGridSlot> buildSingleSlotList(ItemStack stack) {
        List<ItemGridSlot> slots = new ArrayList<>(1);
        slots.add(createSlot(stack, true));
        return slots;
    }

    private List<ItemGridSlot> buildOutputSlots(ItemContainer container) {
        List<ItemGridSlot> slots = new ArrayList<>(OUTPUT_SLOT_COUNT);
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            slots.add(createSlot(getOutputStack(container, i), true));
        }
        return slots;
    }

    private List<ItemGridSlot> buildPlayerSlots(
            Inventory inventory,
            int hotbarCapacity,
            int storageCapacity) {
        List<ItemGridSlot> slots = new ArrayList<>(hotbarCapacity + storageCapacity);
        ItemContainer hotbar = inventory == null ? null : inventory.getHotbar();
        ItemContainer storage = inventory == null ? null : inventory.getStorage();
        appendSlotsFromContainer(slots, hotbar, hotbarCapacity);
        appendSlotsFromContainer(slots, storage, storageCapacity);
        return slots;
    }

    private void appendSlotsFromContainer(
            List<ItemGridSlot> slots,
            ItemContainer container,
            int capacity) {
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = null;
            if (container != null && i < container.getCapacity()) {
                stack = container.getItemStack((short) i);
            }
            slots.add(createSlot(stack, true));
        }
    }

    private ItemStack getOutputStack(ItemContainer container, int outputIndex) {
        if (container == null || outputIndex < 0 || outputIndex >= OUTPUT_SLOT_COUNT) {
            return null;
        }
        short slot = (short) (OUTPUT_SLOT_START + outputIndex);
        if (slot < 0 || slot >= container.getCapacity()) {
            return null;
        }
        return container.getItemStack(slot);
    }

    private ItemGridSlot createSlot(ItemStack stack, boolean skipQualityBackground) {
        ItemGridSlot slot = new ItemGridSlot();
        boolean hasItem = stack != null && !ItemStack.isEmpty(stack);
        // Allow drops into empty slots; otherwise input grid cannot accept items by drag.
        slot.setActivatable(true);
        slot.setSkipItemQualityBackground(skipQualityBackground);
        if (hasItem) {
            slot.setItemStack(copyStack(stack));
        }
        return slot;
    }

    private ItemStack copyStack(ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) {
            return null;
        }
        return new ItemStack(stack.getItemId(), stack.getQuantity(), stack.getMetadata());
    }

    private int getHotbarCapacity(Inventory inventory) {
        ItemContainer hotbar = inventory == null ? null : inventory.getHotbar();
        return hotbar == null ? Inventory.DEFAULT_HOTBAR_CAPACITY : hotbar.getCapacity();
    }

    private int getStorageCapacity(Inventory inventory) {
        ItemContainer storage = inventory == null ? null : inventory.getStorage();
        return storage == null ? Inventory.DEFAULT_STORAGE_CAPACITY : storage.getCapacity();
    }

    private String buildInventoryKey(Inventory inventory, int hotbarCapacity, int storageCapacity) {
        StringBuilder key = new StringBuilder();
        ItemContainer hotbar = inventory == null ? null : inventory.getHotbar();
        ItemContainer storage = inventory == null ? null : inventory.getStorage();
        appendInventoryKey(key, hotbar, hotbarCapacity);
        appendInventoryKey(key, storage, storageCapacity);
        return key.toString();
    }

    private void appendInventoryKey(StringBuilder key, ItemContainer container, int capacity) {
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = null;
            if (container != null && i < container.getCapacity()) {
                stack = container.getItemStack((short) i);
            }
            appendStackKey(key, stack);
        }
    }

    private void appendStackKey(StringBuilder key, ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) {
            key.append("|");
            return;
        }
        key.append(stack.getItemId())
                .append(':')
                .append(stack.getQuantity());
        if (stack.getMetadata() != null) {
            key.append(':').append(stack.getMetadata().hashCode());
        }
        key.append('|');
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

    private ItemContainer resolveMachineContainer(Store<EntityStore> store) {
        World world = getWorld(store);
        if (world == null) {
            return null;
        }
        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            return null;
        }
        return MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
    }

    private void bindItemGrids(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotClicking,
                "#RecipeGrid",
                EventData.of("Action", ACTION_RECIPE_SELECT));
        bindDrag(uiEventBuilder, "#InputGrid", ACTION_INPUT_DROP);
        bindDrag(uiEventBuilder, "#OutputGrid", ACTION_OUTPUT_DROP);
        bindClick(uiEventBuilder, "#InputGrid", ACTION_INPUT_CLICK);
        bindClick(uiEventBuilder, "#OutputGrid", ACTION_OUTPUT_CLICK);
    }

    private void bindDrag(UIEventBuilder uiEventBuilder, String selector, String action) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotClickPressWhileDragging,
                selector,
                EventData.of("Action", ACTION_DRAG_START));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Dropped,
                selector,
                EventData.of("Action", action));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotClickReleaseWhileDragging,
                selector,
                EventData.of("Action", action));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotMouseDragCompleted,
                selector,
                EventData.of("Action", action));
    }

    private void bindClick(UIEventBuilder uiEventBuilder, String selector, String action) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotClicking,
                selector,
                EventData.of("Action", action));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotDoubleClicking,
                selector,
                EventData.of("Action", action));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotClickReleaseWhileDragging,
                selector,
                EventData.of("Action", action));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.SlotMouseDragCompleted,
                selector,
                EventData.of("Action", action));
    }

    private boolean isDragAction(String action) {
        if (action == null) {
            return false;
        }
        return ACTION_INPUT_DROP.equalsIgnoreCase(action)
                || ACTION_OUTPUT_DROP.equalsIgnoreCase(action)
                || ACTION_INVENTORY_DROP.equalsIgnoreCase(action);
    }

    private boolean handleClickDrop(Store<EntityStore> store, OreCrusherUiEvent data, GridType targetGrid) {
        if (store == null || data == null || targetGrid == null) {
            return false;
        }
        Integer targetSlot = data.getSlotIndex();
        if (targetSlot == null) {
            return false;
        }
        String action = actionForTarget(targetGrid);
        if (action == null) {
            return false;
        }
        boolean hasExplicitSource =
                data.getSourceSlotId() != null
                        || data.getSourceInventorySectionId() != null
                        || data.getSourceItemGridIndex() != null;
        if (hasExplicitSource) {
            OreCrusherUiEvent dropEvent = new OreCrusherUiEvent();
            dropEvent.setAction(action);
            dropEvent.setSlotIndex(targetSlot);
            dropEvent.setTarget(data.getTarget());
            dropEvent.setSourceInventorySectionId(data.getSourceInventorySectionId());
            dropEvent.setSourceItemGridIndex(data.getSourceItemGridIndex());
            dropEvent.setSourceSlotId(data.getSourceSlotId());
            dropEvent.setItemStackId(data.getItemStackId());
            dropEvent.setItemStackQuantity(data.getItemStackQuantity());
            dropEvent.setDragItemStackId(data.getDragItemStackId());
            dropEvent.setDragItemStackQuantity(data.getDragItemStackQuantity());
            dropEvent.setDragSourceInventorySectionId(data.getDragSourceInventorySectionId());
            dropEvent.setDragSourceItemGridIndex(data.getDragSourceItemGridIndex());
            dropEvent.setDragSourceSlotId(data.getDragSourceSlotId());
            handleDrag(store, dropEvent);
            suppressNextDrag = true;
            lastDragSource = null;
            return true;
        }

        DragSnapshot cursor = getActiveClickCursor();
        if (cursor != null) {
            if (cursor.grid == targetGrid && cursor.slotId == targetSlot) {
                clickCursor = null;
                sendUpdate(new UICommandBuilder());
                return true;
            }

            OreCrusherUiEvent dropEvent = new OreCrusherUiEvent();
            String sourceSectionId = gridSectionId(cursor.grid);
            Integer sourceGridIndex = gridIndexFor(cursor.grid);
            Integer quantity = cursor.quantity > 0 ? cursor.quantity : null;

            dropEvent.setAction(action);
            dropEvent.setSlotIndex(targetSlot);
            dropEvent.setTarget(data.getTarget());
            dropEvent.setSourceInventorySectionId(sourceSectionId);
            dropEvent.setSourceItemGridIndex(sourceGridIndex);
            dropEvent.setSourceSlotId(cursor.slotId);
            dropEvent.setItemStackId(cursor.itemId);
            dropEvent.setItemStackQuantity(quantity);
            dropEvent.setDragItemStackId(cursor.itemId);
            dropEvent.setDragItemStackQuantity(quantity);
            dropEvent.setDragSourceInventorySectionId(sourceSectionId);
            dropEvent.setDragSourceItemGridIndex(sourceGridIndex);
            dropEvent.setDragSourceSlotId(cursor.slotId);

            handleDrag(store, dropEvent);
            suppressNextDrag = true;
            clickCursor = null;
            lastDragSource = null;
            return true;
        }

        DragSnapshot snapshot = getRecentDragSource();
        if (snapshot == null) {
            Inventory inventory = getPlayerInventoryFull(store);
            ItemContainer machineContainer = resolveMachineContainer(store);
            SlotRef sourceRef = resolveSlotRef(
                    targetGrid,
                    targetSlot,
                    inventory,
                    machineContainer,
                    PlayerIndexMode.COMBINED);
            ItemStack stack = slotRefStack(sourceRef);
            if (stack == null || ItemStack.isEmpty(stack)) {
                return false;
            }
            clickCursor = new DragSnapshot(
                    targetGrid,
                    targetSlot,
                    stack.getItemId(),
                    stack.getQuantity(),
                    System.currentTimeMillis());
            sendUpdate(new UICommandBuilder());
            return true;
        }
        if (snapshot.grid == targetGrid && snapshot.slotId == targetSlot) {
            return false;
        }

        OreCrusherUiEvent dropEvent = new OreCrusherUiEvent();
        String sourceSectionId = gridSectionId(snapshot.grid);
        Integer sourceGridIndex = gridIndexFor(snapshot.grid);
        Integer quantity = snapshot.quantity > 0 ? snapshot.quantity : null;

        dropEvent.setAction(action);
        dropEvent.setSlotIndex(targetSlot);
        dropEvent.setTarget(data.getTarget());
        dropEvent.setSourceInventorySectionId(sourceSectionId);
        dropEvent.setSourceItemGridIndex(sourceGridIndex);
        dropEvent.setSourceSlotId(snapshot.slotId);
        dropEvent.setItemStackId(snapshot.itemId);
        dropEvent.setItemStackQuantity(quantity);
        dropEvent.setDragItemStackId(snapshot.itemId);
        dropEvent.setDragItemStackQuantity(quantity);
        dropEvent.setDragSourceInventorySectionId(sourceSectionId);
        dropEvent.setDragSourceItemGridIndex(sourceGridIndex);
        dropEvent.setDragSourceSlotId(snapshot.slotId);

        handleDrag(store, dropEvent);
        lastDragSource = null;
        return true;
    }

    private String actionForTarget(GridType gridType) {
        if (gridType == GridType.INPUT) {
            return ACTION_INPUT_DROP;
        }
        if (gridType == GridType.OUTPUT) {
            return ACTION_OUTPUT_DROP;
        }
        if (gridType == GridType.PLAYER) {
            return ACTION_INVENTORY_DROP;
        }
        return null;
    }

    private String gridSectionId(GridType gridType) {
        if (gridType == GridType.INPUT) {
            return "#InputGrid";
        }
        if (gridType == GridType.OUTPUT) {
            return "#OutputGrid";
        }
        if (gridType == GridType.PLAYER) {
            return "#PlayerInventoryGrid";
        }
        return null;
    }

    private Integer gridIndexFor(GridType gridType) {
        if (gridType == GridType.INPUT) {
            return GRID_INDEX_INPUT;
        }
        if (gridType == GridType.OUTPUT) {
            return GRID_INDEX_OUTPUT;
        }
        if (gridType == GridType.PLAYER) {
            return GRID_INDEX_PLAYER;
        }
        return null;
    }


    private void handleDrag(Store<EntityStore> store, OreCrusherUiEvent data) {
        boolean updateSent = false;
        try {
            if (store == null || data == null) {
                return;
            }
            if (suppressNextDrag) {
                suppressNextDrag = false;
                return;
            }
            clickCursor = null;
            // Debug: surface drag payload in chat to trace slot/index issues.
            debugDragEvent(store, data);
            GridType targetGrid = resolveTargetGrid(data.getAction());
            if (targetGrid == null) {
                return;
            }
            World world = getWorld(store);
            if (world == null) {
                return;
            }
            Vector3i pos = resolveBlockPosition(world);
            ItemContainer machineContainer = pos == null
                    ? null
                    : MachineItemAccess.getContainer(world, pos.getX(), pos.getY(), pos.getZ());
            Inventory inventory = getPlayerInventoryFull(store);

            String sourceSectionId = firstNonEmpty(
                    data.getSourceInventorySectionId(),
                    data.getDragSourceInventorySectionId());
            Integer sourceGridIndex = sourceSectionId == null
                    ? null
                    : firstNonNull(data.getSourceItemGridIndex(), data.getDragSourceItemGridIndex());
            GridType sourceGrid = resolveSourceGrid(sourceSectionId, sourceGridIndex);
            if (sourceGrid == null) {
                sourceGrid = inferSourceGrid(data, targetGrid, inventory, machineContainer);
            }
            Integer sourceSlotId = firstNonNull(data.getSourceSlotId(), data.getDragSourceSlotId());
            DragSnapshot recentSource = getRecentDragSource();
            if (recentSource != null) {
                boolean useRecent = sourceGrid == null || sourceSlotId == null;
                if (!useRecent && sourceGrid == GridType.PLAYER) {
                    SlotRef ref = resolvePlayerSlot(inventory, sourceSlotId);
                    ItemStack stack = ref == null ? null : ref.container.getItemStack(ref.slot);
                    String eventItemId = resolveEventItemId(data);
                    boolean mismatch = stack == null
                            || ItemStack.isEmpty(stack)
                            || (eventItemId != null && !eventItemId.equals(stack.getItemId()));
                    if (mismatch) {
                        useRecent = true;
                    }
                }
                if (useRecent) {
                    sourceGrid = recentSource.grid;
                    sourceSlotId = recentSource.slotId;
                }
            }
            if (sourceGrid == null) {
                String itemId = resolveEventItemId(data);
                if (itemId != null
                        && findMatchingPlayerSlot(inventory, itemId, data.getItemStackQuantity()) != null) {
                    sourceGrid = GridType.PLAYER;
                } else {
                    return;
                }
            }
            String eventItemId = resolveEventItemId(data);
            if (targetGrid == GridType.INPUT
                    && sourceGrid == GridType.INPUT
                    && eventItemId != null
                    && eventItemId.equals(lastInputToInventoryItemId)
                    && (System.currentTimeMillis() - lastInputToInventoryMs) < INPUT_DROP_SUPPRESS_MS) {
                System.out.println("[HyProTech] DBG Drag suppress input drop after inventory move itemId=" + eventItemId);
                return;
            }
            if (targetGrid == GridType.PLAYER
                    && sourceGrid == GridType.INPUT
                    && eventItemId != null
                    && eventItemId.equals(lastInputDropItemId)
                    && (System.currentTimeMillis() - lastInputDropMs) < INPUT_DROP_SUPPRESS_MS) {
                System.out.println("[HyProTech] DBG Drag suppress inventory drop for itemId=" + eventItemId);
                return;
            }
            sourceGrid = reconcileSourceGrid(sourceGrid, data, inventory, machineContainer);
            if (sourceGrid == GridType.OUTPUT && sourceSlotId != null && sourceSlotId >= OUTPUT_SLOT_COUNT) {
                sourceGrid = GridType.PLAYER;
            }

            Integer targetSlotIndex = data.getSlotIndex();
            if (targetSlotIndex == null && targetGrid == GridType.INPUT) {
                targetSlotIndex = 0;
            }
            if (targetSlotIndex == null) {
                return;
            }
            if (sourceGrid == targetGrid && sourceSlotId != null && sourceSlotId.equals(targetSlotIndex)) {
                return;
            }

            if (targetGrid == GridType.INPUT && sourceGrid == GridType.OUTPUT) {
                ItemStack outputAtIndex = machineContainer == null
                        ? null
                        : getOutputStack(machineContainer, sourceSlotId);
                String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
                if (outputAtIndex == null
                        || ItemStack.isEmpty(outputAtIndex)
                        || (itemId != null && !itemId.equals(outputAtIndex.getItemId()))
                        || itemId == null) {
                    sourceGrid = GridType.PLAYER;
                }
            }

            if (!canMoveBetween(sourceGrid, targetGrid)) {
                return;
            }

            if (targetGrid == GridType.INPUT && sourceGrid != GridType.INPUT) {
                // Treat drops into input as player inventory only when the drag source
                // was not the input grid.
                sourceGrid = GridType.PLAYER;
                sourceSlotId = null;
            }
            if (targetGrid == GridType.PLAYER && sourceGrid == GridType.INPUT && eventItemId != null) {
                lastInputToInventoryMs = System.currentTimeMillis();
                lastInputToInventoryItemId = eventItemId;
            }
            System.out.println("[HyProTech] DBG Drag v2 state target=" + targetGrid
                    + " source=" + sourceGrid
                    + " sourceSlotId=" + sourceSlotId
                    + " itemId=" + eventItemId);
            PlayerIndexMode playerIndexMode =
                    sourceGrid == GridType.PLAYER
                            ? resolvePlayerIndexMode(inventory, sourceSlotId, eventItemId)
                            : PlayerIndexMode.COMBINED;
            SlotRef sourceRef = null;
            if (sourceGrid == GridType.PLAYER) {
                sourceRef = resolvePlayerSourceRef(inventory, sourceSlotId, data, playerIndexMode);
                if (sourceRef != null && sourceSlotId == null) {
                    sourceSlotId = resolvePlayerGridIndex(inventory, sourceRef);
                }
            } else if (sourceSlotId != null) {
                sourceRef = resolveSlotRef(sourceGrid, sourceSlotId, inventory, machineContainer, PlayerIndexMode.COMBINED);
            }
            SlotRef targetRef = resolveSlotRef(targetGrid, targetSlotIndex, inventory, machineContainer, playerIndexMode);
            if (sourceRef == null || targetRef == null) {
                if (targetGrid == GridType.INPUT) {
                    System.out.println("[HyProTech] DBG Drag input sourceRef/targetRef null"
                            + " sourceRef=" + (sourceRef == null ? "null" : "ok")
                            + " targetRef=" + (targetRef == null ? "null" : "ok")
                            + " itemId=" + eventItemId);
                }
                return;
            }

            ItemStack sourceStack = sourceRef.container.getItemStack(sourceRef.slot);
            if ((sourceStack == null || ItemStack.isEmpty(sourceStack)) && sourceGrid == GridType.OUTPUT) {
                SlotRef playerFallback = resolvePlayerSlot(inventory, sourceSlotId);
                if (playerFallback != null) {
                    ItemStack fallbackStack = playerFallback.container.getItemStack(playerFallback.slot);
                    if (fallbackStack != null && !ItemStack.isEmpty(fallbackStack)) {
                        sourceGrid = GridType.PLAYER;
                        sourceRef = playerFallback;
                        sourceStack = fallbackStack;
                    }
                }
            }
            if (sourceGrid == GridType.PLAYER) {
                boolean missingStack = sourceStack == null || ItemStack.isEmpty(sourceStack);
                boolean mismatch = !missingStack && eventItemId != null
                        && !eventItemId.equals(sourceStack.getItemId());
                if (missingStack || mismatch) {
                    Integer desiredQty = data.getItemStackQuantity();
                    SlotRef fallback = findMatchingPlayerSlot(inventory, eventItemId, desiredQty);
                    if (fallback == null && desiredQty != null) {
                        fallback = findMatchingPlayerSlot(inventory, eventItemId, null);
                    }
                    if (fallback != null) {
                        sourceRef = fallback;
                        sourceStack = fallback.container.getItemStack(fallback.slot);
                    }
                }
            }
            if (sourceStack == null || ItemStack.isEmpty(sourceStack)) {
                if (targetGrid == GridType.INPUT) {
                    System.out.println("[HyProTech] DBG Drag input sourceStack empty itemId=" + eventItemId);
                }
                return;
            }

            int quantity = sourceStack.getQuantity();
            Integer eventQty = data.getItemStackQuantity();
            if (eventQty != null && eventQty > 0 && eventQty < quantity) {
                quantity = eventQty;
            }

            if (sourceSlotId != null
                    && isDuplicateDrag(sourceGrid, sourceSlotId, targetGrid, targetSlotIndex, sourceStack, quantity)) {
                return;
            }

            MoveTransaction<?> move = sourceRef.container.moveItemStackFromSlotToSlot(
                    sourceRef.slot,
                    quantity,
                    targetRef.container,
                    targetRef.slot);
            if (move != null && move.succeeded()) {
                if (pos != null) {
                    MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
                }
                if (targetGrid == GridType.INPUT) {
                    ItemStack targetAfter = targetRef.container.getItemStack(targetRef.slot);
                    boolean ok = stackMatches(targetAfter, eventItemId);
                    System.out.println("[HyProTech] DBG Drag input move succeeded ok=" + ok
                            + " itemId=" + eventItemId);
                    lastInputDropMs = System.currentTimeMillis();
                    lastInputDropItemId = eventItemId;
                    if (!ok) {
                        System.out.println("[HyProTech] DBG Drag input move mismatch, trying manual add itemId="
                                + sourceStack.getItemId());
                        if (tryManualInputTransfer(sourceRef, targetRef, sourceStack, quantity)) {
                            updateSent = refreshSlots(store);
                            return;
                        }
                    }
                }
                updateSent = refreshSlots(store);
                return;
            }
            if (sourceGrid == GridType.INPUT && targetGrid == GridType.PLAYER) {
                System.out.println("[HyProTech] DBG Drag input->player move failed, trying manual transfer itemId="
                        + sourceStack.getItemId());
                if (tryManualTransfer(sourceRef, targetRef, sourceStack, quantity)) {
                    if (pos != null) {
                        MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
                    }
                    updateSent = refreshSlots(store);
                    return;
                }
            }
            if (targetGrid == GridType.INPUT && sourceGrid == GridType.PLAYER) {
                System.out.println("[HyProTech] DBG Drag input move failed, trying manual add itemId="
                        + sourceStack.getItemId());
                if (tryManualInputTransfer(sourceRef, targetRef, sourceStack, quantity)) {
                    if (pos != null) {
                        MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
                    }
                    lastInputDropMs = System.currentTimeMillis();
                    lastInputDropItemId = eventItemId;
                    updateSent = refreshSlots(store);
                    return;
                }
            }

            ItemStack targetStack = targetRef.container.getItemStack(targetRef.slot);
            if (targetStack != null && !ItemStack.isEmpty(targetStack)
                    && canSwapBetween(sourceGrid, targetGrid)) {
                ListTransaction<MoveTransaction<SlotTransaction>> swap =
                        sourceRef.container.swapItems(
                                sourceRef.slot,
                                targetRef.container,
                                targetRef.slot,
                                (short) 1);
                if (swap != null && swap.succeeded()) {
                    if (pos != null) {
                        MachineItemAccess.markContainerDirty(world, pos.getX(), pos.getY(), pos.getZ());
                    }
                    updateSent = refreshSlots(store);
                }
            }
        } finally {
            if (!updateSent) {
                sendUpdate(new UICommandBuilder());
            }
        }
    }

    private void debugDragEvent(Store<EntityStore> store, OreCrusherUiEvent data) {
        String msg = "DBG Drag action=" + data.getAction()
                + " target=" + data.getTarget()
                + " slotIndex=" + data.getSlotIndex()
                + " srcSlot=" + data.getSourceSlotId()
                + " srcGridIdx=" + data.getSourceItemGridIndex()
                + " srcSection=" + data.getSourceInventorySectionId()
                + " dragSrcSlot=" + data.getDragSourceSlotId()
                + " dragSrcGridIdx=" + data.getDragSourceItemGridIndex()
                + " dragSrcSection=" + data.getDragSourceInventorySectionId()
                + " itemId=" + data.getItemStackId()
                + " dragItemId=" + data.getDragItemStackId()
                + " qty=" + data.getItemStackQuantity();
        System.out.println("[HyProTech] v2 " + msg);
    }

    private void debugClickEvent(String label, OreCrusherUiEvent data) {
        if (data == null) {
            return;
        }
        String msg = "DBG Click " + label
                + " slotIndex=" + data.getSlotIndex()
                + " srcSlot=" + data.getSourceSlotId()
                + " srcGridIdx=" + data.getSourceItemGridIndex()
                + " srcSection=" + data.getSourceInventorySectionId()
                + " dragSrcSlot=" + data.getDragSourceSlotId()
                + " dragSrcGridIdx=" + data.getDragSourceItemGridIndex()
                + " dragSrcSection=" + data.getDragSourceInventorySectionId()
                + " itemId=" + data.getItemStackId()
                + " dragItemId=" + data.getDragItemStackId()
                + " qty=" + data.getItemStackQuantity()
                + " btn=" + data.getPressedMouseButton()
                + " target=" + data.getTarget();
        System.out.println("[HyProTech] v2 " + msg);
    }

    private boolean refreshSlots(Store<EntityStore> store) {
        UICommandBuilder update = new UICommandBuilder();
        if (updateSlots(update)) {
            sendUpdate(update);
            return true;
        }
        return false;
    }

    private boolean tryManualTransfer(
            SlotRef sourceRef,
            SlotRef targetRef,
            ItemStack sourceStack,
            int quantity) {
        if (sourceRef == null || targetRef == null || sourceStack == null || ItemStack.isEmpty(sourceStack)) {
            return false;
        }
        ItemStack moveStack = new ItemStack(
                sourceStack.getItemId(),
                quantity,
                sourceStack.getMetadata());
        if (!targetRef.container.canAddItemStackToSlot(targetRef.slot, moveStack, false, false)) {
            System.out.println("[HyProTech] DBG Drag manual transfer add rejected for itemId=" + moveStack.getItemId());
            return false;
        }
        ItemStackSlotTransaction addTx = targetRef.container.addItemStackToSlot(targetRef.slot, moveStack);
        if (addTx == null || !addTx.succeeded()) {
            System.out.println("[HyProTech] DBG Drag manual transfer add failed for itemId=" + moveStack.getItemId());
            return false;
        }
        ItemStackSlotTransaction removeTx = sourceRef.container.removeItemStackFromSlot(sourceRef.slot, quantity);
        if (removeTx == null || !removeTx.succeeded()) {
            System.out.println("[HyProTech] DBG Drag manual transfer remove failed for itemId=" + moveStack.getItemId());
            // rollback best-effort
            targetRef.container.removeItemStackFromSlot(targetRef.slot, moveStack, quantity, false, false);
            return false;
        }
        System.out.println("[HyProTech] DBG Drag manual transfer ok itemId=" + moveStack.getItemId());
        return true;
    }

    private boolean tryManualInputTransfer(
            SlotRef sourceRef,
            SlotRef targetRef,
            ItemStack sourceStack,
            int quantity) {
        if (sourceRef == null || targetRef == null || sourceStack == null || ItemStack.isEmpty(sourceStack)) {
            return false;
        }
        ItemStack moveStack = new ItemStack(
                sourceStack.getItemId(),
                quantity,
                sourceStack.getMetadata());
        if (!targetRef.container.canAddItemStackToSlot(targetRef.slot, moveStack, false, false)) {
            System.out.println("[HyProTech] DBG Drag input add rejected for itemId=" + moveStack.getItemId());
            return false;
        }
        ItemStackSlotTransaction addTx = targetRef.container.addItemStackToSlot(targetRef.slot, moveStack);
        if (addTx == null || !addTx.succeeded()) {
            System.out.println("[HyProTech] DBG Drag input add failed for itemId=" + moveStack.getItemId());
            return false;
        }
        ItemStackSlotTransaction removeTx = sourceRef.container.removeItemStackFromSlot(sourceRef.slot, quantity);
        if (removeTx == null || !removeTx.succeeded()) {
            System.out.println("[HyProTech] DBG Drag input remove failed for itemId=" + moveStack.getItemId());
            // rollback best-effort
            targetRef.container.removeItemStackFromSlot(targetRef.slot, moveStack, quantity, false, false);
            return false;
        }
        System.out.println("[HyProTech] DBG Drag input manual transfer ok itemId=" + moveStack.getItemId());
        return true;
    }

    private boolean isDuplicateDrag(
            GridType sourceGrid,
            int sourceSlot,
            GridType targetGrid,
            int targetSlot,
            ItemStack stack,
            int quantity) {
        String key = sourceGrid
                + ":"
                + sourceSlot
                + "->"
                + targetGrid
                + ":"
                + targetSlot
                + ":"
                + (stack == null ? "" : stack.getItemId())
                + ":"
                + quantity;
        long now = System.currentTimeMillis();
        if (key.equals(lastDragKey) && now - lastDragMs < DRAG_DEDUP_WINDOW_MS) {
            return true;
        }
        lastDragKey = key;
        lastDragMs = now;
        return false;
    }

    private boolean canMoveBetween(GridType source, GridType target) {
        if (target == GridType.OUTPUT && source != GridType.OUTPUT) {
            return false;
        }
        if (target == GridType.INPUT && source == GridType.OUTPUT) {
            return false;
        }
        return true;
    }

    private boolean canSwapBetween(GridType source, GridType target) {
        if (source == GridType.OUTPUT && target != GridType.OUTPUT) {
            return false;
        }
        if (target == GridType.OUTPUT && source != GridType.OUTPUT) {
            return false;
        }
        return true;
    }

    private GridType resolveTargetGrid(String action) {
        if (action == null) {
            return null;
        }
        if (ACTION_INPUT_DROP.equalsIgnoreCase(action)) {
            return GridType.INPUT;
        }
        if (ACTION_OUTPUT_DROP.equalsIgnoreCase(action)) {
            return GridType.OUTPUT;
        }
        if (ACTION_INVENTORY_DROP.equalsIgnoreCase(action)) {
            return GridType.PLAYER;
        }
        return null;
    }

    private GridType resolveSourceGrid(String sectionId, Integer gridIndex) {
        if (sectionId == null) {
            return null;
        }
        GridType fromIndex = gridIndex == null ? null : resolveGridIndex(gridIndex);
        if (fromIndex != null) {
            return fromIndex;
        }
        String normalized = sectionId.replace("#", "").trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("input")) {
            return GridType.INPUT;
        }
        if (normalized.contains("output")) {
            return GridType.OUTPUT;
        }
        if (normalized.contains("inventory") || normalized.contains("player") || normalized.contains("hotbar")) {
            return GridType.PLAYER;
        }
        return null;
    }

    private GridType reconcileSourceGrid(
            GridType sourceGrid,
            OreCrusherUiEvent data,
            Inventory inventory,
            ItemContainer machineContainer) {
        if (sourceGrid == null || data == null || machineContainer == null) {
            return sourceGrid;
        }
        if (sourceGrid == GridType.PLAYER) {
            return sourceGrid;
        }
        String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
        if (itemId == null) {
            return sourceGrid;
        }

        if (sourceGrid == GridType.OUTPUT) {
            Integer sourceSlotId = firstNonNull(data.getSourceSlotId(), data.getDragSourceSlotId());
            if (sourceSlotId != null && sourceSlotId >= 0 && sourceSlotId < OUTPUT_SLOT_COUNT) {
                ItemStack outputAtSlot = getOutputStack(machineContainer, sourceSlotId);
                if (!stackMatches(outputAtSlot, itemId)) {
                    return GridType.PLAYER;
                }
            } else if (!matchesAnyOutput(machineContainer, itemId)) {
                return GridType.PLAYER;
            }
        }

        ItemStack input = machineContainer.getCapacity() > INPUT_SLOT
                ? machineContainer.getItemStack(INPUT_SLOT)
                : null;
        boolean matchesInput = stackMatches(input, itemId);
        boolean matchesOutput = matchesAnyOutput(machineContainer, itemId);

        if (matchesOutput && !matchesInput) {
            return GridType.OUTPUT;
        }
        if (matchesInput && !matchesOutput) {
            return GridType.INPUT;
        }

        return sourceGrid;
    }

    private boolean matchesAnyOutput(ItemContainer container, String itemId) {
        if (container == null || itemId == null) {
            return false;
        }
        if (container.getCapacity() <= OUTPUT_SLOT_START) {
            return false;
        }
        for (short slot = OUTPUT_SLOT_START; slot <= OUTPUT_SLOT_END && slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stackMatches(stack, itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean stackMatches(ItemStack stack, String itemId) {
        if (stack == null || ItemStack.isEmpty(stack) || itemId == null) {
            return false;
        }
        return itemId.equals(stack.getItemId());
    }

    private GridType resolveGridFromTarget(String target) {
        if (target == null) {
            return null;
        }
        String normalized = target.replace("#", "").trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("inputgrid")) {
            return GridType.INPUT;
        }
        if (normalized.contains("outputgrid")) {
            return GridType.OUTPUT;
        }
        if (normalized.contains("playerinventorygrid") || normalized.contains("inventory")) {
            return GridType.PLAYER;
        }
        return null;
    }

    private void captureDragSource(OreCrusherUiEvent data) {
        if (data == null) {
            return;
        }
        GridType grid = resolveSourceGrid(data.getDragSourceInventorySectionId(), data.getDragSourceItemGridIndex());
        if (grid == null) {
            grid = resolveSourceGrid(data.getSourceInventorySectionId(), data.getSourceItemGridIndex());
        }
        if (grid == null) {
            grid = resolveGridFromTarget(data.getTarget());
        }

        Integer slotId = firstNonNull(
                data.getDragSourceSlotId(),
                data.getSourceSlotId(),
                data.getSlotIndex());
        if (grid == null || slotId == null) {
            return;
        }

        String itemId = firstNonEmpty(data.getDragItemStackId(), data.getItemStackId());
        Integer quantity = firstNonNull(data.getDragItemStackQuantity(), data.getItemStackQuantity());
        lastDragSource = new DragSnapshot(
                grid,
                slotId,
                itemId,
                quantity == null ? 0 : quantity,
                System.currentTimeMillis());
    }

    private void captureClickSource(GridType grid, OreCrusherUiEvent data) {
        if (data == null || grid == null) {
            return;
        }
        Integer slotId = data.getSlotIndex();
        if (slotId == null) {
            return;
        }
        String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
        Integer quantity = firstNonNull(data.getItemStackQuantity(), data.getDragItemStackQuantity());
        lastDragSource = new DragSnapshot(
                grid,
                slotId,
                itemId,
                quantity == null ? 0 : quantity,
                System.currentTimeMillis());
    }

    private DragSnapshot getRecentDragSource() {
        if (lastDragSource == null) {
            return null;
        }
        long age = System.currentTimeMillis() - lastDragSource.timestampMs;
        if (age > DRAG_SOURCE_WINDOW_MS) {
            return null;
        }
        return lastDragSource;
    }

    private DragSnapshot getActiveClickCursor() {
        if (clickCursor == null) {
            return null;
        }
        long age = System.currentTimeMillis() - clickCursor.timestampMs;
        if (age > CLICK_CURSOR_TIMEOUT_MS) {
            clickCursor = null;
            return null;
        }
        return clickCursor;
    }

    private boolean dragMatchesSnapshot(OreCrusherUiEvent data, DragSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
        if (itemId == null || snapshot.itemId == null) {
            return true;
        }
        return itemId.equals(snapshot.itemId);
    }

    private static Integer firstNonNull(Integer first, Integer second, Integer third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private static Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String resolveEventItemId(OreCrusherUiEvent data) {
        if (data == null) {
            return null;
        }
        String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
        if (itemId == null && lastDragSource != null) {
            itemId = lastDragSource.itemId;
        }
        return itemId;
    }

    private SlotRef resolvePlayerSourceRef(
            Inventory inventory,
            Integer sourceSlotId,
            OreCrusherUiEvent data,
            PlayerIndexMode playerIndexMode) {
        String itemId = resolveEventItemId(data);
        Integer desiredQty = data == null ? null : data.getItemStackQuantity();

        if (sourceSlotId != null) {
            SlotRef ref = resolvePlayerSlotByMode(inventory, sourceSlotId, playerIndexMode);
            if (ref != null) {
                ItemStack stack = ref.container.getItemStack(ref.slot);
                if (stack != null && !ItemStack.isEmpty(stack)
                        && (itemId == null || itemId.equals(stack.getItemId()))) {
                    return ref;
                }
            }
            if (itemId != null) {
                SlotRef storageRef = resolvePlayerSlotStorageOnly(inventory, sourceSlotId);
                if (storageRef != null) {
                    ItemStack stack = storageRef.container.getItemStack(storageRef.slot);
                    if (stack != null && !ItemStack.isEmpty(stack)
                            && itemId.equals(stack.getItemId())) {
                        return storageRef;
                    }
                }
                SlotRef hotbarRef = resolvePlayerSlotHotbarOnly(inventory, sourceSlotId);
                if (hotbarRef != null) {
                    ItemStack stack = hotbarRef.container.getItemStack(hotbarRef.slot);
                    if (stack != null && !ItemStack.isEmpty(stack)
                            && itemId.equals(stack.getItemId())) {
                        return hotbarRef;
                    }
                }
            }
        }

        if (lastDragSource != null && lastDragSource.grid == GridType.PLAYER) {
            SlotRef recent = resolvePlayerSlotByMode(inventory, lastDragSource.slotId, playerIndexMode);
            if (recent != null) {
                ItemStack stack = recent.container.getItemStack(recent.slot);
                if (stack != null && !ItemStack.isEmpty(stack)
                        && (itemId == null || itemId.equals(stack.getItemId()))) {
                    return recent;
                }
            }
        }

        if (itemId != null) {
            SlotRef match = findMatchingPlayerSlot(inventory, itemId, desiredQty);
            if (match == null && desiredQty != null) {
                match = findMatchingPlayerSlot(inventory, itemId, null);
            }
            return match;
        }

        return null;
    }

    private SlotRef findMatchingPlayerSlot(Inventory inventory, String itemId, Integer desiredQty) {
        if (inventory == null || itemId == null) {
            return null;
        }
        int qty = desiredQty == null || desiredQty <= 0 ? 1 : desiredQty;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar != null) {
            SlotRef ref = findMatchingSlotInContainer(hotbar, itemId, qty);
            if (ref != null) {
                return ref;
            }
        }
        ItemContainer storage = inventory.getStorage();
        if (storage != null) {
            return findMatchingSlotInContainer(storage, itemId, qty);
        }
        return null;
    }

    private SlotRef findMatchingSlotInContainer(ItemContainer container, String itemId, int minQty) {
        if (container == null || itemId == null) {
            return null;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            if (itemId.equals(stack.getItemId()) && stack.getQuantity() >= minQty) {
                return new SlotRef(container, slot);
            }
        }
        return null;
    }

    private GridType inferSourceGrid(
            OreCrusherUiEvent data,
            GridType targetGrid,
            Inventory inventory,
            ItemContainer machineContainer) {
        if (data == null) {
            return null;
        }
        Integer sourceSlotId = firstNonNull(data.getSourceSlotId(), data.getDragSourceSlotId());
        if (sourceSlotId == null) {
            return null;
        }

        if (targetGrid != GridType.PLAYER) {
            return GridType.PLAYER;
        }

        String itemId = firstNonEmpty(data.getItemStackId(), data.getDragItemStackId());
        if (itemId != null && inventory != null) {
            SlotRef playerSlot = resolvePlayerSlot(inventory, sourceSlotId);
            if (playerSlot != null) {
                ItemStack playerStack = playerSlot.container.getItemStack(playerSlot.slot);
                if (playerStack != null && !ItemStack.isEmpty(playerStack)
                        && itemId.equals(playerStack.getItemId())) {
                    return GridType.PLAYER;
                }
            }
        }

        if (itemId != null && machineContainer != null) {
            ItemStack input = machineContainer.getItemStack(INPUT_SLOT);
            if (input != null && !ItemStack.isEmpty(input) && itemId.equals(input.getItemId())) {
                return GridType.INPUT;
            }
            if (matchesAnyOutput(machineContainer, itemId)) {
                return GridType.OUTPUT;
            }
        }

        return GridType.PLAYER;
    }

    private GridType resolveGridIndex(int gridIndex) {
        if (gridIndex == GRID_INDEX_RECIPE) {
            return null;
        }
        if (gridIndex == GRID_INDEX_INPUT) {
            return GridType.INPUT;
        }
        if (gridIndex == GRID_INDEX_OUTPUT) {
            return GridType.OUTPUT;
        }
        if (gridIndex == GRID_INDEX_PLAYER) {
            return GridType.PLAYER;
        }
        return null;
    }

    private SlotRef resolveSlotRef(
            GridType gridType,
            int slotIndex,
            Inventory inventory,
            ItemContainer machineContainer,
            PlayerIndexMode playerIndexMode) {
        if (gridType == GridType.INPUT) {
            return machineContainer == null ? null : new SlotRef(machineContainer, INPUT_SLOT);
        }
        if (gridType == GridType.OUTPUT) {
            if (machineContainer == null) {
                return null;
            }
            if (slotIndex < 0 || slotIndex >= OUTPUT_SLOT_COUNT) {
                return null;
            }
            short outputSlot = (short) (OUTPUT_SLOT_START + slotIndex);
            if (outputSlot < OUTPUT_SLOT_START || outputSlot > OUTPUT_SLOT_END) {
                return null;
            }
            return new SlotRef(machineContainer, outputSlot);
        }
        if (gridType == GridType.PLAYER) {
            return resolvePlayerSlotByMode(inventory, slotIndex, playerIndexMode);
        }
        return null;
    }

    private SlotRef resolvePlayerSlot(Inventory inventory, int gridIndex) {
        if (gridIndex < 0) {
            return null;
        }
        if (inventory == null) {
            return null;
        }
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        int hotbarCapacity = hotbar == null ? 0 : hotbar.getCapacity();
        if (hotbar != null && gridIndex < hotbarCapacity) {
            return new SlotRef(hotbar, (short) gridIndex);
        }
        int storageIndex = gridIndex - hotbarCapacity;
        if (storage != null && storageIndex >= 0 && storageIndex < storage.getCapacity()) {
            return new SlotRef(storage, (short) storageIndex);
        }
        return null;
    }

    private SlotRef resolvePlayerSlotHotbarOnly(Inventory inventory, int gridIndex) {
        if (gridIndex < 0 || inventory == null) {
            return null;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar != null && gridIndex < hotbar.getCapacity()) {
            return new SlotRef(hotbar, (short) gridIndex);
        }
        return null;
    }

    private SlotRef resolvePlayerSlotStorageOnly(Inventory inventory, int gridIndex) {
        if (gridIndex < 0 || inventory == null) {
            return null;
        }
        ItemContainer storage = inventory.getStorage();
        if (storage != null && gridIndex < storage.getCapacity()) {
            return new SlotRef(storage, (short) gridIndex);
        }
        return null;
    }

    private SlotRef resolvePlayerSlotByMode(
            Inventory inventory,
            int gridIndex,
            PlayerIndexMode mode) {
        if (mode == PlayerIndexMode.STORAGE_ONLY) {
            return resolvePlayerSlotStorageOnly(inventory, gridIndex);
        }
        if (mode == PlayerIndexMode.HOTBAR_ONLY) {
            return resolvePlayerSlotHotbarOnly(inventory, gridIndex);
        }
        SlotRef ref = resolvePlayerSlot(inventory, gridIndex);
        if (ref == null) {
            ref = resolvePlayerSlotStorageOnly(inventory, gridIndex);
        }
        if (ref == null) {
            ref = resolvePlayerSlotHotbarOnly(inventory, gridIndex);
        }
        return ref;
    }

    private PlayerIndexMode resolvePlayerIndexMode(
            Inventory inventory,
            Integer slotIndex,
            String itemId) {
        if (inventory == null || slotIndex == null || itemId == null) {
            return PlayerIndexMode.COMBINED;
        }
        SlotRef combined = resolvePlayerSlot(inventory, slotIndex);
        if (stackMatches(slotRefStack(combined), itemId)) {
            return PlayerIndexMode.COMBINED;
        }
        SlotRef storage = resolvePlayerSlotStorageOnly(inventory, slotIndex);
        if (stackMatches(slotRefStack(storage), itemId)) {
            return PlayerIndexMode.STORAGE_ONLY;
        }
        SlotRef hotbar = resolvePlayerSlotHotbarOnly(inventory, slotIndex);
        if (stackMatches(slotRefStack(hotbar), itemId)) {
            return PlayerIndexMode.HOTBAR_ONLY;
        }
        return PlayerIndexMode.COMBINED;
    }

    private ItemStack slotRefStack(SlotRef ref) {
        if (ref == null || ref.container == null) {
            return null;
        }
        return ref.container.getItemStack(ref.slot);
    }

    private Integer resolvePlayerGridIndex(Inventory inventory, SlotRef slotRef) {
        if (inventory == null || slotRef == null || slotRef.container == null) {
            return null;
        }
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        if (hotbar != null && slotRef.container == hotbar) {
            return (int) slotRef.slot;
        }
        int hotbarCapacity = hotbar == null ? 0 : hotbar.getCapacity();
        if (storage != null && slotRef.container == storage) {
            return hotbarCapacity + slotRef.slot;
        }
        return null;
    }

    private void bindButtons(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#UpgradeButton",
                EventData.of("Action", ACTION_UPGRADE));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CrusherToggleButton",
                EventData.of("Action", ACTION_TOGGLE));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TakeInputButton",
                EventData.of("Action", ACTION_TAKE_INPUT));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TakeOutputButton",
                EventData.of("Action", ACTION_TAKE_OUTPUT));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipeConfirmButton",
                EventData.of("Action", ACTION_RECIPE_LOAD));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipeQtyMinus",
                EventData.of("Action", ACTION_RECIPE_QTY_MINUS));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipeQtyPlus",
                EventData.of("Action", ACTION_RECIPE_QTY_PLUS));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipeQtyPlusTen",
                EventData.of("Action", ACTION_RECIPE_QTY_PLUS_TEN));
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RecipeQtyAll",
                EventData.of("Action", ACTION_RECIPE_QTY_ALL));
    }

    private enum GridType {
        INPUT,
        OUTPUT,
        PLAYER
    }

    private enum PlayerIndexMode {
        COMBINED,
        STORAGE_ONLY,
        HOTBAR_ONLY
    }

    private static final class DragSnapshot {
        private final GridType grid;
        private final int slotId;
        private final String itemId;
        private final int quantity;
        private final long timestampMs;

        private DragSnapshot(GridType grid, int slotId, String itemId, int quantity, long timestampMs) {
            this.grid = grid;
            this.slotId = slotId;
            this.itemId = itemId;
            this.quantity = quantity;
            this.timestampMs = timestampMs;
        }
    }

    private static final class SlotRef {
        private final ItemContainer container;
        private final short slot;

        private SlotRef(ItemContainer container, short slot) {
            this.container = container;
            this.slot = slot;
        }
    }
}
