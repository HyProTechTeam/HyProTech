package HyProTechTeam.ui;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.EnergySideMode;
import HyProTechTeam.energy.EnergyUnits;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class CablePage extends InteractiveCustomUIPage<SideToggleEvent> {
    private static final String NO_LINK_LABEL = "No link";
    private static final long ENERGY_UPDATE_INTERVAL_MS = 250L;

    private final Ref<ChunkStore> blockRef;
    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private Vector3i blockPosition;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastInputMask = Integer.MIN_VALUE;
    private int lastOutputMask = Integer.MIN_VALUE;
    private int lastConnectedMask = Integer.MIN_VALUE;
    private String lastNeighborKey = "";
    private long lastEnergyUpdateMs;

    public CablePage(
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
        uiCommandBuilder.append("HyProTech_Cable.ui");
        bindSideButtons(uiEventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, SideToggleEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }

        String action = data.getAction();
        if ("Toggle".equalsIgnoreCase(action)) {
            EnergySide side = EnergySide.fromName(data.getSide());
            if (side == null) {
                return;
            }

            EntityStore entityStore = store.getExternalData();
            World world = entityStore == null ? null : entityStore.getWorld();
            if (world == null) {
                return;
            }

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            EnergyNodeComponent node = chunkStore.getComponent(blockRef, energyType);
            if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.CABLE) {
                return;
            }

            node.cycleSideMode(side);
            chunkStore.putComponent(blockRef, energyType, node);
            update(node);
        }
    }

    public void update(EnergyNodeComponent node) {
        if (node == null) {
            return;
        }

        World world = null;
        Vector3i blockPos = null;
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null) {
            Store<EntityStore> store = playerEntityRef.getStore();
            world = getWorld(store);
            blockPos = resolveBlockPosition(world);
        }

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = false;
        long now = System.currentTimeMillis();

        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            boolean allowUpdate = lastEnergyUpdateMs == 0L
                    || now - lastEnergyUpdateMs >= ENERGY_UPDATE_INTERVAL_MS;
            if (!allowUpdate) {
                energy = lastEnergy;
                capacity = lastCapacity;
            }
        }
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#CableEnergy.Text",
                    "Energy: " + EnergyUnits.formatEnergyWithCapacity(energy, capacity));
            lastEnergy = energy;
            lastCapacity = capacity;
            lastEnergyUpdateMs = now;
            changed = true;
        }

        int inputMask = node.getInputMask();
        int outputMask = node.getOutputMask();
        int connectedMask = node.getConnectedMask();
        String neighborKey = buildNeighborKey(world, blockPos);
        if (inputMask != lastInputMask
                || outputMask != lastOutputMask
                || connectedMask != lastConnectedMask
                || !neighborKey.equals(lastNeighborKey)) {
            updateSideButtons(update, node, world, blockPos);
            lastInputMask = inputMask;
            lastOutputMask = outputMask;
            lastConnectedMask = connectedMask;
            lastNeighborKey = neighborKey;
            changed = true;
        }

        if (changed) {
            sendUpdate(update);
        }
    }

    private void bindSideButtons(UIEventBuilder uiEventBuilder) {
        for (EnergySide side : EnergySide.VALUES) {
            EventData data = EventData.of("Action", "Toggle").append("Side", side.name());
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, sideId(side), data);
        }
    }

    private void updateSideButtons(
            UICommandBuilder update,
            EnergyNodeComponent node,
            World world,
            Vector3i blockPos) {
        for (EnergySide side : EnergySide.VALUES) {
            boolean connected = node.isSideConnected(side);
            String modeLabel = displayModeLabel(node.getSideMode(side));
            String text = connected
                    ? side.label() + ": " + modeLabel
                    : side.label() + ": " + modeLabel + " (" + NO_LINK_LABEL + ")";
            update.set(sideId(side) + ".Visible", true);
            update.set(sideId(side) + ".Text", text);
            update.set(sideIconId(side) + ".ItemId", getNeighborItemId(world, blockPos, side));
        }
    }

    private String sideId(EnergySide side) {
        return "#Side" + side.label();
    }

    private String sideIconId(EnergySide side) {
        return "#Side" + side.label() + "Icon";
    }

    private String displayModeLabel(EnergySideMode mode) {
        if (mode == null) {
            return "";
        }
        switch (mode) {
            case INPUT:
                return EnergySideMode.OUTPUT.label();
            case OUTPUT:
                return EnergySideMode.INPUT.label();
            default:
                return mode.label();
        }
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

        Item item = blockType.getItem();
        if (item == null || item.getId() == null) {
            return "";
        }

        return UiItemIds.safeItemId(item.getId());
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
}
