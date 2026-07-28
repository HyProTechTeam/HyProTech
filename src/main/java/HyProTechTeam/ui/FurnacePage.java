package HyProTechTeam.ui;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergyUnits;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.Map;
import java.util.UUID;

public class FurnacePage extends InteractiveCustomUIPage<FurnacePage.FurnaceEventData> {
    private static final String PAGE_LAYOUT = "HyProTech_Furnace.ui";
    private static final String ACTION_OPEN_STORAGE = "OpenStorage";
    private static final String ACTION_TOGGLE_POWER = "TogglePower";

    private final Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private Vector3i blockPosition;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastConsumption = Integer.MIN_VALUE;
    private int lastProgress = Integer.MIN_VALUE;
    private int lastProgressMax = Integer.MIN_VALUE;
    private boolean lastEnabled;
    private String lastInputItemId;
    private int lastInputQty = Integer.MIN_VALUE;
    private String lastOutputItemId;
    private int lastOutputQty = Integer.MIN_VALUE;

    public FurnacePage(
            PlayerRef playerRef,
            Ref<ChunkStore> blockRef,
            ComponentType<ChunkStore, EnergyNodeComponent> energyType) {
        super(playerRef, CustomPageLifetime.CanDismiss, FurnaceEventData.CODEC);
        this.blockRef = blockRef;
        this.energyType = energyType;
    }

    public Ref<ChunkStore> getBlockRef() {
        return blockRef;
    }

    public void setBlockPosition(Vector3i blockPosition) {
        this.blockPosition = blockPosition;
    }

    public EnergyNodeComponent ensureNode(World world) {
        if (world == null) {
            return null;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        return chunkStore.getComponent(blockRef, energyType);
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
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, FurnaceEventData data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        String action = data.getAction();
        if (ACTION_TOGGLE_POWER.equalsIgnoreCase(action)) {
            togglePower(store);
        } else if (ACTION_OPEN_STORAGE.equalsIgnoreCase(action)) {
            if (!openProcessingBench(playerRef, store)) {
                sendUpdate(new UICommandBuilder());
            }
        }
    }

    public void update(EnergyNodeComponent node) {
        if (node == null) {
            return;
        }

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = false;

        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#FurnaceEnergy.Text",
                    "Energy: " + EnergyUnits.formatEnergyWithCapacity(energy, capacity));
            lastEnergy = energy;
            lastCapacity = capacity;
            changed = true;
        }

        int consumption = node.getConsumption();
        if (consumption != lastConsumption) {
            update.set("#FurnaceConsumption.Text", "Consumption: " + EnergyUnits.formatWatts(consumption));
            lastConsumption = consumption;
            changed = true;
        }

        boolean enabled = node.isEnabled();
        if (enabled != lastEnabled) {
            String statusLabel = enabled ? "ON" : "OFF";
            update.set("#FurnaceStatus.Text", "Status: " + statusLabel);
            update.set("#FurnaceToggle.Text", "Power: " + statusLabel);
            lastEnabled = enabled;
            changed = true;
        }

        int progress = node.getProgress();
        int progressMax = node.getProgressMax();
        if (progress != lastProgress || progressMax != lastProgressMax) {
            int percent = 0;
            if (progressMax > 0) {
                percent = (int) Math.round(100.0 * progress / progressMax);
                percent = Math.min(100, Math.max(0, percent));
            }
            update.set("#FurnaceProgress.Text", "Progress: " + percent + "%");
            lastProgress = progress;
            lastProgressMax = progressMax;
            changed = true;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null) {
            Store<EntityStore> store = playerEntityRef.getStore();
            World world = getWorld(store);
            Vector3i pos = resolveBlockPosition(world);
            ProcessingBenchBlock benchState = getProcessingBenchState(world, pos);
            ItemContainer inputContainer = getBenchInputContainer(benchState);
            ItemContainer outputContainer = getBenchOutputContainer(benchState);
            ItemStack inputStack = getFirstStack(inputContainer);
            ItemStack outputStack = getFirstStack(outputContainer);
            if (updateSlot(update, true, inputStack)) {
                changed = true;
            }
            if (updateSlot(update, false, outputStack)) {
                changed = true;
            }
        }

        if (changed) {
            sendUpdate(update);
        }
    }

    private void bindButtons(UIEventBuilder uiEventBuilder) {
        EventData openData = EventData.of("Action", ACTION_OPEN_STORAGE);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpenInventory", openData);

        EventData powerData = EventData.of("Action", ACTION_TOGGLE_POWER);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FurnaceToggle", powerData);
    }

    private void togglePower(Store<EntityStore> store) {
        World world = getWorld(store);
        if (world == null) {
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        EnergyNodeComponent node = chunkStore.getComponent(blockRef, energyType);
        if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.FURNACE) {
            return;
        }

        node.setEnabled(!node.isEnabled());
        chunkStore.putComponent(blockRef, energyType, node);
        update(node);
    }

    private boolean openProcessingBench(Ref<EntityStore> playerEntityRef, Store<EntityStore> store) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return false;
        }

        PageManager pageManager = player.getPageManager();
        dismissCustomPage(playerEntityRef, store, pageManager);

        if (getOpenFurnaceWindow(player) != null) {
            return true;
        }

        World world = getWorld(store);
        if (world == null) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        ProcessingBenchBlock benchState = getProcessingBenchState(world, pos);
        if (benchState == null) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null || blockType.getBench() == null) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        if (!ensureBenchInitialized(benchState, blockType)) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        ProcessingBenchWindow window = getOrCreateBenchWindow(playerEntityRef, store, benchState);
        if (window == null) {
            pageManager.openCustomPage(playerEntityRef, store, this);
            return false;
        }

        if (pageManager.setPageWithWindows(
                playerEntityRef,
                store,
                Page.Bench,
                true,
                new Window[] { window })) {
            return true;
        }

        ContainerWindow inventoryWindow = new ContainerWindow(player.getInventory().getStorage());
        if (pageManager.setPageWithWindows(
                playerEntityRef,
                store,
                Page.Inventory,
                true,
                new Window[] { inventoryWindow, window })) {
            return true;
        }

        pageManager.openCustomPage(playerEntityRef, store, this);
        return false;
    }

    private void dismissCustomPage(
            Ref<EntityStore> playerEntityRef,
            Store<EntityStore> store,
            PageManager pageManager) {
        try {
            pageManager.handleEvent(
                    playerEntityRef,
                    store,
                    new CustomPageEvent(CustomPageEventType.Dismiss, ""));
        } catch (Throwable ignored) {
        }
    }

    private ProcessingBenchWindow getOpenFurnaceWindow(Player player) {
        if (player == null) {
            return null;
        }
        for (Window window : player.getWindowManager().getWindows()) {
            if (!(window instanceof ProcessingBenchWindow)) {
                continue;
            }
            ProcessingBenchWindow benchWindow = (ProcessingBenchWindow) window;
            if (benchWindow.getBlockType() == null) {
                continue;
            }
            if (TieredIdUtil.isTieredId(
                    benchWindow.getBlockType().getId(),
                    HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
                return benchWindow;
            }
        }
        return null;
    }

    private ProcessingBenchWindow getOrCreateBenchWindow(
            Ref<EntityStore> playerEntityRef,
            Store<EntityStore> store,
            ProcessingBenchBlock benchState) {
        UUIDComponent uuidComponent =
                store.getComponent(playerEntityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }

        World world = getWorld(store);
        Vector3i pos = resolveBlockPosition(world);
        if (pos == null) {
            return null;
        }
        BenchBlock benchBlock = world == null ? null
                : BlockModule.get().getComponent(BenchBlock.getComponentType(), world, pos.getX(), pos.getY(), pos.getZ());
        if (benchBlock == null) {
            return null;
        }
        BlockType benchBlockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        if (benchBlockType == null) {
            return null;
        }

        UUID playerId = uuidComponent.getUuid();
        Map<UUID, BenchWindow> windows = benchBlock.getWindows();
        BenchWindow existing = windows.get(playerId);
        if (existing != null) {
            if (existing instanceof ProcessingBenchWindow) {
                return (ProcessingBenchWindow) existing;
            }
            return null;
        }

        ProcessingBenchWindow window = new ProcessingBenchWindow(benchState, benchBlock, null, pos.getX(), pos.getY(), pos.getZ(), 0, benchBlockType);
        BenchWindow prior = windows.putIfAbsent(playerId, window);
        if (prior != null) {
            if (prior instanceof ProcessingBenchWindow) {
                return (ProcessingBenchWindow) prior;
            }
            return null;
        }

        benchState.updateFuelValues(windows);
        window.registerCloseEvent(event -> windows.remove(playerId, window));
        return window;
    }

    private boolean updateSlot(UICommandBuilder update, boolean isInput, ItemStack stack) {
        String itemId = "";
        int quantity = 0;

        if (stack != null && !ItemStack.isEmpty(stack)) {
            itemId = stack.getItemId();
            quantity = stack.getQuantity();
        }
        itemId = UiItemIds.safeItemId(itemId);

        boolean changed = false;
        String qtyText = quantity > 1 ? Integer.toString(quantity) : "";
        if (isInput) {
            if (!itemId.equals(lastInputItemId)) {
                update.set("#InputSlot.ItemId", itemId);
                lastInputItemId = itemId;
                changed = true;
            }
            if (quantity != lastInputQty) {
                update.set("#InputQty.Text", qtyText);
                lastInputQty = quantity;
                changed = true;
            }
        } else {
            if (!itemId.equals(lastOutputItemId)) {
                update.set("#OutputSlot.ItemId", itemId);
                lastOutputItemId = itemId;
                changed = true;
            }
            if (quantity != lastOutputQty) {
                update.set("#OutputQty.Text", qtyText);
                lastOutputQty = quantity;
                changed = true;
            }
        }

        return changed;
    }

    private ItemStack getFirstStack(ItemContainer container) {
        if (container == null || container.getCapacity() <= 0) {
            return null;
        }
        return container.getItemStack((short) 0);
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

    private int findBlockIndex(BlockComponentChunk blockComponents) {
        int targetIndex = blockRef.getIndex();
        if (targetIndex == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        for (Int2ReferenceMap.Entry<Ref<ChunkStore>> entry
                : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            Ref<ChunkStore> entryRef = entry.getValue();
            if (entryRef != null && entryRef.getIndex() == targetIndex) {
                return entry.getIntKey();
            }
        }
        return Integer.MIN_VALUE;
    }

    private ProcessingBenchBlock getProcessingBenchState(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return null;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            return null;
        }

        return BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ());
    }

    private boolean ensureBenchInitialized(ProcessingBenchBlock benchState, BlockType blockType) {
        if (benchState == null || blockType == null || blockType.getBench() == null) {
            return false;
        }

        boolean needsInit = benchState.getBench() == null
                || !blockType.getBench().equals(benchState.getBench());
        if (needsInit && !benchState.initializeBenchConfig(blockType)) {
            return false;
        }
        return benchState.getItemContainer() != null;
    }

    private ItemContainer getBenchInputContainer(ProcessingBenchBlock benchState) {
        if (benchState == null) {
            return null;
        }
        return benchState.getInputContainer();
    }

    private ItemContainer getBenchOutputContainer(ProcessingBenchBlock benchState) {
        if (benchState == null) {
            return null;
        }
        return benchState.getOutputContainer();
    }

    private World getWorld(Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }

    public static final class FurnaceEventData {
        public static final BuilderCodec<FurnaceEventData> CODEC =
                BuilderCodec.<FurnaceEventData>builder(FurnaceEventData.class, FurnaceEventData::new)
                        .addField(new KeyedCodec<>("Action", new StringCodec()),
                                (event, value) -> event.action = value,
                                event -> event.action)
                        .build();

        private String action;

        public String getAction() {
            return action;
        }
    }
}
