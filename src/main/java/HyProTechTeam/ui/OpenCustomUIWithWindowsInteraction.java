package HyProTechTeam.ui;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;

public class OpenCustomUIWithWindowsInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<OpenCustomUIWithWindowsInteraction> CODEC =
            BuilderCodec.builder(
                            OpenCustomUIWithWindowsInteraction.class,
                            OpenCustomUIWithWindowsInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .documentation("Opens a custom ui page with windows.")
                    .addField(
                            new KeyedCodec<>("Page", OpenCustomUIInteraction.PAGE_CODEC),
                            (interaction, supplier) -> interaction.customPageSupplier = supplier,
                            interaction -> interaction.customPageSupplier)
                    .build();

    private OpenCustomUIInteraction.CustomPageSupplier customPageSupplier;

    public OpenCustomUIWithWindowsInteraction() {
        super();
    }

    @Override
    protected void firstRun(
            InteractionType interactionType,
            InteractionContext context,
            CooldownHandler cooldownHandler) {
        Ref<EntityStore> playerEntityRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = commandBuffer.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        PageManager pageManager = player.getPageManager();
        if (pageManager.getCustomPage() != null) {
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (playerRef == null || customPageSupplier == null) {
            return;
        }

        CustomUIPage page = customPageSupplier.tryCreate(
                playerEntityRef, commandBuffer, playerRef, context);
        if (page == null) {
            return;
        }

        Vector3i targetPos = resolveTargetBlockPosition(context, commandBuffer);
        if (targetPos != null) {
            if (page instanceof FurnacePage) {
                ((FurnacePage) page).setBlockPosition(targetPos);
            } else if (page instanceof OreCrusherPage) {
                ((OreCrusherPage) page).setBlockPosition(targetPos);
            } else if (page instanceof AlloySmelterPage) {
                ((AlloySmelterPage) page).setBlockPosition(targetPos);
            }
        }

        Store<EntityStore> store = commandBuffer.getStore();
        if (page instanceof WindowlessPage) {
            pageManager.openCustomPage(playerEntityRef, store, page);
            return;
        }

        Window[] windows = null;
        if (page instanceof WindowProvider) {
            windows = ((WindowProvider) page).createWindows(playerEntityRef, store);
        }
        if (windows == null || windows.length == 0) {
            windows = createBenchWindows(context, commandBuffer, playerEntityRef);
        }

        if (windows != null && windows.length > 0) {
            Page basePage = resolveBasePage(windows);
            if (!pageManager.setPageWithWindows(
                    playerEntityRef,
                    store,
                    basePage,
                    true,
                    windows)) {
                pageManager.openCustomPage(playerEntityRef, store, page);
                return;
            }
        }
        pageManager.openCustomPage(playerEntityRef, store, page);
    }

    @SuppressWarnings("removal")
    private Vector3i resolveTargetBlockPosition(
            InteractionContext context,
            CommandBuffer<EntityStore> commandBuffer) {
        BlockPosition target = context.getTargetBlock();
        if (target == null) {
            return null;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        if (world == null) {
            return null;
        }

        BlockPosition baseBlock = world.getBaseBlock(target);
        if (baseBlock == null) {
            return null;
        }

        return new Vector3i(baseBlock.x, baseBlock.y, baseBlock.z);
    }

    private Window[] createBenchWindows(
            InteractionContext context,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> playerEntityRef) {
        BlockPosition target = context.getTargetBlock();
        if (target == null) {
            return null;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        if (world == null) {
            return null;
        }

        BlockPosition baseBlock = world.getBaseBlock(target);
        if (baseBlock == null) {
            return null;
        }
        int x = baseBlock.x;
        int y = baseBlock.y;
        int z = baseBlock.z;

        ProcessingBenchBlock bench = BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                x,
                y,
                z);
        if (bench == null) {
            return null;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || blockType.getBench() == null) {
            return null;
        }

        BenchBlock benchBlock = BlockModule.get().getComponent(
                BenchBlock.getComponentType(),
                world,
                x,
                y,
                z);

        if (!ensureBenchInitialized(bench, blockType)) {
            return null;
        }

        UUIDComponent uuidComponent =
                commandBuffer.getComponent(playerEntityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        UUID playerId = uuidComponent.getUuid();

        Map<UUID, BenchWindow> windows = benchBlock != null ? benchBlock.getWindows() : null;
        if (windows != null) {
            BenchWindow existing = windows.get(playerId);
            if (existing != null) {
                return new Window[] { existing };
            }
        }

        ProcessingBenchWindow window = new ProcessingBenchWindow(
                bench, benchBlock, null, x, y, z, 0, blockType);
        if (windows != null) {
            BenchWindow prior = windows.putIfAbsent(playerId, window);
            if (prior != null) {
                return new Window[] { prior };
            }
        }

        if (benchBlock != null) {
            bench.updateFuelValues(benchBlock.getWindows());
        }
        if (windows != null) {
            window.registerCloseEvent(event -> windows.remove(playerId, window));
        }
        return new Window[] { window };
    }

    private boolean ensureBenchInitialized(ProcessingBenchBlock bench, BlockType blockType) {
        if (bench == null || blockType == null || blockType.getBench() == null) {
            return false;
        }
        bench.initializeBenchConfig(blockType);
        return true;
    }

    private Page resolveBasePage(Window[] windows) {
        for (Window window : windows) {
            if (window instanceof ProcessingBenchWindow) {
                return Page.Bench;
            }
        }
        return Page.Inventory;
    }
}
