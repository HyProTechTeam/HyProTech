package HyProTechTeam.ui;

import HyProTechTeam.HyProTechComponents;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.item.FilterMode;
import HyProTechTeam.item.ItemMode;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.item.ItemStorageConfigChunk;
import HyProTechTeam.item.ItemStorageConfigComponent;
import HyProTechTeam.item.ItemTarget;
import HyProTechTeam.machine.MachineItemAccess;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCablePage extends InteractiveCustomUIPage<SideToggleEvent> {
    private static final String PAGE_LAYOUT = "HyProTech_Item_Cable.ui";
    private static final String NO_LINK_LABEL = "No link";
    private static final int FILTER_ROW_COUNT = 12;
    private static final int PRESET_SLOT_COUNT = 5;
    private static final String CATEGORY_ALL = "All";
    private static final String CATEGORY_ORES = "Ores";
    private static final String CATEGORY_INGOTS = "Ingots";
    private static final String CATEGORY_PLATES = "Plates";
    private static final String CATEGORY_COMPONENTS = "Components";
    private static final String CATEGORY_MACHINES = "Machines";
    private static final String CATEGORY_CABLES = "Cables";
    private static final String CATEGORY_FLUIDS = "Fluids";
    private static final String CATEGORY_NUCLEAR = "Nuclear";
    private static final String CATEGORY_OTHER = "Other";
    private static final String FILTER_HINT_WHITELIST = "Select items to allow";
    private static final String FILTER_HINT_BLACKLIST = "Select items to block";
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            CATEGORY_ALL,
            CATEGORY_ORES,
            CATEGORY_INGOTS,
            CATEGORY_PLATES,
            CATEGORY_COMPONENTS,
            CATEGORY_MACHINES,
            CATEGORY_CABLES,
            CATEGORY_FLUIDS,
            CATEGORY_NUCLEAR,
            CATEGORY_OTHER);
    private static final Map<UUID, String> FILTER_CLIPBOARD = new ConcurrentHashMap<>();

    private final Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, ItemNodeComponent> itemType;
    private Vector3i blockPosition;
    private ItemMode lastMode;
    private ItemTarget lastTarget;
    private int lastInputMask = Integer.MIN_VALUE;
    private int lastOutputMask = Integer.MIN_VALUE;
    private String lastNeighborKey = "";
    private String lastFilterKey = "";
    private final List<String> filterItems = new ArrayList<>();
    private final List<String> allItemIds = new ArrayList<>();
    private final List<String> filteredItemIds = new ArrayList<>();
    private final Map<String, String> itemDisplayNames = new HashMap<>();
    private final Map<String, String> itemIdByDisplayName = new HashMap<>();
    private final Map<String, String> itemCategoryById = new HashMap<>();
    private final List<String> presetNames = new ArrayList<>();
    private EnergySide activeFilterSide;
    private boolean filterPanelVisible;
    private int filterPageIndex;
    private String filterQuery = "";
    private String activeCategory = CATEGORY_ALL;
    private Vector3i renameTargetPos;

    public ItemCablePage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, ItemNodeComponent> itemType) {
        super(playerRef, CustomPageLifetime.CanDismiss, SideToggleEvent.CODEC);
        this.blockRef = blockRef;
        this.itemType = itemType;
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
        bindButtons(uiEventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, SideToggleEvent data) {
        if (data == null) {
            return;
        }

        World world = getWorld(store);
        if (world == null) {
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        ItemNodeComponent node = getOrCreateItemNode(chunkStore);
        String searchQuery = data.getSearchQuery();
        if (searchQuery != null) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            filterPageIndex = 0;
            ensureAllItemsLoaded();
            applySearchQuery(searchQuery);
            updateFilterUi(node);
            return;
        }

        String action = data.getAction();
        if (action == null) {
            return;
        }

        if ("ItemTarget".equalsIgnoreCase(action)) {
            node.setTarget(node.getTarget().next());
        } else if ("OpenNetwork".equalsIgnoreCase(action)) {
            openNetworkPage(playerRef, store);
            return;
        } else if ("ToggleSide".equalsIgnoreCase(action)) {
            EnergySide side = EnergySide.fromName(data.getSide());
            if (side == null) {
                return;
            }
            node.cycleSideMode(side);
        } else if ("RenameStorage".equalsIgnoreCase(action)) {
            EnergySide side = EnergySide.fromName(data.getSide());
            Vector3i pos = resolveBlockPosition(world);
            Vector3i storagePos = resolveStoragePosition(world, pos, side);
            if (storagePos == null) {
                return;
            }
            renameTargetPos = storagePos;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", true);
            update.set("#RenameInput.Value", getStorageConfiguredName(world, storagePos));
            sendUpdate(update);
            return;
        } else if ("RenameStorageSave".equalsIgnoreCase(action)) {
            if (renameTargetPos != null) {
                updateStorageName(world, renameTargetPos, data.getValue());
            }
            renameTargetPos = null;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", false);
            update.set("#RenameInput.Value", "");
            sendUpdate(update);
            update(node);
            return;
        } else if ("RenameStorageCancel".equalsIgnoreCase(action)) {
            renameTargetPos = null;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", false);
            update.set("#RenameInput.Value", "");
            sendUpdate(update);
            return;
        } else if ("OpenFilter".equalsIgnoreCase(action)) {
            EnergySide side = EnergySide.fromName(data.getSide());
            if (side == null) {
                return;
            }
            ItemMode sideMode = node.getSideMode(side);
            if (sideMode == null || (!sideMode.allowsPut() && !sideMode.allowsTake())) {
                return;
            }
            openFilterPanel(playerRef, store, node, side);
            return;
        } else if ("FilterCategory".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            String category = data.getValue();
            if (category == null || category.isEmpty()) {
                category = data.getIndex();
            }
            if (category == null || category.isEmpty()) {
                return;
            }
            activeCategory = category;
            filterPageIndex = 0;
            applySearchQuery(filterQuery);
            updateFilterUi(node);
            return;
        } else if ("CloseFilter".equalsIgnoreCase(action)) {
            closeFilterPanel();
            return;
        } else if ("PresetOres".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            applyPresetItems(chunkStore, node, buildOrePreset());
            return;
        } else if ("PresetSave".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            String name = data.getValue();
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            Set<String> items = node.getFilters(activeFilterSide);
            if (items.isEmpty()) {
                return;
            }
            node.saveFilterPreset(name.trim(), items);
            chunkStore.putComponent(blockRef, itemType, node);
            UICommandBuilder clear = new UICommandBuilder();
            clear.set("#FilterPresetNameInput.Value", "");
            sendUpdate(clear);
            updateFilterUi(node);
            return;
        } else if ("PresetLoad".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            int index = parseIndex(data.getIndex());
            String presetName = resolvePresetName(node, index);
            if (presetName == null) {
                return;
            }
            applyPresetItems(chunkStore, node, node.getFilterPreset(presetName));
            return;
        } else if ("PresetCopy".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            sendPresetCopy(node, store);
            return;
        } else if ("PresetPaste".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            String text = data.getValue();
            if (text == null || text.trim().isEmpty()) {
                text = getClipboardText(store);
            }
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            Set<String> items = parsePresetText(text);
            applyPresetItems(chunkStore, node, items);
            return;
        } else if ("PresetClear".equalsIgnoreCase(action)) {
            if (!filterPanelVisible || activeFilterSide == null) {
                return;
            }
            node.clearFilters(activeFilterSide);
            chunkStore.putComponent(blockRef, itemType, node);
            updateFilterUi(node);
            return;
        } else if ("ToggleFilterMode".equalsIgnoreCase(action)) {
            if (activeFilterSide == null) {
                return;
            }
            node.toggleFilterMode(activeFilterSide);
            chunkStore.putComponent(blockRef, itemType, node);
            updateFilterUi(node);
            return;
        } else if ("ToggleFilter".equalsIgnoreCase(action)) {
            if (activeFilterSide == null) {
                return;
            }
            int index = parseIndex(data.getIndex());
            if (index < 0 || index >= filterItems.size()) {
                return;
            }
            String itemId = filterItems.get(index);
            if (itemId == null || itemId.isEmpty()) {
                return;
            }
            node.toggleFilterItem(activeFilterSide, itemId);
            chunkStore.putComponent(blockRef, itemType, node);
            updateFilterUi(node);
            return;
        } else if ("FilterPrev".equalsIgnoreCase(action)) {
            if (filterPageIndex > 0) {
                filterPageIndex--;
                updateFilterUi(node);
            }
            return;
        } else if ("FilterNext".equalsIgnoreCase(action)) {
            int total = filteredItemIds.size();
            int maxPage = total == 0 ? 0 : (total - 1) / FILTER_ROW_COUNT;
            if (filterPageIndex < maxPage) {
                filterPageIndex++;
                updateFilterUi(node);
            }
            return;
        } else {
            return;
        }

        chunkStore.putComponent(blockRef, itemType, node);
        update(node);
    }

    public void update(ItemNodeComponent node) {
        if (node == null) {
            return;
        }

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = false;

        ItemMode mode = node.getMode();
        ItemTarget target = node.getTarget();
        int inputMask = node.getInputMask();
        int outputMask = node.getOutputMask();

        if (mode != lastMode) {
            update.set("#ItemMode.Text", "Item Mode: " + mode.label());
            lastMode = mode;
            changed = true;
        }
        if (target != lastTarget) {
            update.set("#ItemTarget.Text", "Target: " + target.label());
            lastTarget = target;
            changed = true;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null) {
            Store<EntityStore> store = playerEntityRef.getStore();
            World world = getWorld(store);
            Vector3i pos = resolveBlockPosition(world);
            String neighborKey = buildNeighborKey(world, pos);
            String filterKey = buildFilterKey(node);
            if (!neighborKey.equals(lastNeighborKey)
                    || inputMask != lastInputMask
                    || outputMask != lastOutputMask
                    || !filterKey.equals(lastFilterKey)) {
                updateSideButtons(update, node, world, pos);
                lastNeighborKey = neighborKey;
                lastInputMask = inputMask;
                lastOutputMask = outputMask;
                lastFilterKey = filterKey;
                changed = true;
            }
        }

        if (changed) {
            sendUpdate(update);
        }
    }

    private void bindButtons(UIEventBuilder uiEventBuilder) {
        EventData targetData = EventData.of("Action", "ItemTarget").append("Side", "All");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemTarget", targetData);
        EventData networkData = EventData.of("Action", "OpenNetwork").append("Side", "All");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemNetwork", networkData);
        EventData renameSave = EventData.of("Action", "RenameStorageSave").append("@Value", "#RenameInput.Value");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RenameSave", renameSave);
        EventData renameCancel = EventData.of("Action", "RenameStorageCancel");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RenameCancel", renameCancel);
        bindSideButtons(uiEventBuilder);
        bindFilterButtons(uiEventBuilder);
    }

    private void bindSideButtons(UIEventBuilder uiEventBuilder) {
        for (EnergySide side : EnergySide.VALUES) {
            EventData data = EventData.of("Action", "ToggleSide").append("Side", side.name());
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sideId(side), data);
            EventData renameData = EventData.of("Action", "RenameStorage").append("Side", side.name());
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sideRenameId(side), renameData);
        }
    }

    private void bindFilterButtons(UIEventBuilder uiEventBuilder) {
        for (EnergySide side : EnergySide.VALUES) {
            EventData data = EventData.of("Action", "OpenFilter").append("Side", side.name());
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sideFilterId(side), data);
        }

        bindCategoryButton(uiEventBuilder, "#FilterCategoryAll", CATEGORY_ALL);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryOres", CATEGORY_ORES);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryIngots", CATEGORY_INGOTS);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryPlates", CATEGORY_PLATES);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryComponents", CATEGORY_COMPONENTS);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryMachines", CATEGORY_MACHINES);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryCables", CATEGORY_CABLES);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryFluids", CATEGORY_FLUIDS);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryNuclear", CATEGORY_NUCLEAR);
        bindCategoryButton(uiEventBuilder, "#FilterCategoryOther", CATEGORY_OTHER);

        EventData presetOres = EventData.of("Action", "PresetOres");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPresetOres", presetOres);

        EventData presetSave = EventData.of("Action", "PresetSave")
                .append("@Value", "#FilterPresetNameInput.Value");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPresetSave", presetSave);

        EventData searchData = EventData.of("@SearchQuery", "#FilterSearchInput.Value");
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#FilterSearchInput",
                searchData,
                false);
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#FilterSearchButton",
                searchData,
                false);

        EventData modeData = EventData.of("Action", "ToggleFilterMode").append("Side", "Active");
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#FilterModeCheck",
                modeData);

        EventData closeData = EventData.of("Action", "CloseFilter").append("Side", "All");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterClose", closeData);

        EventData prevData = EventData.of("Action", "FilterPrev").append("Side", "All");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPrev", prevData);

        EventData nextData = EventData.of("Action", "FilterNext").append("Side", "All");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterNext", nextData);

        EventData presetCopy = EventData.of("Action", "PresetCopy");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPresetCopy", presetCopy);

        EventData presetPaste = EventData.of("Action", "PresetPaste")
                .append("@Value", "#FilterPresetText.Value");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPresetPaste", presetPaste);

        EventData presetClear = EventData.of("Action", "PresetClear");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPresetClear", presetClear);

        for (int i = 0; i < PRESET_SLOT_COUNT; i++) {
            EventData data = EventData.of("Action", "PresetLoad")
                    .append("Index", Integer.toString(i));
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    presetSlotId(i),
                    data);
        }

        for (int i = 0; i < FILTER_ROW_COUNT; i++) {
            EventData data = EventData.of("Action", "ToggleFilter")
                    .append("Side", "Active")
                    .append("Index", Integer.toString(i));
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    filterCheckId(i),
                    data);
        }
    }

    private void bindCategoryButton(UIEventBuilder uiEventBuilder, String selector, String category) {
        EventData data = EventData.of("Action", "FilterCategory")
                .append("Index", category);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, data);
    }

    private ItemNodeComponent getOrCreateItemNode(Store<ChunkStore> chunkStore) {
        ItemNodeComponent node = chunkStore.getComponent(blockRef, itemType);
        if (node == null) {
            node = new ItemNodeComponent();
        }
        return node;
    }

    private String buildNeighborKey(World world, Vector3i blockPos) {
        if (world == null || blockPos == null) {
            return "";
        }

        StringBuilder key = new StringBuilder();
        for (EnergySide side : EnergySide.VALUES) {
            key.append(side.name())
                    .append('=')
                    .append(getNeighborItemId(world, blockPos, side))
                    .append('|');
        }

        return key.toString();
    }

    private String buildFilterKey(ItemNodeComponent node) {
        if (node == null) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        for (EnergySide side : EnergySide.VALUES) {
            key.append(side.name())
                    .append('=')
                    .append(node.getFilterCount(side))
                    .append('|');
        }
        return key.toString();
    }

    private void updateSideButtons(UICommandBuilder update, ItemNodeComponent node, World world, Vector3i blockPos) {
        for (EnergySide side : EnergySide.VALUES) {
            Vector3i storagePos = resolveStoragePosition(world, blockPos, side);
            String itemId = storagePos == null
                    ? getNeighborItemId(world, blockPos, side)
                    : getStorageItemId(world, storagePos);
            String linkLabel = NO_LINK_LABEL;
            if (storagePos != null) {
                String configuredName = getStorageConfiguredName(world, storagePos);
                if (configuredName != null && !configuredName.isEmpty()) {
                    linkLabel = configuredName;
                } else if (!UiItemIds.isEmptyItemId(itemId)) {
                    linkLabel = getDisplayName(itemId);
                }
            } else if (!UiItemIds.isEmptyItemId(itemId)) {
                linkLabel = getDisplayName(itemId);
            }
            String modeLabel = node.getSideMode(side).label();
            String text = side.label() + ": " + modeLabel + " (" + linkLabel + ")";
            update.set(sideIconId(side) + ".ItemId", UiItemIds.safeItemId(itemId));
            update.set(sideId(side) + ".Text", text);
            update.set(sideRenameId(side) + ".Visible", storagePos != null);
            updateFilterButton(update, node, side);
        }
    }

    private void updateFilterButton(UICommandBuilder update, ItemNodeComponent node, EnergySide side) {
        ItemMode sideMode = node.getSideMode(side);
        boolean allowsFilter = sideMode != null && (sideMode.allowsPut() || sideMode.allowsTake());
        update.set(sideFilterId(side) + ".Visible", allowsFilter);
    }

    private void openFilterPanel(
            Ref<EntityStore> playerRef,
            Store<EntityStore> store,
            ItemNodeComponent node,
            EnergySide side) {
        activeFilterSide = side;
        filterPanelVisible = true;
        filterQuery = "";
        filterPageIndex = 0;
        ensureAllItemsLoaded();
        applySearchQuery(filterQuery);
        rebuildFilterItems();

        UICommandBuilder update = new UICommandBuilder();
        update.set("#FilterPanel.Visible", true);
        update.set("#FilterTitle.Text", "Filters: " + side.label());
        update.set("#FilterSearchInput.Value", filterQuery);
        update.set("#FilterPresetNameInput.Value", "");
        update.set("#FilterPresetText.Value", getClipboardText(store));
        updateFilterModeUi(update, node);
        updateCategoryUi(update);
        updatePresetUi(update, node);
        updateFilterRows(update, node);
        sendUpdate(update);
    }

    private void closeFilterPanel() {
        if (!filterPanelVisible) {
            return;
        }
        filterPanelVisible = false;
        activeFilterSide = null;
        filterItems.clear();
        filterPageIndex = 0;
        filterQuery = "";

        UICommandBuilder update = new UICommandBuilder();
        update.set("#FilterPanel.Visible", false);
        update.set("#FilterSearchInput.Value", "");
        sendUpdate(update);
    }

    private void updateFilterUi(ItemNodeComponent node) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        World world = getWorld(store);
        Vector3i pos = resolveBlockPosition(world);
        if (world == null || pos == null) {
            return;
        }

        UICommandBuilder update = new UICommandBuilder();
        updateSideButtons(update, node, world, pos);
        if (filterPanelVisible) {
            updateFilterModeUi(update, node);
            updateCategoryUi(update);
            updatePresetUi(update, node);
            updateFilterRows(update, node);
        }
        sendUpdate(update);
    }

    private void updateFilterModeUi(UICommandBuilder update, ItemNodeComponent node) {
        if (activeFilterSide == null || update == null || node == null) {
            return;
        }
        FilterMode mode = node.getFilterMode(activeFilterSide);
        update.set("#FilterModeCheck.Value", mode == FilterMode.BLACKLIST);
        update.set("#FilterModeValue.Text", mode.label());
        update.set("#FilterHint.Text",
                mode == FilterMode.BLACKLIST ? FILTER_HINT_BLACKLIST : FILTER_HINT_WHITELIST);
    }

    private void updateFilterRows(UICommandBuilder update, ItemNodeComponent node) {
        rebuildFilterItems();
        Set<String> activeFilters = activeFilterSide == null
                ? Collections.emptySet()
                : node.getFilters(activeFilterSide);

        int size = filterItems.size();
        update.set("#FilterEmpty.Visible", size == 0);
        updateFilterPaging(update);
        for (int i = 0; i < FILTER_ROW_COUNT; i++) {
            boolean visible = i < size;
            update.set(filterRowId(i) + ".Visible", visible);
            if (!visible) {
                continue;
            }
            String itemId = filterItems.get(i);
            update.set(filterIconId(i) + ".ItemId", UiItemIds.safeItemId(itemId));
            update.set(filterLabelId(i) + ".Text", getDisplayName(itemId));
            update.set(filterCheckId(i) + ".Value", activeFilters.contains(itemId));
        }
    }

    private void updateFilterPaging(UICommandBuilder update) {
        int total = filteredItemIds.size();
        if (total <= 0) {
            update.set("#FilterPage.Text", "0 / 0");
            update.set("#FilterPrev.Visible", false);
            update.set("#FilterNext.Visible", false);
            return;
        }

        int start = filterPageIndex * FILTER_ROW_COUNT + 1;
        int end = Math.min(total, start + FILTER_ROW_COUNT - 1);
        update.set("#FilterPage.Text", start + "-" + end + " / " + total);
        update.set("#FilterPrev.Visible", filterPageIndex > 0);
        update.set("#FilterNext.Visible", end < total);
    }

    private void ensureAllItemsLoaded() {
        if (!allItemIds.isEmpty()) {
            return;
        }
        Map<?, ?> assetMap = Item.getAssetMap().getAssetMap();
        for (Object key : assetMap.keySet()) {
            if (key == null) {
                continue;
            }
            String itemId = key.toString();
            if (itemId.isEmpty()) {
                continue;
            }
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null || item == Item.UNKNOWN || item.isState()) {
                continue;
            }
            String displayName = resolveDisplayName(itemId, item);
            itemDisplayNames.put(itemId, displayName);
            String normalizedName = normalizeDisplayName(displayName);
            if (!normalizedName.isEmpty()) {
                itemIdByDisplayName.putIfAbsent(normalizedName, itemId);
            }
            String category = categorizeItem(itemId);
            itemCategoryById.put(itemId, category);
            allItemIds.add(itemId);
        }
        allItemIds.sort(Comparator.comparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER));
    }

    private void applySearchQuery(String query) {
        filterQuery = query == null ? "" : query.trim().toLowerCase();
        applyFilters();
    }

    private void applyFilters() {
        filteredItemIds.clear();
        if (allItemIds.isEmpty()) {
            return;
        }
        if (!CATEGORY_ORDER.contains(activeCategory)) {
            activeCategory = CATEGORY_ALL;
        }
        String query = filterQuery == null ? "" : filterQuery.trim().toLowerCase();
        for (String itemId : allItemIds) {
            if (!CATEGORY_ALL.equals(activeCategory)) {
                String category = itemCategoryById.get(itemId);
                if (!activeCategory.equals(category)) {
                    continue;
                }
            }
            if (!query.isEmpty()) {
                String display = getDisplayName(itemId).toLowerCase();
                if (!itemId.toLowerCase().contains(query) && !display.contains(query)) {
                    continue;
                }
            }
            filteredItemIds.add(itemId);
        }
    }

    private void updateCategoryUi(UICommandBuilder update) {
        if (update == null) {
            return;
        }
        update.set("#FilterCategoryValue.Text", activeCategory);
        updateCategoryButton(update, "#FilterCategoryAll", CATEGORY_ALL);
        updateCategoryButton(update, "#FilterCategoryOres", CATEGORY_ORES);
        updateCategoryButton(update, "#FilterCategoryIngots", CATEGORY_INGOTS);
        updateCategoryButton(update, "#FilterCategoryPlates", CATEGORY_PLATES);
        updateCategoryButton(update, "#FilterCategoryComponents", CATEGORY_COMPONENTS);
        updateCategoryButton(update, "#FilterCategoryMachines", CATEGORY_MACHINES);
        updateCategoryButton(update, "#FilterCategoryCables", CATEGORY_CABLES);
        updateCategoryButton(update, "#FilterCategoryFluids", CATEGORY_FLUIDS);
        updateCategoryButton(update, "#FilterCategoryNuclear", CATEGORY_NUCLEAR);
        updateCategoryButton(update, "#FilterCategoryOther", CATEGORY_OTHER);
    }

    private void updateCategoryButton(UICommandBuilder update, String selector, String category) {
        String label = category;
        if (category.equalsIgnoreCase(activeCategory)) {
            label = "> " + category;
        }
        update.set(selector + ".Text", label);
    }

    private void updatePresetUi(UICommandBuilder update, ItemNodeComponent node) {
        if (update == null || node == null) {
            return;
        }
        refreshPresetNames(node);
        for (int i = 0; i < PRESET_SLOT_COUNT; i++) {
            boolean hasPreset = i < presetNames.size();
            String label = hasPreset ? presetNames.get(i) : "Empty";
            update.set(presetSlotId(i) + ".Text", label);
            update.set(presetSlotId(i) + ".Visible", true);
        }
    }

    private void refreshPresetNames(ItemNodeComponent node) {
        presetNames.clear();
        if (node == null) {
            return;
        }
        presetNames.addAll(node.getFilterPresetNames());
    }

    private String resolvePresetName(ItemNodeComponent node, int index) {
        refreshPresetNames(node);
        if (index < 0 || index >= presetNames.size()) {
            return null;
        }
        return presetNames.get(index);
    }

    private void applyPresetItems(Store<ChunkStore> chunkStore, ItemNodeComponent node, Set<String> items) {
        if (chunkStore == null || node == null || activeFilterSide == null) {
            return;
        }
        filterPageIndex = 0;
        if (items == null || items.isEmpty()) {
            node.clearFilters(activeFilterSide);
        } else {
            node.setFilters(activeFilterSide, items);
        }
        chunkStore.putComponent(blockRef, itemType, node);
        updateFilterUi(node);
    }

    private Set<String> buildOrePreset() {
        ensureAllItemsLoaded();
        Set<String> ores = new LinkedHashSet<>();
        for (String itemId : allItemIds) {
            if (CATEGORY_ORES.equals(itemCategoryById.get(itemId))) {
                ores.add(itemId);
            }
        }
        return ores;
    }

    private void sendPresetCopy(ItemNodeComponent node, Store<EntityStore> store) {
        if (node == null || activeFilterSide == null) {
            return;
        }
        Set<String> items = node.getFilters(activeFilterSide);
        String text = serializePresetText(items);
        setClipboardText(store, text);
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FilterPresetText.Value", text);
        sendUpdate(update);
    }

    private String getClipboardText(Store<EntityStore> store) {
        UUID playerId = resolvePlayerId(store);
        if (playerId == null) {
            return "";
        }
        return FILTER_CLIPBOARD.getOrDefault(playerId, "");
    }

    private void setClipboardText(Store<EntityStore> store, String text) {
        UUID playerId = resolvePlayerId(store);
        if (playerId == null) {
            return;
        }
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            FILTER_CLIPBOARD.remove(playerId);
        } else {
            FILTER_CLIPBOARD.put(playerId, normalized);
        }
    }

    private UUID resolvePlayerId(Store<EntityStore> store) {
        if (store == null) {
            return null;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return null;
        }
        UUIDComponent uuidComponent = store.getComponent(playerEntityRef, UUIDComponent.getComponentType());
        return uuidComponent == null ? null : uuidComponent.getUuid();
    }

    private String serializePresetText(Set<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (String itemId : items) {
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            out.append(getDisplayName(itemId));
            first = false;
        }
        return out.toString();
    }

    private Set<String> parsePresetText(String text) {
        ensureAllItemsLoaded();
        Set<String> items = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }
        String[] tokens = text.split("[,;\\n]+");
        for (String token : tokens) {
            String itemId = resolveItemIdFromToken(token);
            if (itemId != null && !itemId.isEmpty()) {
                items.add(itemId);
            }
        }
        return items;
    }

    private String resolveItemIdFromToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (isKnownItemId(trimmed)) {
            return trimmed;
        }
        String normalized = normalizeDisplayName(trimmed);
        String byName = itemIdByDisplayName.get(normalized);
        if (byName != null) {
            return byName;
        }
        String candidate = trimmed.replace(' ', '_');
        if (isKnownItemId(candidate)) {
            return candidate;
        }
        String HyProTech = "HyProTech_" + candidate;
        if (isKnownItemId(HyProTech)) {
            return HyProTech;
        }
        String ore = "Ore_" + candidate;
        if (isKnownItemId(ore)) {
            return ore;
        }
        return null;
    }

    private boolean isKnownItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null && item != Item.UNKNOWN && !item.isState();
    }

    private String getDisplayName(String itemId) {
        if (UiItemIds.isEmptyItemId(itemId)) {
            return "";
        }
        String name = itemDisplayNames.get(itemId);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return humanizeItemId(itemId);
    }

    private String resolveDisplayName(String itemId, Item item) {
        String name = humanizeItemId(itemId);
        return name == null || name.isEmpty() ? itemId : name;
    }

    private String normalizeDisplayName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase();
    }

    private String humanizeItemId(String itemId) {
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

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        List<String> keepUpper = Arrays.asList("AI", "MOX", "HV", "LV", "UV", "PCB");
        String[] parts = input.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            String word = part;
            String upper = part.toUpperCase();
            if (keepUpper.contains(upper)) {
                word = upper;
            } else if (part.length() > 1) {
                word = Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase();
            } else {
                word = part.toUpperCase();
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word);
        }
        return out.toString();
    }

    private String categorizeItem(String itemId) {
        if (itemId == null) {
            return CATEGORY_OTHER;
        }
        String id = itemId.toLowerCase();
        if (id.startsWith("ore_") || id.contains("_ore_") || id.endsWith("_ore")) {
            return CATEGORY_ORES;
        }
        if (id.contains("ingot")) {
            return CATEGORY_INGOTS;
        }
        if (id.contains("plate") || id.contains("plating") || id.contains("panel")) {
            return CATEGORY_PLATES;
        }
        if (id.contains("cable") || id.contains("wire")) {
            return CATEGORY_CABLES;
        }
        if (id.contains("acid")
                || id.contains("fluid")
                || id.contains("solvent")
                || id.contains("gel")
                || id.contains("lubricant")
                || id.contains("resin")
                || id.contains("slurry")) {
            return CATEGORY_FLUIDS;
        }
        if (id.contains("uranium")
                || id.contains("plutonium")
                || id.contains("nuclear")
                || id.contains("reactor")
                || id.contains("radiation")
                || id.contains("fuel")
                || id.contains("isotope")) {
            return CATEGORY_NUCLEAR;
        }
        if (id.contains("furnace")
                || id.contains("crusher")
                || id.contains("smelter")
                || id.contains("quarry")
                || id.contains("assembler")
                || id.contains("machine")
                || id.contains("centrifuge")
                || id.contains("leacher")
                || id.contains("electrolyzer")
                || id.contains("distillation")
                || id.contains("sifter")
                || id.contains("washer")
                || id.contains("turbine")
                || id.contains("generator")
                || id.contains("solar")
                || id.contains("wind")) {
            return CATEGORY_MACHINES;
        }
        if (id.startsWith("HyProTech_") || id.startsWith("ingredient_")) {
            return CATEGORY_COMPONENTS;
        }
        return CATEGORY_OTHER;
    }

    private void rebuildFilterItems() {
        filterItems.clear();
        int total = filteredItemIds.size();
        if (total <= 0) {
            return;
        }
        int start = filterPageIndex * FILTER_ROW_COUNT;
        if (start >= total) {
            filterPageIndex = Math.max(0, (total - 1) / FILTER_ROW_COUNT);
            start = filterPageIndex * FILTER_ROW_COUNT;
        }
        int end = Math.min(total, start + FILTER_ROW_COUNT);
        for (int i = start; i < end; i++) {
            filterItems.add(filteredItemIds.get(i));
        }
    }

    private int parseIndex(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getNeighborItemId(World world, Vector3i blockPos, EnergySide side) {
        if (world == null || blockPos == null) {
            return "";
        }

        int nx = blockPos.getX() + side.dx();
        int ny = blockPos.getY() + side.dy();
        int nz = blockPos.getZ() + side.dz();
        if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
            return "";
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(nx, nz);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return "";
        }

        BlockType blockType = accessor.getBlockType(nx, ny, nz);
        if (blockType == null || blockType == BlockType.EMPTY || BlockType.EMPTY_KEY.equals(blockType.getId())) {
            return "";
        }

        if (blockType.getItem() == null || blockType.getItem().getId() == null) {
            return "";
        }

        return UiItemIds.safeItemId(blockType.getItem().getId());
    }

    private Vector3i resolveStoragePosition(World world, Vector3i blockPos, EnergySide side) {
        if (world == null || blockPos == null || side == null) {
            return null;
        }
        int nx = blockPos.getX() + side.dx();
        int ny = blockPos.getY() + side.dy();
        int nz = blockPos.getZ() + side.dz();
        if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
            return null;
        }
        ContainerLookup lookup = resolveContainerState(world, nx, ny, nz);
        if (lookup == null || lookup.position == null) {
            return null;
        }
        return resolveBasePosition(world, lookup.position);
    }

    private ContainerLookup resolveContainerState(World world, int x, int y, int z) {
        Object state = MachineItemAccess.getContainerState(world, x, y, z);
        if (state != null) {
            return new ContainerLookup(state, new Vector3i(x, y, z));
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
            Object neighborState = MachineItemAccess.getContainerState(world, nx, ny, nz);
            if (neighborState != null) {
                return new ContainerLookup(neighborState, new Vector3i(nx, ny, nz));
            }
        }

        return null;
    }

    private String getStorageConfiguredName(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return "";
        }
        ChunkStore chunkStore = world.getChunkStore();
        ItemStorageConfigComponent config = getStorageConfig(world, chunkStore, pos);
        return config == null || config.getName() == null ? "" : config.getName();
    }

    private String getStorageItemId(World world, Vector3i pos) {
        BlockType blockType = getBlockTypeIfLoaded(world, pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null) {
            return "";
        }
        Item item = blockType.getItem();
        if (item != null && item.getId() != null) {
            return UiItemIds.safeItemId(item.getId());
        }
        return "";
    }

    private void updateStorageName(World world, Vector3i pos, String value) {
        if (world == null || pos == null) {
            return;
        }
        String name = value == null ? "" : value.trim();
        applyStorageConfig(world, pos, config -> config.setName(name));
    }

    private void applyStorageConfig(World world, Vector3i pos, ConfigUpdate update) {
        if (world == null || pos == null || update == null) {
            return;
        }
        Vector3i basePos = resolveBasePosition(world, pos);
        if (basePos == null) {
            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return;
        }
        ItemStorageConfigComponent config = getStorageConfig(world, chunkStore, basePos);
        if (config == null) {
            config = new ItemStorageConfigComponent();
        }
        update.apply(config);
        storeStorageConfig(world, chunkStore, basePos, config);
    }

    private ItemStorageConfigComponent getStorageConfig(World world, ChunkStore chunkStore, Vector3i pos) {
        if (pos == null) {
            return null;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return null;
        }
        if (world != null && HyProTechComponents.ITEM_STORAGE != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor != null) {
                Holder<ChunkStore> holder = accessor.getBlockComponentHolder(x, y, z);
                if (holder != null) {
                    ItemStorageConfigComponent config = holder.getComponent(HyProTechComponents.ITEM_STORAGE);
                    if (config != null) {
                        return config;
                    }
                }
            }
        }
        if (chunkStore == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentChunk components =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (components != null && HyProTechComponents.ITEM_STORAGE != null) {
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            ItemStorageConfigComponent config = components.getComponent(
                    blockIndex,
                    HyProTechComponents.ITEM_STORAGE);
            if (config != null) {
                return config;
            }
        }
        return getChunkStorageConfig(chunkStore, x, y, z);
    }

    private void storeStorageConfig(
            World world,
            ChunkStore chunkStore,
            Vector3i pos,
            ItemStorageConfigComponent config) {
        if (world == null || chunkStore == null || pos == null || config == null) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return;
        }
        BlockType blockType = world.getBlockType(x, y, z);
        String blockId = blockType == null ? null : blockType.getId();
        boolean allowBlockConfig = isHyProTechBlockId(blockId);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        BlockComponentChunk components =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        boolean stored = false;
        if (allowBlockConfig && accessor != null && HyProTechComponents.ITEM_STORAGE != null) {
            Holder<ChunkStore> holder = accessor.getBlockComponentHolder(x, y, z);
            if (holder != null) {
                holder.putComponent(HyProTechComponents.ITEM_STORAGE, config);
                stored = true;
            }
        }
        if (!stored && allowBlockConfig && components != null && HyProTechComponents.ITEM_STORAGE != null) {
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            Ref<ChunkStore> ref = components.getEntityReference(blockIndex);
            if (ref != null) {
                Store<ChunkStore> store = ref.getStore();
                if (store != null) {
                    store.putComponent(ref, HyProTechComponents.ITEM_STORAGE, config);
                    stored = true;
                }
            } else {
                Holder<ChunkStore> holder = components.getEntityHolder(blockIndex);
                if (holder == null) {
                    holder = ChunkStore.REGISTRY.newHolder();
                    holder.putComponent(HyProTechComponents.ITEM_STORAGE, config);
                    components.storeEntityHolder(blockIndex, holder);
                } else {
                    holder.putComponent(HyProTechComponents.ITEM_STORAGE, config);
                }
                stored = true;
            }
        }
        if (components != null && stored) {
            components.markNeedsSaving();
        }
        storeChunkStorageConfig(chunkStore, x, y, z, config);
    }

    private boolean isHyProTechBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        int colonIndex = blockId.indexOf(':');
        String normalized = colonIndex >= 0 ? blockId.substring(colonIndex + 1) : blockId;
        return normalized.regionMatches(true, 0, "HyProTech_", 0, "HyProTech_".length());
    }

    private ItemStorageConfigComponent getChunkStorageConfig(ChunkStore chunkStore, int x, int y, int z) {
        if (chunkStore == null || HyProTechComponents.ITEM_STORAGE_CHUNK == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        ItemStorageConfigChunk chunkConfig =
                chunkStore.getChunkComponent(chunkIndex, HyProTechComponents.ITEM_STORAGE_CHUNK);
        if (chunkConfig == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        return chunkConfig.getConfig(blockIndex);
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
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null) {
            return;
        }
        Store<ChunkStore> store = chunkRef.getStore();
        if (store != null) {
            store.putComponent(chunkRef, HyProTechComponents.ITEM_STORAGE_CHUNK, chunkConfig);
        }
    }

    private String sideId(EnergySide side) {
        return "#Side" + side.label();
    }

    private String sideIconId(EnergySide side) {
        return "#Side" + side.label() + "Icon";
    }

    private String sideFilterId(EnergySide side) {
        return "#Side" + side.label() + "Filter";
    }

    private String sideRenameId(EnergySide side) {
        return "#Side" + side.label() + "Rename";
    }

    private String filterRowId(int index) {
        return "#FilterRow" + index;
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

    private String filterIconId(int index) {
        return "#FilterItem" + index + "Icon";
    }

    private String filterCheckId(int index) {
        return "#FilterItem" + index + "Check";
    }

    private String filterLabelId(int index) {
        return "#FilterItem" + index + "Label";
    }

    private String presetSlotId(int index) {
        return "#FilterPresetSlot" + index;
    }

    private Vector3i resolveBlockPosition(World world) {
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
        EntityStore entityStore = store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }

    private void openNetworkPage(Ref<EntityStore> playerEntityRef, Store<EntityStore> store) {
        if (playerEntityRef == null || store == null) {
            return;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        ItemNetworkPriorityPage page = new ItemNetworkPriorityPage(
                playerRef,
                blockRef,
                itemType,
                HyProTechComponents.ITEM_STORAGE,
                HyProTechComponents.ITEM_STORAGE_CHUNK);
        pageManager.openCustomPage(playerEntityRef, store, page);
    }

    private static final class ContainerLookup {
        private final Object state;
        private final Vector3i position;

        private ContainerLookup(Object state, Vector3i position) {
            this.state = state;
            this.position = position;
        }
    }

    private interface ConfigUpdate {
        void apply(ItemStorageConfigComponent config);
    }
}
