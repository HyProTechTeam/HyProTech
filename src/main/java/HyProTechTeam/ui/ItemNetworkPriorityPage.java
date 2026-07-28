package HyProTechTeam.ui;

import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.item.ItemDistributionMode;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.item.ItemStorageConfigChunk;
import HyProTechTeam.item.ItemStorageConfigComponent;
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
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemNetworkPriorityPage extends InteractiveCustomUIPage<ItemNetworkPriorityEvent> {
    private static final String PAGE_LAYOUT = "HyProTech_Item_Priority.ui";
    private static final int ROW_COUNT = 6;
    private static final long UPDATE_INTERVAL_MS = 250L;

    private final Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, ItemNodeComponent> itemType;
    private final ComponentType<ChunkStore, ItemStorageConfigComponent> storageType;
    private final ComponentType<ChunkStore, ItemStorageConfigChunk> storageChunkType;
    private final List<StorageEntry> storageEntries = new ArrayList<>();

    private Vector3i blockPosition;
    private long lastUpdateMs;
    private int pageIndex;
    private String lastSnapshotKey = "";
    private Vector3i renameTargetPos;

    public ItemNetworkPriorityPage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, ItemNodeComponent> itemType,
            ComponentType<ChunkStore, ItemStorageConfigComponent> storageType,
            ComponentType<ChunkStore, ItemStorageConfigChunk> storageChunkType) {
        super(playerRef, CustomPageLifetime.CanDismiss, ItemNetworkPriorityEvent.CODEC);
        this.blockRef = blockRef;
        this.itemType = itemType;
        this.storageType = storageType;
        this.storageChunkType = storageChunkType;
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
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, ItemNetworkPriorityEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        World world = getWorld(store);
        Vector3i startPos = resolveBlockPosition(world);
        if (world == null || startPos == null) {
            return;
        }

        NetworkSnapshot snapshot = buildSnapshot(world, startPos);
        if (snapshot == null) {
            return;
        }
        storageEntries.clear();
        storageEntries.addAll(snapshot.storages);

        String action = data.getAction();
        if ("Distribution".equalsIgnoreCase(action)) {
            ItemDistributionMode next = snapshot.distributionMode == null
                    ? ItemDistributionMode.ROUND_ROBIN
                    : snapshot.distributionMode.next();
            applyDistribution(world, snapshot, next);
            forceRefresh();
            return;
        }

        if ("Prev".equalsIgnoreCase(action)) {
            if (pageIndex > 0) {
                pageIndex--;
            }
            forceRefresh();
            return;
        }
        if ("Next".equalsIgnoreCase(action)) {
            int maxPage = getMaxPage(storageEntries.size());
            if (pageIndex < maxPage) {
                pageIndex++;
            }
            forceRefresh();
            return;
        }

        if ("Priority".equalsIgnoreCase(action)) {
            StorageEntry entry = getEntryByRow(data.getIndex());
            if (entry == null) {
                return;
            }
            updatePriority(world, entry);
            forceRefresh();
            return;
        }

        if ("Rename".equalsIgnoreCase(action)) {
            StorageEntry entry = getEntryByRow(data.getIndex());
            if (entry == null) {
                return;
            }
            renameTargetPos = entry.position;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", true);
            update.set("#RenameInput.Value", entry.configuredName == null ? "" : entry.configuredName);
            sendUpdate(update);
            return;
        }

        if ("RenameSave".equalsIgnoreCase(action)) {
            if (renameTargetPos != null) {
                updateName(world, renameTargetPos, data.getValue());
            }
            renameTargetPos = null;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", false);
            update.set("#RenameInput.Value", "");
            sendUpdate(update);
            forceRefresh();
            return;
        }

        if ("RenameCancel".equalsIgnoreCase(action)) {
            renameTargetPos = null;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#RenamePanel.Visible", false);
            update.set("#RenameInput.Value", "");
            sendUpdate(update);
        }
    }

    public void update() {
        long now = System.currentTimeMillis();
        if (lastUpdateMs != 0L && now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }
        lastUpdateMs = now;

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        World world = getWorld(store);
        Vector3i startPos = resolveBlockPosition(world);
        if (world == null || startPos == null) {
            return;
        }

        NetworkSnapshot snapshot = buildSnapshot(world, startPos);
        if (snapshot == null) {
            return;
        }
        storageEntries.clear();
        storageEntries.addAll(snapshot.storages);

        int maxPage = getMaxPage(storageEntries.size());
        if (pageIndex > maxPage) {
            pageIndex = maxPage;
        }

        String snapshotKey = snapshot.buildKey(pageIndex);
        if (snapshotKey.equals(lastSnapshotKey)) {
            return;
        }
        lastSnapshotKey = snapshotKey;

        UICommandBuilder update = new UICommandBuilder();
        updateDistribution(update, snapshot);
        updateStorageRows(update, storageEntries);
        updatePaging(update, storageEntries.size());
        sendUpdate(update);
    }

    private void bindButtons(UIEventBuilder uiEventBuilder) {
        EventData distributionData = EventData.of("Action", "Distribution");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NetworkDistribution", distributionData);

        EventData prevData = EventData.of("Action", "Prev");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StoragePrev", prevData);
        EventData nextData = EventData.of("Action", "Next");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StorageNext", nextData);

        EventData saveData = EventData.of("Action", "RenameSave").append("@Value", "#RenameInput.Value");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RenameSave", saveData);
        EventData cancelData = EventData.of("Action", "RenameCancel");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RenameCancel", cancelData);

        for (int i = 0; i < ROW_COUNT; i++) {
            EventData priorityData = EventData.of("Action", "Priority").append("Index", Integer.toString(i));
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    storagePriorityId(i),
                    priorityData);

            EventData renameData = EventData.of("Action", "Rename").append("Index", Integer.toString(i));
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    storageRenameId(i),
                    renameData);
        }
    }

    private void updateDistribution(UICommandBuilder update, NetworkSnapshot snapshot) {
        String label;
        if (snapshot == null || snapshot.cables.isEmpty()) {
            label = "Distribution: -";
        } else if (snapshot.distributionMode == null) {
            label = "Distribution: Mixed";
        } else {
            label = "Distribution: " + snapshot.distributionMode.label();
        }
        update.set("#NetworkDistribution.Text", label);
    }

    private void updateStorageRows(UICommandBuilder update, List<StorageEntry> storages) {
        int total = storages == null ? 0 : storages.size();
        int startIndex = pageIndex * ROW_COUNT;
        for (int i = 0; i < ROW_COUNT; i++) {
            int absoluteIndex = startIndex + i;
            boolean visible = absoluteIndex >= 0 && absoluteIndex < total;
            update.set(storageRowId(i) + ".Visible", visible);
            if (!visible) {
                continue;
            }
            StorageEntry entry = storages.get(absoluteIndex);
            update.set(storageIconId(i) + ".ItemId", UiItemIds.safeItemId(entry.iconItemId));
            update.set(storageNameId(i) + ".Text", entry.displayName);
            update.set(storagePriorityId(i) + ".Text", "Priority: " + entry.priority);
        }
    }

    private void updatePaging(UICommandBuilder update, int total) {
        if (total <= 0) {
            update.set("#StoragePage.Text", "0 / 0");
            update.set("#StoragePrev.Visible", false);
            update.set("#StorageNext.Visible", false);
            return;
        }

        int maxPage = getMaxPage(total);
        int start = pageIndex * ROW_COUNT + 1;
        int end = Math.min(total, start + ROW_COUNT - 1);
        update.set("#StoragePage.Text", start + "-" + end + " / " + total);
        update.set("#StoragePrev.Visible", pageIndex > 0);
        update.set("#StorageNext.Visible", pageIndex < maxPage);
    }

    private StorageEntry getEntryByRow(String indexValue) {
        int row = parseIndex(indexValue);
        if (row < 0) {
            return null;
        }
        int absoluteIndex = pageIndex * ROW_COUNT + row;
        if (absoluteIndex < 0 || absoluteIndex >= storageEntries.size()) {
            return null;
        }
        return storageEntries.get(absoluteIndex);
    }

    private void updatePriority(World world, StorageEntry entry) {
        if (world == null || entry == null) {
            return;
        }
        int nextPriority = entry.priority + 1;
        if (nextPriority > ItemNodeComponent.MAX_PRIORITY) {
            nextPriority = ItemNodeComponent.MIN_PRIORITY;
        }
        final int finalPriority = nextPriority;
        applyStorageConfig(world, entry.position, config -> config.setPriority(finalPriority));
    }

    private void updateName(World world, Vector3i pos, String value) {
        if (world == null || pos == null) {
            return;
        }
        String name = value == null ? "" : value.trim();
        applyStorageConfig(world, pos, config -> config.setName(name));
    }

    private void applyDistribution(World world, NetworkSnapshot snapshot, ItemDistributionMode mode) {
        if (world == null || snapshot == null || mode == null) {
            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return;
        }
        Map<Long, BlockComponentChunk> componentsByChunk = new HashMap<>();
        for (CableInfo cable : snapshot.cables) {
            if (cable == null || cable.node == null) {
                continue;
            }
            cable.node.setDistributionMode(mode);
            BlockComponentChunk components = componentsByChunk.get(cable.chunkIndex);
            if (components == null) {
                components = chunkStore.getChunkComponent(cable.chunkIndex, BlockComponentChunk.getComponentType());
                componentsByChunk.put(cable.chunkIndex, components);
            }
            if (components != null) {
                components.markNeedsSaving();
            }
        }
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
        if (world != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
            if (accessor != null) {
                Holder<ChunkStore> holder = accessor.getBlockComponentHolder(x, y, z);
                if (holder != null) {
                    ItemStorageConfigComponent config = holder.getComponent(storageType);
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
        if (components != null && storageType != null) {
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            ItemStorageConfigComponent config = components.getComponent(blockIndex, storageType);
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
        if (allowBlockConfig && accessor != null && storageType != null) {
            Holder<ChunkStore> holder = accessor.getBlockComponentHolder(x, y, z);
            if (holder != null) {
                holder.putComponent(storageType, config);
                stored = true;
            }
        }
        if (!stored && allowBlockConfig && components != null && storageType != null) {
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            Ref<ChunkStore> ref = components.getEntityReference(blockIndex);
            if (ref != null) {
                Store<ChunkStore> store = ref.getStore();
                if (store != null) {
                    store.putComponent(ref, storageType, config);
                    stored = true;
                }
            } else {
                Holder<ChunkStore> holder = components.getEntityHolder(blockIndex);
                if (holder == null) {
                    holder = ChunkStore.REGISTRY.newHolder();
                    holder.putComponent(storageType, config);
                    components.storeEntityHolder(blockIndex, holder);
                } else {
                    holder.putComponent(storageType, config);
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
        if (chunkStore == null || storageChunkType == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        ItemStorageConfigChunk chunkConfig = chunkStore.getChunkComponent(chunkIndex, storageChunkType);
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
        if (chunkStore == null || storageChunkType == null || config == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        ItemStorageConfigChunk chunkConfig = chunkStore.getChunkComponent(chunkIndex, storageChunkType);
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
            store.putComponent(chunkRef, storageChunkType, chunkConfig);
        }
    }

    private void forceRefresh() {
        lastSnapshotKey = "";
        lastUpdateMs = 0L;
        update();
    }

    private int getMaxPage(int total) {
        if (total <= 0) {
            return 0;
        }
        return (total - 1) / ROW_COUNT;
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

    private NetworkSnapshot buildSnapshot(World world, Vector3i startPos) {
        if (world == null || startPos == null) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }

        Deque<Vector3i> queue = new ArrayDeque<>();
        Long2ObjectMap<IntOpenHashSet> visited = new Long2ObjectOpenHashMap<>();
        if (!markVisited(visited, startPos.getX(), startPos.getY(), startPos.getZ())) {
            return null;
        }
        queue.add(startPos);

        List<CableInfo> cables = new ArrayList<>();
        Map<String, StorageEntry> storages = new HashMap<>();

        while (!queue.isEmpty()) {
            Vector3i pos = queue.removeFirst();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockComponentChunk components =
                    chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (components == null) {
                continue;
            }
            int localX = ChunkUtil.localCoordinate((long) pos.getX());
            int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
            ItemNodeComponent node = components.getComponent(blockIndex, itemType);
            if (node == null) {
                continue;
            }

            cables.add(new CableInfo(node, pos.getX(), pos.getY(), pos.getZ(), chunkIndex));

            boolean allowTake = node.getMode() != null && node.getMode().allowsTake();
            boolean allowPut = node.getMode() != null && node.getMode().allowsPut();

            for (EnergySide side : EnergySide.VALUES) {
                int nx = pos.getX() + side.dx();
                int ny = pos.getY() + side.dy();
                int nz = pos.getZ() + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }

                if (isItemCable(chunkStore, nx, ny, nz)) {
                    if (markVisited(visited, nx, ny, nz)) {
                        queue.add(new Vector3i(nx, ny, nz));
                    }
                    continue;
                }

                boolean sideAllowsTake = allowTake && node.allowsTake(side);
                boolean sideAllowsPut = allowPut && node.allowsPut(side);
                if (!sideAllowsTake && !sideAllowsPut) {
                    continue;
                }

                ContainerLookup lookup = resolveContainerState(world, nx, ny, nz);
                if (lookup == null) {
                    continue;
                }
                addStorageEntry(world, chunkStore, lookup.position, storages);
            }
        }

        if (cables.isEmpty()) {
            return null;
        }

        List<StorageEntry> storageList = new ArrayList<>(storages.values());
        storageList.sort(STORAGE_ORDER);

        ItemDistributionMode mode = resolveDistributionMode(cables);
        return new NetworkSnapshot(cables, storageList, mode);
    }

    private ItemDistributionMode resolveDistributionMode(List<CableInfo> cables) {
        ItemDistributionMode mode = null;
        for (CableInfo cable : cables) {
            if (cable == null || cable.node == null) {
                continue;
            }
            ItemDistributionMode current = cable.node.getDistributionMode();
            if (current == null) {
                current = ItemDistributionMode.ROUND_ROBIN;
            }
            if (mode == null) {
                mode = current;
            } else if (mode != current) {
                return null;
            }
        }
        return mode;
    }

    private void addStorageEntry(
            World world,
            ChunkStore chunkStore,
            Vector3i pos,
            Map<String, StorageEntry> storages) {
        if (pos == null || storages == null) {
            return;
        }
        Vector3i basePos = resolveBasePosition(world, pos);
        if (basePos == null) {
            return;
        }
        String key = basePos.getX() + "," + basePos.getY() + "," + basePos.getZ();
        if (storages.containsKey(key)) {
            return;
        }

        ItemStorageConfigComponent config = getStorageConfig(world, chunkStore, basePos);
        String configuredName = config == null ? "" : config.getName();
        int priority = config == null ? ItemNodeComponent.DEFAULT_PRIORITY : config.getPriority();

        String fallbackName = buildFallbackName(world, basePos);
        String displayName = configuredName == null || configuredName.isEmpty()
                ? fallbackName
                : configuredName;
        String iconItemId = getStorageIcon(world, basePos);

        StorageEntry entry = new StorageEntry(basePos, displayName, configuredName, priority, iconItemId);
        storages.put(key, entry);
    }

    private String buildFallbackName(World world, Vector3i pos) {
        BlockType blockType = getBlockTypeIfLoaded(world, pos.getX(), pos.getY(), pos.getZ());
        if (blockType != null) {
            Item item = blockType.getItem();
            if (item != null && item.getId() != null && !item.getId().isEmpty()) {
                return item.getId();
            }
            if (blockType.getId() != null && !blockType.getId().isEmpty()) {
                return blockType.getId();
            }
        }
        return "Storage " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String getStorageIcon(World world, Vector3i pos) {
        BlockType blockType = getBlockTypeIfLoaded(world, pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null) {
            return "";
        }
        Item item = blockType.getItem();
        if (item != null && item.getId() != null) {
            return item.getId();
        }
        return "";
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

    private boolean markVisited(Long2ObjectMap<IntOpenHashSet> visited, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);

        IntOpenHashSet set = visited.get(chunkIndex);
        if (set == null) {
            set = new IntOpenHashSet();
            visited.put(chunkIndex, set);
        }
        return set.add(blockIndex);
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
        EntityStore entityStore = store == null ? null : store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }

    private String storageRowId(int index) {
        return "#StorageRow" + index;
    }

    private String storageIconId(int index) {
        return "#Storage" + index + "Icon";
    }

    private String storageNameId(int index) {
        return "#Storage" + index + "Name";
    }

    private String storagePriorityId(int index) {
        return "#Storage" + index + "Priority";
    }

    private String storageRenameId(int index) {
        return "#Storage" + index + "Rename";
    }

    private static final Comparator<StorageEntry> STORAGE_ORDER = (first, second) -> {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        int cmp = first.sortKey.compareTo(second.sortKey);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(first.position.getX(), second.position.getX());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(first.position.getY(), second.position.getY());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(first.position.getZ(), second.position.getZ());
    };

    private static final class CableInfo {
        private final ItemNodeComponent node;
        private final int x;
        private final int y;
        private final int z;
        private final long chunkIndex;

        private CableInfo(ItemNodeComponent node, int x, int y, int z, long chunkIndex) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkIndex = chunkIndex;
        }
    }

    private static final class StorageEntry {
        private final Vector3i position;
        private final String displayName;
        private final String configuredName;
        private final int priority;
        private final String iconItemId;
        private final String sortKey;

        private StorageEntry(
                Vector3i position,
                String displayName,
                String configuredName,
                int priority,
                String iconItemId) {
            this.position = position;
            this.displayName = displayName;
            this.configuredName = configuredName;
            this.priority = priority;
            this.iconItemId = iconItemId == null ? "" : iconItemId;
            this.sortKey = (displayName == null ? "" : displayName.toLowerCase());
        }
    }

    private static final class NetworkSnapshot {
        private final List<CableInfo> cables;
        private final List<StorageEntry> storages;
        private final ItemDistributionMode distributionMode;

        private NetworkSnapshot(
                List<CableInfo> cables,
                List<StorageEntry> storages,
                ItemDistributionMode distributionMode) {
            this.cables = cables == null ? new ArrayList<>() : cables;
            this.storages = storages == null ? new ArrayList<>() : storages;
            this.distributionMode = distributionMode;
        }

        private String buildKey(int pageIndex) {
            StringBuilder key = new StringBuilder();
            key.append(pageIndex).append('|');
            if (distributionMode == null) {
                key.append("mixed");
            } else {
                key.append(distributionMode.name());
            }
            key.append('|').append(storages.size());
            for (StorageEntry entry : storages) {
                key.append('|')
                        .append(entry.position.getX()).append(',')
                        .append(entry.position.getY()).append(',')
                        .append(entry.position.getZ()).append(':')
                        .append(entry.priority).append(':')
                        .append(entry.displayName);
            }
            return key.toString();
        }
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
