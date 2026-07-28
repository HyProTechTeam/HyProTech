package HyProTechTeam.ui;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.EnergyUnits;
import HyProTechTeam.energy.SolarUpgradeConfig;
import HyProTechTeam.energy.SunlightUtil;
import HyProTechTeam.energy.WindUpgradeConfig;
import HyProTechTeam.energy.WindUtil;
import HyProTechTeam.energy.CableUpgradeConfig;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.machine.MachineComponent;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerUiSystem extends EntityTickingSystem<EntityStore> {
    private static final double LOOK_DISTANCE = 6.0;
    private static final long SOLAR_UPDATE_INTERVAL_MS = 1000L;
    private static final long FURNACE_UPDATE_INTERVAL_MS = 100L;
    private static final long FURNACE_WINDOW_GRACE_MS = 0L;
    private static final long FURNACE_HUD_MIN_OPEN_MS = 1000L;
    private static final long FURNACE_NODE_GRACE_MS = 500L;
    private static final long FURNACE_HUD_SHOW_GRACE_MS = 250L;
    private static final boolean ENABLE_FURNACE_HUD = false;

    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;
    private final ComponentType<ChunkStore, ItemNodeComponent> itemType;
    private final ComponentType<ChunkStore, MachineComponent> machineType;
    private final Map<UUID, DisplayState> displayStates = new HashMap<>();
    private final Map<UUID, FurnaceHudState> furnaceHudStates = new HashMap<>();

    public PlayerUiSystem(
            ComponentType<ChunkStore, EnergyNodeComponent> energyType,
            ComponentType<ChunkStore, ItemNodeComponent> itemType,
            ComponentType<ChunkStore, MachineComponent> machineType) {
        this.energyType = energyType;
        this.itemType = itemType;
        this.machineType = machineType;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(Player.getComponentType());
    }

    @Override
    public boolean isParallel(int total, int chunkSize) {
        return false;
    }

    @Override
    public void tick(
            float delta,
            int entityIndex,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(entityIndex, Player.getComponentType());
        if (player == null) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        EntityStore entityStore = store.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        if (world == null) {
            return;
        }

        updateSolarHud(entityRef, playerRef, world, store, commandBuffer);
        updateFurnaceHud(player, playerRef, world);
        updateCustomPage(player, playerRef, store, world);
    }

    private void updateSolarHud(
            Ref<EntityStore> entityRef,
            PlayerRef playerRef,
            World world,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        Vector3i target = TargetUtil.getTargetBlock(entityRef, LOOK_DISTANCE, commandBuffer);
        DisplayState state = getDisplayState(playerRef);

        if (target == null) {
            hideTitleIfNeeded(playerRef, state);
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(target.getX(), target.getZ());
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            hideTitleIfNeeded(playerRef, state);
            return;
        }

        EnergyNodeComponent node = getEnergyNodeAt(world, target.getX(), target.getY(), target.getZ());
        ItemNodeComponent itemNode = null;
        Vector3i effectiveTarget = target;
        if (node == null) {
            itemNode = getItemNodeAt(world, target.getX(), target.getY(), target.getZ());
        }
        if (node == null && itemNode == null && target.getY() > ChunkUtil.MIN_Y) {
            int belowY = target.getY() - 1;
            EnergyNodeComponent belowNode = getEnergyNodeAt(world, target.getX(), belowY, target.getZ());
            if (belowNode != null && belowNode.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
                node = belowNode;
                effectiveTarget = new Vector3i(target.getX(), belowY, target.getZ());
            }
        }
        if (node == null && itemNode == null) {
            hideTitleIfNeeded(playerRef, state);
            return;
        }

        String title;
        String subtitle;
        TargetKind targetKind;
        if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.SOLAR) {
            double sunlightFactor = 1.0;
            WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
            if (timeResource != null) {
                double baseFactor = timeResource.getSunlightFactor();
                sunlightFactor = SunlightUtil.adjustedSunlightFactor(world, timeResource, baseFactor);
            }
            if (!SunlightUtil.hasSkyAccess(world, target.getX(), target.getY(), target.getZ())) {
                sunlightFactor = 0.0;
            }
            int output = (int) Math.round(node.getGeneration() * sunlightFactor);
            int tier = SolarUpgradeConfig.clampTier(node.getSolarTier());
            title = SolarUpgradeConfig.getTierName(tier) + " Solar Panel";
            subtitle = "Output: " + EnergyUnits.formatWatts(output);
            targetKind = TargetKind.SOLAR;
        } else if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.WIND) {
            double windFactor = WindUtil.getWindFactor(world, target.getX(), target.getY(), target.getZ());
            int output = (int) Math.round(node.getGeneration() * windFactor);
            int tier = WindUpgradeConfig.clampTier(node.getWindTier());
            title = WindUpgradeConfig.getTierName(tier) + " Wind Turbine";
            subtitle = "Output: " + EnergyUnits.formatWatts(output);
            targetKind = TargetKind.WIND;
        } else if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            CableNetworkInfo info = getCableNetworkInfo(world, target);
            if (info == null) {
                hideTitleIfNeeded(playerRef, state);
                return;
            }
            String tierName = info.minTier == info.maxTier
                    ? CableUpgradeConfig.getTierName(info.minTier)
                    : "Mixed";
            title = info.cableCount > 1
                    ? tierName + " Cable (" + info.cableCount + "x)"
                    : tierName + " Cable";
            subtitle = "Stored: "
                    + EnergyUnits.formatEnergyWithCapacity(info.totalEnergy, info.totalCapacity)
                    + " | Max: " + info.maxTransfer + " J/s";
            targetKind = TargetKind.CABLE;
        } else if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.BATTERY) {
            title = "Battery";
            subtitle = "Energy: " + EnergyUnits.formatEnergyWithCapacity(node.getEnergy(), node.getCapacity());
            targetKind = TargetKind.BATTERY;
        } else if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
            int progressMax = node.getProgressMax();
            int progress = node.getProgress();
            int percent = progressMax > 0 ? (int) Math.round(100.0 * progress / progressMax) : 0;
            title = "Electric Furnace";
            subtitle = "Energy: " + EnergyUnits.formatEnergyWithCapacity(node.getEnergy(), node.getCapacity())
                    + " | Progress: " + percent + "%";
            targetKind = TargetKind.FURNACE;
        } else if (itemNode != null) {
            ItemCableNetworkInfo info = getItemCableNetworkInfo(world, target);
            if (info == null) {
                hideTitleIfNeeded(playerRef, state);
                return;
            }
            String tierName = info.minTier == info.maxTier
                    ? CableUpgradeConfig.getTierName(info.minTier)
                    : "Mixed";
            title = info.cableCount > 1
                    ? tierName + " Item Cable (" + info.cableCount + "x)"
                    : tierName + " Item Cable";
            subtitle = "Max: " + info.maxTransfer + " items/s";
            targetKind = TargetKind.ITEM_CABLE;
        } else {
            hideTitleIfNeeded(playerRef, state);
            return;
        }

        boolean targetChanged = state.lastTargetX != effectiveTarget.getX()
                || state.lastTargetY != effectiveTarget.getY()
                || state.lastTargetZ != effectiveTarget.getZ()
                || state.lastTargetKind != targetKind;
        if (targetChanged && state.visible) {
            EventTitleUtil.hideEventTitleFromPlayer(playerRef, 0.05f);
            state.visible = false;
        }

        boolean isFurnace = targetKind == TargetKind.FURNACE;
        long updateInterval = isFurnace ? FURNACE_UPDATE_INTERVAL_MS : SOLAR_UPDATE_INTERVAL_MS;
        boolean textChanged = !title.equals(state.lastTitle) || !subtitle.equals(state.lastSubtitle);

        long now = System.currentTimeMillis();
        if (!state.visible || textChanged || now - state.lastShownMs >= updateInterval) {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    Message.raw(title),
                    Message.raw(subtitle),
                    false);
            state.lastTitle = title;
            state.lastSubtitle = subtitle;
            state.lastShownMs = now;
            state.visible = true;
            state.lastTargetX = effectiveTarget.getX();
            state.lastTargetY = effectiveTarget.getY();
            state.lastTargetZ = effectiveTarget.getZ();
            state.lastTargetKind = targetKind;
        }
    }

    private DisplayState getDisplayState(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        DisplayState state = displayStates.get(playerId);
        if (state == null) {
            state = new DisplayState();
            displayStates.put(playerId, state);
        }
        return state;
    }

    private void hideTitleIfNeeded(PlayerRef playerRef, DisplayState state) {
        if (!state.visible) {
            return;
        }
        EventTitleUtil.hideEventTitleFromPlayer(playerRef, 0.1f);
        state.visible = false;
    }

    private void updateCustomPage(Player player, PlayerRef playerRef, Store<EntityStore> store, World world) {
        CustomUIPage page = player.getPageManager().getCustomPage();
        if (page == null) {
            return;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        try {
            if (page instanceof BatteryPage) {
                BatteryPage batteryPage = (BatteryPage) page;
                Vector3i pos = batteryPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = batteryPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.BATTERY) {
                    batteryPage.update(node);
                }
            } else if (page instanceof SolarPage) {
                SolarPage solarPage = (SolarPage) page;
                Vector3i pos = solarPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = solarPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.SOLAR) {
                    solarPage.update(node);
                }
            } else if (page instanceof WindPage) {
                WindPage windPage = (WindPage) page;
                Vector3i pos = windPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = windPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.WIND) {
                    windPage.update(node);
                }
            } else if (page instanceof CablePage) {
                CablePage cablePage = (CablePage) page;
                EnergyNodeComponent node = chunkStore.getComponent(cablePage.getBlockRef(), energyType);
                if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                    cablePage.update(node);
                }
            } else if (page instanceof ItemCablePage) {
                ItemCablePage itemCablePage = (ItemCablePage) page;
                ItemNodeComponent node = chunkStore.getComponent(itemCablePage.getBlockRef(), itemType);
                if (node != null) {
                    itemCablePage.update(node);
                }
            } else if (page instanceof ItemNetworkPriorityPage) {
                ItemNetworkPriorityPage priorityPage = (ItemNetworkPriorityPage) page;
                priorityPage.update();
            } else if (page instanceof FurnacePage) {
                FurnacePage furnacePage = (FurnacePage) page;
                EnergyNodeComponent node = furnacePage.ensureNode(world);
                if (node != null && node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE) {
                    furnacePage.update(node);
                }
            } else if (page instanceof OreCrusherPage) {
                OreCrusherPage oreCrusherPage = (OreCrusherPage) page;
                Vector3i pos = oreCrusherPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = oreCrusherPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null) {
                    MachineComponent machine = pos == null
                            ? null
                            : getMachineAt(world, pos.getX(), pos.getY(), pos.getZ());
                    if (machine == null) {
                        Ref<ChunkStore> ref = oreCrusherPage.resolveBlockRef(world);
                        machine = chunkStore.getComponent(ref, machineType);
                    }
                    oreCrusherPage.update(node, machine);
                }
            } else if (page instanceof AlloySmelterPage) {
                AlloySmelterPage alloySmelterPage = (AlloySmelterPage) page;
                Vector3i pos = alloySmelterPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = alloySmelterPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null) {
                    MachineComponent machine = pos == null
                            ? null
                            : getMachineAt(world, pos.getX(), pos.getY(), pos.getZ());
                    if (machine == null) {
                        Ref<ChunkStore> ref = alloySmelterPage.resolveBlockRef(world);
                        machine = chunkStore.getComponent(ref, machineType);
                    }
                    alloySmelterPage.update(node, machine);
                }
            } else if (page instanceof QuarryPage) {
                QuarryPage quarryPage = (QuarryPage) page;
                Vector3i pos = quarryPage.resolveBlockPosition(world);
                EnergyNodeComponent node = pos == null
                        ? null
                        : getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
                if (node == null) {
                    Ref<ChunkStore> ref = quarryPage.resolveBlockRef(world);
                    node = chunkStore.getComponent(ref, energyType);
                }
                if (node != null && EnergyNodeComponent.isMachineLike(node.getNodeType())) {
                    MachineComponent machine = pos == null
                            ? null
                            : getMachineAt(world, pos.getX(), pos.getY(), pos.getZ());
                    if (machine == null) {
                        Ref<ChunkStore> ref = quarryPage.resolveBlockRef(world);
                        machine = chunkStore.getComponent(ref, machineType);
                    }
                    quarryPage.update(node, machine);
                }
            }
        } catch (IllegalStateException e) {
            dismissCustomPage(player, playerRef, store);
        }
    }

    private void dismissCustomPage(Player player, PlayerRef playerRef, Store<EntityStore> store) {
        if (player == null || playerRef == null) {
            return;
        }
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return;
        }
        try {
            player.getPageManager().handleEvent(
                    playerEntityRef,
                    store,
                    new CustomPageEvent(CustomPageEventType.Dismiss, ""));
        } catch (Throwable ignored) {
        }
    }

    private void updateFurnaceHud(Player player, PlayerRef playerRef, World world) {
        FurnaceHudState state = getFurnaceHudState(playerRef);
        HudManager hudManager = player.getHudManager();
        if (!ENABLE_FURNACE_HUD) {
            if (hudManager != null && state.visible) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, hudManager.getCustomHud());
            }
            return;
        }
        if (hudManager == null) {
            if (state.visible) {
                state.visible = false;
                state.hud.resetCache();
            }
            return;
        }
        CustomUIHud activeHud = hudManager.getCustomHud();
        CustomUIPage openPage = player.getPageManager().getCustomPage();
        if (openPage instanceof FurnacePage) {
            hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            return;
        }
        ProcessingBenchWindow window = getOpenFurnaceWindow(player);
        long now = System.currentTimeMillis();
        if (window == null) {
            state.windowOpen = false;
            if (state.visible && now - state.lastWindowSeenMs > FURNACE_WINDOW_GRACE_MS) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            }
            return;
        }
        state.lastWindowSeenMs = now;
        if (!state.windowOpen) {
            state.windowOpen = true;
            state.windowOpenedMs = now;
        }
        if (now - state.windowOpenedMs < FURNACE_HUD_MIN_OPEN_MS) {
            if (state.visible) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            }
            return;
        }

        Vector3i basePos = resolveBenchBasePosition(world, window);
        if (basePos == null) {
            if (state.visible && now - state.lastWindowSeenMs > FURNACE_WINDOW_GRACE_MS) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            }
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(basePos.getX(), basePos.getZ());
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            if (state.visible && now - state.lastWindowSeenMs > FURNACE_WINDOW_GRACE_MS) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            }
            return;
        }

        EnergyNodeComponent node = getEnergyNodeAt(
                world, basePos.getX(), basePos.getY(), basePos.getZ());
        if ((node == null || node.getCapacity() <= 0) && basePos.getY() > ChunkUtil.MIN_Y) {
            EnergyNodeComponent belowNode = getEnergyNodeAt(
                    world, basePos.getX(), basePos.getY() - 1, basePos.getZ());
            if (belowNode != null) {
                node = belowNode;
            }
        }
        boolean validNode = node != null
                && node.getNodeType() == EnergyNodeComponent.NodeType.FURNACE
                && node.getCapacity() > 0;
        if (!validNode) {
            if (state.visible && now - state.lastValidNodeSeenMs > FURNACE_NODE_GRACE_MS) {
                hideFurnaceHudIfOwned(state, playerRef, hudManager, activeHud);
            }
            return;
        }
        state.lastValidNodeSeenMs = now;

        if (!canUseFurnaceHud(activeHud, state)) {
            state.visible = false;
            state.hud.resetCache();
            return;
        }

        if (!state.visible) {
            state.hud.resetCache();
            hudManager.setCustomHud(playerRef, state.hud);
            state.visible = true;
            state.lastHudShownMs = now;
            return;
        }

        if (now - state.lastHudShownMs < FURNACE_HUD_SHOW_GRACE_MS) {
            return;
        }

        CustomUIHud currentHud = hudManager.getCustomHud();
        if (currentHud != state.hud) {
            state.visible = false;
            state.hud.resetCache();
            return;
        }

        state.hud.update(node);
    }

    private FurnaceHudState getFurnaceHudState(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        FurnaceHudState state = furnaceHudStates.get(playerId);
        if (state == null) {
            state = new FurnaceHudState(new FurnaceHud(playerRef));
            furnaceHudStates.put(playerId, state);
        }
        return state;
    }

    private ProcessingBenchWindow getOpenFurnaceWindow(Player player) {
        for (Window window : player.getWindowManager().getWindows()) {
            if (!(window instanceof ProcessingBenchWindow)) {
                continue;
            }
            ProcessingBenchWindow benchWindow = (ProcessingBenchWindow) window;
            if (benchWindow.getBlockType() != null
                    && TieredIdUtil.isTieredId(
                            benchWindow.getBlockType().getId(),
                            HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
                return benchWindow;
            }
        }
        return null;
    }

    @SuppressWarnings("removal")
    private Vector3i resolveBenchBasePosition(World world, ProcessingBenchWindow window) {
        int x = window.getX();
        int y = window.getY();
        int z = window.getZ();
        BlockPosition baseBlock = world.getBaseBlock(new BlockPosition(x, y, z));
        if (baseBlock != null) {
            return new Vector3i(baseBlock.x, baseBlock.y, baseBlock.z);
        }
        return new Vector3i(x, y, z);
    }

    private static final class DisplayState {
        private String lastTitle = "";
        private String lastSubtitle = "";
        private long lastShownMs;
        private boolean visible;
        private int lastTargetX = Integer.MIN_VALUE;
        private int lastTargetY = Integer.MIN_VALUE;
        private int lastTargetZ = Integer.MIN_VALUE;
        private TargetKind lastTargetKind;
    }

    private static final class FurnaceHudState {
        private final FurnaceHud hud;
        private boolean visible;
        private long lastWindowSeenMs;
        private boolean windowOpen;
        private long windowOpenedMs;
        private long lastValidNodeSeenMs;
        private long lastHudShownMs;

        private FurnaceHudState(FurnaceHud hud) {
            this.hud = hud;
        }
    }

    private boolean canUseFurnaceHud(CustomUIHud activeHud, FurnaceHudState state) {
        return activeHud == null || activeHud == state.hud;
    }

    private void hideFurnaceHudIfOwned(
            FurnaceHudState state,
            PlayerRef playerRef,
            HudManager hudManager,
            CustomUIHud activeHud) {
        if (!state.visible) {
            return;
        }
        if (hudManager != null && activeHud == state.hud) {
            hudManager.resetHud(playerRef);
        }
        state.visible = false;
        state.hud.resetCache();
    }

    private CableNetworkInfo getCableNetworkInfo(World world, Vector3i target) {
        if (world == null || target == null) {
            return null;
        }
        CableNetworkInfo info = new CableNetworkInfo();
        Deque<Vector3i> queue = new ArrayDeque<>();
        Long2ObjectMap<IntOpenHashSet> visited = new Long2ObjectOpenHashMap<>();

        if (!markVisited(visited, target.getX(), target.getY(), target.getZ())) {
            return null;
        }
        queue.add(target);

        while (!queue.isEmpty()) {
            Vector3i pos = queue.removeFirst();
            EnergyNodeComponent node = getEnergyNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
            if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.CABLE) {
                continue;
            }

            if (info.cableCount == 0) {
                info.cableColor = node.getCableColor();
                info.maxTransfer = node.getMaxTransfer();
            } else {
                info.maxTransfer = Math.min(info.maxTransfer, node.getMaxTransfer());
            }
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            info.minTier = Math.min(info.minTier, tier);
            info.maxTier = Math.max(info.maxTier, tier);
            info.cableCount++;
            info.totalEnergy += node.getEnergy();
            info.totalCapacity += node.getCapacity();

            for (EnergySide side : EnergySide.VALUES) {
                if (!canCableConnect(node, side)) {
                    continue;
                }
                int nx = pos.getX() + side.dx();
                int ny = pos.getY() + side.dy();
                int nz = pos.getZ() + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }
                if (!markVisited(visited, nx, ny, nz)) {
                    continue;
                }

                EnergyNodeComponent neighbor = getEnergyNodeAt(world, nx, ny, nz);
                EnergySide neighborSide = side.opposite();
                if (neighbor != null
                        && neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                        && neighbor.getCableColor() == info.cableColor
                        && canCableConnect(neighbor, neighborSide)) {
                    queue.add(new Vector3i(nx, ny, nz));
                }
            }
        }

        return info.cableCount > 0 ? info : null;
    }

    private boolean canCableConnect(EnergyNodeComponent node, EnergySide side) {
        return node.allowsInput(side) || node.allowsOutput(side);
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

    private EnergyNodeComponent getEnergyNodeAt(World world, int x, int y, int z) {
        return getComponentAt(world, x, y, z, energyType);
    }

    private ItemNodeComponent getItemNodeAt(World world, int x, int y, int z) {
        return getComponentAt(world, x, y, z, itemType);
    }

    private MachineComponent getMachineAt(World world, int x, int y, int z) {
        return getComponentAt(world, x, y, z, machineType);
    }

    private <T extends Component<ChunkStore>> T getComponentAt(
            World world,
            int x,
            int y,
            int z,
            ComponentType<ChunkStore, T> type) {
        if (world == null) {
            return null;
        }
        if (type == null) {
            return null;
        }
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
        Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
        if (ref != null && !ref.isValid()) {
            blockComponents.removeEntityReference(blockIndex, ref);
            blockComponents.markNeedsSaving();
            return null;
        }
        Holder<ChunkStore> holder = ref == null ? blockComponents.getEntityHolder(blockIndex) : null;
        if (holder != null) {
            return holder.getComponent(type);
        }
        if (ref == null) {
            return null;
        }
        Store<ChunkStore> chunkStoreData = chunkStore.getStore();
        if (chunkStoreData == null) {
            return null;
        }
        try {
            return chunkStoreData.getComponent(ref, type);
        } catch (IllegalStateException ex) {
            blockComponents.removeEntityReference(blockIndex, ref);
            blockComponents.markNeedsSaving();
            return null;
        }
    }

    private ItemCableNetworkInfo getItemCableNetworkInfo(World world, Vector3i target) {
        if (world == null || target == null) {
            return null;
        }

        ItemCableNetworkInfo info = new ItemCableNetworkInfo();
        Deque<Vector3i> queue = new ArrayDeque<>();
        Long2ObjectMap<IntOpenHashSet> visited = new Long2ObjectOpenHashMap<>();

        if (!markVisited(visited, target.getX(), target.getY(), target.getZ())) {
            return null;
        }
        queue.add(target);

        while (!queue.isEmpty()) {
            Vector3i pos = queue.removeFirst();
            ItemNodeComponent node = getItemNodeAt(world, pos.getX(), pos.getY(), pos.getZ());
            if (node == null) {
                continue;
            }

            info.addCable(node);

            for (EnergySide side : EnergySide.VALUES) {
                int nx = pos.getX() + side.dx();
                int ny = pos.getY() + side.dy();
                int nz = pos.getZ() + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }
                if (!markVisited(visited, nx, ny, nz)) {
                    continue;
                }
                ItemNodeComponent neighbor = getItemNodeAt(world, nx, ny, nz);
                if (neighbor != null) {
                    queue.add(new Vector3i(nx, ny, nz));
                }
            }
        }

        return info.cableCount > 0 ? info : null;
    }

    private enum TargetKind {
        SOLAR,
        WIND,
        CABLE,
        BATTERY,
        FURNACE,
        ITEM_CABLE
    }

    private static final class CableNetworkInfo {
        private int cableCount;
        private int totalEnergy;
        private int totalCapacity;
        private int maxTransfer = Integer.MAX_VALUE;
        private int cableColor;
        private int minTier = Integer.MAX_VALUE;
        private int maxTier = Integer.MIN_VALUE;
    }

    private static final class ItemCableNetworkInfo {
        private int cableCount;
        private int maxTransfer = Integer.MAX_VALUE;
        private int minTier = Integer.MAX_VALUE;
        private int maxTier = Integer.MIN_VALUE;

        private void addCable(ItemNodeComponent node) {
            cableCount++;
            maxTransfer = Math.min(maxTransfer, node.getMaxTransfer());
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            minTier = Math.min(minTier, tier);
            maxTier = Math.max(maxTier, tier);
        }
    }
}
