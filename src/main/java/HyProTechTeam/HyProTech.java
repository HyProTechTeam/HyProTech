package HyProTechTeam;

import HyProTechTeam.changelog.ChangelogConfig;
import HyProTechTeam.changelog.ChangelogManager;
import HyProTechTeam.energy.BatteryUpgradeConfig;
import HyProTechTeam.energy.CableUpgradeConfig;
import HyProTechTeam.energy.EnergyNetworkSystem;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.SolarUpgradeConfig;
import HyProTechTeam.energy.WindUpgradeConfig;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageLookup;
import HyProTechTeam.item.ItemNetworkSystem;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.item.ItemStorageConfigChunk;
import HyProTechTeam.item.ItemStorageConfigComponent;
import HyProTechTeam.furnace.FurnaceConfig;
import HyProTechTeam.machine.MachineComponent;
import HyProTechTeam.machine.MachineRegistry;
import HyProTechTeam.machine.MachineSystem;
import HyProTechTeam.machine.QuarryAreaManager;
import HyProTechTeam.machine.AlloySmelterConfig;
import HyProTechTeam.machine.OreCrusherMachine;
import HyProTechTeam.machine.AlloySmelterMachine;
import HyProTechTeam.machine.QuarryMachine;
import HyProTechTeam.machine.OreCrusherConfig;
import HyProTechTeam.machine.QuarryConfig;
import HyProTechTeam.mac.MacConfigBridge;
import HyProTechTeam.sound.HyProTechSounds;
import HyProTechTeam.interaction.CableSideToolInteraction;
import HyProTechTeam.interaction.CableNetworkUpgradeInteraction;
import HyProTechTeam.ui.BatteryPage;
import HyProTechTeam.ui.CablePage;
import HyProTechTeam.ui.ItemCablePage;
import HyProTechTeam.ui.OreCrusherPage;
import HyProTechTeam.ui.AlloySmelterPage;
import HyProTechTeam.ui.QuarryPage;
import HyProTechTeam.ui.OpenCustomUIWithWindowsInteraction;
import HyProTechTeam.ui.PlayerUiSystem;
import HyProTechTeam.ui.SolarPage;
import HyProTechTeam.ui.WindPage;
import HyProTechTeam.interaction.OpenPoweredBenchInteraction;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class HyProTech extends JavaPlugin {
    private static final String TUTBOOKS_DOWNLOAD_URL =
            "https://github.com/YoofeCZ/HyProTechBook/releases/latest/download/HyProTechBook.zip";
    private static final String TUTBOOKS_MOD_ID = "HyProTech";
    private static final int WIND_TURBINE_BLOCK_HEIGHT = 5;
    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_BATTERY = CONFIG_DIR + "/battery-upgrades";
    private static final String CONFIG_SOLAR = CONFIG_DIR + "/solar-upgrades";
    private static final String CONFIG_WIND = CONFIG_DIR + "/wind-upgrades";
    private static final String CONFIG_CABLE = CONFIG_DIR + "/cable-upgrades";
    private static final String CONFIG_FURNACE = CONFIG_DIR + "/furnace";
    private static final String CONFIG_ORE_CRUSHER = CONFIG_DIR + "/ore-crusher";
    private static final String CONFIG_ALLOY_SMELTER = CONFIG_DIR + "/alloy-smelter";
    private static final String CONFIG_QUARRY = CONFIG_DIR + "/quarry";
    private static final String CONFIG_CHANGELOG_STATE = CONFIG_DIR + "/changelog-state";
    private final AtomicBoolean tutbooksDownloadQueued = new AtomicBoolean(false);
    private final Config<BatteryUpgradeConfig.ConfigData> batteryConfig;
    private final Config<SolarUpgradeConfig.ConfigData> solarConfig;
    private final Config<WindUpgradeConfig.ConfigData> windConfig;
    private final Config<CableUpgradeConfig.ConfigData> cableConfig;
    private final Config<FurnaceConfig.ConfigData> furnaceConfig;
    private final Config<OreCrusherConfig.ConfigData> oreCrusherConfig;
    private final Config<AlloySmelterConfig.ConfigData> alloySmelterConfig;
    private final Config<QuarryConfig.ConfigData> quarryConfig;
    private final Config<ChangelogConfig> changelogStateConfig;
    private ChangelogManager changelogManager;

    public HyProTech(@NonNullDecl JavaPluginInit init) {
        super(init);
        batteryConfig = withConfig(CONFIG_BATTERY, BatteryUpgradeConfig.ConfigData.CODEC);
        solarConfig = withConfig(CONFIG_SOLAR, SolarUpgradeConfig.ConfigData.CODEC);
        windConfig = withConfig(CONFIG_WIND, WindUpgradeConfig.ConfigData.CODEC);
        cableConfig = withConfig(CONFIG_CABLE, CableUpgradeConfig.ConfigData.CODEC);
        furnaceConfig = withConfig(CONFIG_FURNACE, FurnaceConfig.ConfigData.CODEC);
        oreCrusherConfig = withConfig(CONFIG_ORE_CRUSHER, OreCrusherConfig.ConfigData.CODEC);
        alloySmelterConfig = withConfig(CONFIG_ALLOY_SMELTER, AlloySmelterConfig.ConfigData.CODEC);
        quarryConfig = withConfig(CONFIG_QUARRY, QuarryConfig.ConfigData.CODEC);
        changelogStateConfig = withConfig(CONFIG_CHANGELOG_STATE, ChangelogConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();

        loadConfigs();
        changelogManager = new ChangelogManager(
                this,
                changelogStateConfig,
                resolveConfigPath(CONFIG_CHANGELOG_STATE));
        changelogManager.load();

        HyProTechSounds.registerDefaultsIfMissing();

        registerTutbooksDownloadOnPlayerJoin();
        registerChangelogPopupOnPlayerJoin();

        ComponentType<ChunkStore, EnergyNodeComponent> energyType =
                getChunkStoreRegistry().registerComponent(
                        EnergyNodeComponent.class,
                        HyProTechIds.ENERGY_COMPONENT_ID,
                        EnergyNodeComponent.CODEC);
        ComponentType<ChunkStore, ItemNodeComponent> itemType =
                getChunkStoreRegistry().registerComponent(
                        ItemNodeComponent.class,
                        HyProTechIds.ITEM_COMPONENT_ID,
                        ItemNodeComponent.CODEC);
        ComponentType<ChunkStore, ItemStorageConfigComponent> storageType =
                getChunkStoreRegistry().registerComponent(
                        ItemStorageConfigComponent.class,
                        HyProTechIds.ITEM_STORAGE_COMPONENT_ID,
                        ItemStorageConfigComponent.CODEC);
        ComponentType<ChunkStore, ItemStorageConfigChunk> storageChunkType =
                getChunkStoreRegistry().registerComponent(
                        ItemStorageConfigChunk.class,
                        HyProTechIds.ITEM_STORAGE_CHUNK_COMPONENT_ID,
                        ItemStorageConfigChunk.CODEC);
        ComponentType<ChunkStore, MachineComponent> machineType =
                getChunkStoreRegistry().registerComponent(
                        MachineComponent.class,
                        HyProTechIds.MACHINE_COMPONENT_ID,
                        MachineComponent.CODEC);
        HyProTechComponents.init(
                energyType,
                itemType,
                storageType,
                storageChunkType,
                machineType);

        MachineRegistry.register(new QuarryMachine());
        MachineRegistry.register(new OreCrusherMachine());
        MachineRegistry.register(new AlloySmelterMachine());

        EnergyStorageLookup.register((world, x, y, z) -> {
            if (world == null || y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
                return null;
            }
            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null) {
                return null;
            }
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            BlockComponentChunk components =
                    chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (components == null) {
                return null;
            }
            int localX = ChunkUtil.localCoordinate((long) x);
            int localZ = ChunkUtil.localCoordinate((long) z);
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);
            EnergyNodeComponent node = components.getComponent(blockIndex, energyType);
            if (node == null) {
                return null;
            }
            return node.getStorage(components::markNeedsSaving);
        });

        getChunkStoreRegistry().registerSystem(new EnergyNetworkSystem(energyType));
        getChunkStoreRegistry().registerSystem(new ItemNetworkSystem(itemType));
        getChunkStoreRegistry().registerSystem(new MachineSystem(machineType, energyType));
        getEntityStoreRegistry().registerSystem(new PlayerUiSystem(energyType, itemType, machineType));

        getCodecRegistry(Interaction.CODEC).register(
                HyProTechIds.OPEN_UI_INTERACTION_ID,
                OpenCustomUIInteraction.class,
                OpenCustomUIInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyProTechIds.OPEN_UI_WITH_WINDOWS_INTERACTION_ID,
                OpenCustomUIWithWindowsInteraction.class,
                OpenCustomUIWithWindowsInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyProTechIds.OPEN_POWERED_BENCH_INTERACTION_ID,
                OpenPoweredBenchInteraction.class,
                OpenPoweredBenchInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyProTechIds.TOGGLE_CABLE_SIDE_INTERACTION_ID,
                CableSideToolInteraction.class,
                CableSideToolInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyProTechIds.UPGRADE_CABLE_NETWORK_INTERACTION_ID,
                CableNetworkUpgradeInteraction.class,
                CableNetworkUpgradeInteraction.CODEC);

        getCommandRegistry().registerCommand(new HyProTechCommand(changelogManager));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.BATTERY_PAGE_ID,
                (playerRef, blockRef) -> new BatteryPage(playerRef, blockRef, energyType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.SOLAR_PAGE_ID,
                (playerRef, blockRef) -> new SolarPage(playerRef, blockRef, energyType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.WIND_PAGE_ID,
                (playerRef, blockRef) -> new WindPage(playerRef, blockRef, energyType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.CABLE_PAGE_ID,
                (playerRef, blockRef) -> new CablePage(playerRef, blockRef, energyType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.ITEM_CABLE_PAGE_ID,
                (playerRef, blockRef) -> new ItemCablePage(playerRef, blockRef, itemType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.ORE_CRUSHER_PAGE_ID,
                (playerRef, blockRef) -> new OreCrusherPage(playerRef, blockRef, energyType, machineType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.ALLOY_SMELTER_PAGE_ID,
                (playerRef, blockRef) -> new AlloySmelterPage(playerRef, blockRef, energyType, machineType));

        OpenCustomUIInteraction.registerBlockEntityCustomPage(
                this,
                OpenCustomUIInteraction.CustomPageSupplier.class,
                HyProTechIds.QUARRY_PAGE_ID,
                (playerRef, blockRef) -> new QuarryPage(playerRef, blockRef, energyType, machineType));

        getEventRegistry().registerGlobal(
                DamageBlockEvent.class,
                event -> {
                    if (event.isCancelled()) {
                        return;
                    }
                    BlockType blockType = event.getBlockType();
                    String blockId = blockType == null ? null : blockType.getId();
                    if (!UpgradePersistence.isUpgradeableBlockId(blockId)) {
                        return;
                    }
                    Vector3i pos = event.getTargetBlock();
                    if (pos == null) {
                        return;
                    }
                    World world = UpgradePersistence.findWorld(pos, blockType);
                    if (world == null) {
                        return;
                    }
                    if (isCableBlockId(blockId)) {
                        disableBlockTicking(world, pos);
                        Vector3i disablePos = new Vector3i(pos);
                        world.execute(() -> disableBlockTicking(world, disablePos));
                        return;
                    }
                    UpgradePersistence.cleanupInvalidChunkReferences(world, pos);
                    Vector3i cleanupPos = new Vector3i(pos);
                    world.execute(() -> UpgradePersistence.cleanupInvalidChunkReferences(world, cleanupPos));
                    List<ItemStack> drops = UpgradePersistence.snapshotBreakDrops(world, pos);
                    UpgradePersistence.cacheBreakDrops(world, pos, drops);
                });

        getEventRegistry().registerGlobal(
                BreakBlockEvent.class,
                event -> {
                    if (event.isCancelled()) {
                        return;
                    }
                    BlockType blockType = event.getBlockType();
                    String blockId = blockType == null ? null : blockType.getId();
                    Vector3i pos = event.getTargetBlock();
                    World world = null;
                    if (pos != null) {
                        world = UpgradePersistence.findWorld(pos, blockType);
                        if (world == null) {
                            world = UpgradePersistence.findWorld(pos, null);
                        }
                    }
                    if (pos != null && blockId != null) {
                        if (world != null) {
                            stopMachineSound(world, pos, blockId);
                        }
                    }
                    if (isCableBlockId(blockId)) {
                        if (world != null && pos != null) {
                            disableBlockTicking(world, pos);
                            UpgradePersistence.clearBreakDrops(world, pos);
                            schedulePostBreakReferenceCleanup(world, pos);
                            World disableWorld = world;
                            Vector3i disablePos = new Vector3i(pos);
                            disableWorld.execute(() -> disableBlockTicking(disableWorld, disablePos));
                        }
                        return;
                    }
                    if (pos != null && TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_QUARRY)) {
                        if (world != null) {
                            MachineComponent machine = getMachineAt(world, pos, machineType);
                            int width = machine == null ? 5 : machine.getAreaWidth();
                            int depth = machine == null ? 5 : machine.getAreaDepth();
                            QuarryAreaManager.hideArea(world, pos, width, depth);
                        }
                    }
                    if (!UpgradePersistence.isUpgradeableBlockId(blockId)) {
                        return;
                    }
                    if (pos == null) {
                        return;
                    }
                    if (world == null) {
                        world = UpgradePersistence.findWorld(pos, null);
                    }
                    if (world == null) {
                        return;
                    }
                    ItemStack drop = UpgradePersistence.buildDropStack(world, pos, blockType, blockId);
                    if (drop == null) {
                        return;
                    }
                    List<ItemStack> extraDrops = UpgradePersistence.consumeBreakDrops(world, pos);
                    if (extraDrops == null || extraDrops.isEmpty()) {
                        extraDrops = UpgradePersistence.snapshotBreakDrops(world, pos);
                    }
                    event.setCancelled(true);
                    UpgradePersistence.queueBreakAndDrop(world, pos, blockType, drop, extraDrops);
                    schedulePostBreakReferenceCleanup(world, pos);
                });

        getEventRegistry().registerGlobal(
                PlaceBlockEvent.class,
                event -> {
                    ItemStack stack = event.getItemInHand();
                    if (stack == null || ItemStack.isEmpty(stack)) {
                        return;
                    }
                    Vector3i pos = event.getTargetBlock();
                    if (pos == null) {
                        return;
                    }
                    World world = UpgradePersistence.findWorld(pos, null);
                    if (world == null) {
                        return;
                    }
                    String blockId = stack.getBlockKey();
                    if (isCableBlockId(blockId)) {
                        Vector3i disablePos = new Vector3i(pos);
                        world.execute(() -> {
                            disableBlockTicking(world, disablePos);
                            world.execute(() -> disableBlockTicking(world, disablePos));
                        });
                    }
                    if (!TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_WIND_TURBINE)) {
                        if (isBlockedByWindTurbine(world, pos)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    if (!UpgradePersistence.isUpgradeableBlockId(blockId)) {
                        return;
                    }
                    UpgradePersistence.storePending(world, pos, blockId, stack);
                });
                
        // Registration of upgrade IDs event
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, HyProTechTeam.ItemMigrationListener::onPlayerJoin);
        // Furnace custom UI removed; vanilla bench opens via interaction.
    }

    private void loadConfigs() {
        BatteryUpgradeConfig.applyConfig(loadConfig(CONFIG_BATTERY, batteryConfig, BatteryUpgradeConfig.ConfigData::new));
        SolarUpgradeConfig.applyConfig(loadConfig(CONFIG_SOLAR, solarConfig, SolarUpgradeConfig.ConfigData::new));
        WindUpgradeConfig.applyConfig(loadConfig(CONFIG_WIND, windConfig, WindUpgradeConfig.ConfigData::new));
        CableUpgradeConfig.applyConfig(loadConfig(CONFIG_CABLE, cableConfig, CableUpgradeConfig.ConfigData::new));
        FurnaceConfig.applyConfig(loadConfig(CONFIG_FURNACE, furnaceConfig, FurnaceConfig.ConfigData::new));
        OreCrusherConfig.applyConfig(loadConfig(CONFIG_ORE_CRUSHER, oreCrusherConfig, OreCrusherConfig.ConfigData::new));
        AlloySmelterConfig.applyConfig(loadConfig(
                CONFIG_ALLOY_SMELTER,
                alloySmelterConfig,
                AlloySmelterConfig.ConfigData::new));
        QuarryConfig.applyConfig(loadConfig(CONFIG_QUARRY, quarryConfig, QuarryConfig.ConfigData::new));
        MacConfigBridge.applyIfPresent(
                this,
                new MacConfigBridge.ConfigBundle(
                        batteryConfig,
                        solarConfig,
                        windConfig,
                        cableConfig,
                        furnaceConfig,
                        oreCrusherConfig,
                        alloySmelterConfig,
                        quarryConfig));
    }

    private <T> T loadConfig(String name, Config<T> config, Supplier<T> fallbackSupplier) {
        boolean exists = Files.exists(resolveConfigPath(name));
        T loaded;
        try {
            loaded = config.load().join();
        } catch (Exception ex) {
            getLogger().atWarning().log("[HyProTech] Failed to load config %s: %s", name, ex.getMessage());
            loaded = fallbackSupplier.get();
        }
        if (!exists) {
            config.save().exceptionally(ex -> {
                getLogger().atWarning().log("[HyProTech] Failed to save config %s: %s", name, ex.getMessage());
                return null;
            });
        }
        return loaded;
    }

    private Path resolveConfigPath(String name) {
        return getDataDirectory().resolve(name + ".json");
    }

    private void registerTutbooksDownloadOnPlayerJoin() {
        getEventRegistry().registerGlobal(
                PlayerReadyEvent.class,
                event -> {
                    if (!tutbooksDownloadQueued.compareAndSet(false, true)) {
                        return;
                    }
                    CommandManager commandManager = CommandManager.get();
                    if (commandManager == null) {
                        tutbooksDownloadQueued.set(false);
                        getLogger().atWarning().log("[HyProTech] CommandManager not available for TutBooks download.");
                        return;
                    }
                    String downloadCommand = String.format(
                            "tutbooks download %s %s",
                            TUTBOOKS_MOD_ID,
                            TUTBOOKS_DOWNLOAD_URL);
                    getLogger().atInfo().log("[HyProTech] Running: %s", downloadCommand);
                    commandManager
                            .handleCommand(ConsoleSender.INSTANCE, downloadCommand)
                            .thenCompose(ignored -> commandManager.handleCommand(ConsoleSender.INSTANCE, "tutbooks reload"))
                            .thenAccept(ignored -> getLogger().atInfo().log("[HyProTech] TutBooks download/reload complete."))
                            .exceptionally(ex -> {
                                tutbooksDownloadQueued.set(false);
                                getLogger().atWarning().log(
                                        "[HyProTech] TutBooks download/reload failed: %s",
                                        ex.getMessage());
                                return null;
                            });
                });
    }

    private void registerChangelogPopupOnPlayerJoin() {
        getEventRegistry().registerGlobal(
                PlayerReadyEvent.class,
                event -> {
                    if (changelogManager == null) {
                        return;
                    }
                    Player player = event.getPlayer();
                    Ref<EntityStore> playerEntityRef = event.getPlayerRef();
                    if (player == null || playerEntityRef == null) {
                        return;
                    }
                    Store<EntityStore> store = playerEntityRef.getStore();
                    if (store == null) {
                        return;
                    }
                    PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
                    if (playerRef == null) {
                        return;
                    }
                    changelogManager.openForPlayer(player, playerRef, false);
                });
    }

    private static MachineComponent getMachineAt(
            World world,
            Vector3i pos,
            ComponentType<ChunkStore, MachineComponent> machineType) {
        if (world == null || pos == null) {
            return null;
        }
        if (pos.getY() < ChunkUtil.MIN_Y || pos.getY() >= ChunkUtil.HEIGHT) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate((long) pos.getX());
        int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
        return blockComponents.getComponent(blockIndex, machineType);
    }

    private static boolean isBlockedByWindTurbine(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return false;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int minY = Math.max(ChunkUtil.MIN_Y, y - (WIND_TURBINE_BLOCK_HEIGHT - 1));
        for (int checkY = y; checkY >= minY; checkY--) {
            BlockType blockType = world.getBlockType(x, checkY, z);
            if (blockType == null) {
                continue;
            }
            String blockId = blockType.getId();
            if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_WIND_TURBINE)) {
                int baseY = checkY;
                if (y < baseY + WIND_TURBINE_BLOCK_HEIGHT) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void stopMachineSound(World world, Vector3i pos, String blockId) {
        if (world == null || pos == null || blockId == null) {
            return;
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_WIND_TURBINE)) {
            HyProTechSounds.stopSound(
                    world,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    HyProTechSounds.EVENT_WIND_TURBINE,
                    HyProTechSounds.FILE_WIND_TURBINE);
            return;
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_SOLAR_PANEL)) {
            HyProTechSounds.stopSound(
                    world,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    HyProTechSounds.EVENT_SOLAR_PANEL,
                    HyProTechSounds.FILE_SOLAR_PANEL);
            return;
        }
        if (TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ELECTRIC_FURNACE)) {
            HyProTechSounds.stopSound(
                    world,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    HyProTechSounds.EVENT_ELECTRIC_FURNACE,
                    HyProTechSounds.FILE_ELECTRIC_FURNACE);
            return;
        }
    }

    private static boolean isCableBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        return BlockIdUtil.isIdOrState(blockId, HyProTechIds.BLOCK_ENERGY_CABLE)
                || BlockIdUtil.isIdOrState(blockId, HyProTechIds.BLOCK_ITEM_CABLE);
    }

    private static void disableBlockTicking(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }
        try {
            accessor.setTicking(pos.getX(), pos.getY(), pos.getZ(), false);
        } catch (Exception ignored) {
            // Best-effort guard against stale ticking refs on immediate block break.
        }
    }

    private static void schedulePostBreakReferenceCleanup(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        Vector3i cleanupPos = new Vector3i(pos);
        world.execute(() -> {
            cleanupInvalidReferencesNear(world, cleanupPos);
            world.execute(() -> cleanupInvalidReferencesNear(world, cleanupPos));
        });
    }

    private static void cleanupInvalidReferencesNear(World world, Vector3i pos) {
        if (world == null || pos == null) {
            return;
        }
        int[] offsets = {-32, -16, 0, 16, 32};
        for (int xOffset : offsets) {
            for (int zOffset : offsets) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(
                        pos.getX() + xOffset,
                        pos.getZ() + zOffset);
                UpgradePersistence.cleanupInvalidChunkReferences(world, chunkIndex);
            }
        }
    }

}
