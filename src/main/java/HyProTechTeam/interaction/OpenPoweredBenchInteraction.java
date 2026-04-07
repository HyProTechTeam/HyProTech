package HyProTechTeam.interaction;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
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
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;

public class OpenPoweredBenchInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<OpenPoweredBenchInteraction> CODEC =
            BuilderCodec.builder(
                            OpenPoweredBenchInteraction.class,
                            OpenPoweredBenchInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .documentation("Opens the processing bench.")
                    .build();

    public OpenPoweredBenchInteraction() {
        super();
    }

    @Override
    protected void firstRun(
            InteractionType interactionType,
            InteractionContext context,
            CooldownHandler cooldownHandler) {
        if (context == null || context.getCommandBuffer() == null) {
            return;
        }

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
        if (playerRef == null) {
            return;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        if (world == null) {
            return;
        }

        Vector3i targetPos = resolveTargetBlockPosition(context, world);
        if (targetPos == null) {
            return;
        }

        Window[] windows = createBenchWindows(world, targetPos, commandBuffer, playerEntityRef);
        if (windows == null || windows.length == 0) {
            return;
        }

        Page basePage = resolveBasePage(windows);
        pageManager.setPageWithWindows(
                playerEntityRef,
                store,
                basePage,
                true,
                windows);
    }

    @SuppressWarnings("removal")
    private Vector3i resolveTargetBlockPosition(InteractionContext context, World world) {
        BlockPosition target = context.getTargetBlock();
        if (target == null || world == null) {
            return null;
        }

        BlockPosition baseBlock = world.getBaseBlock(target);
        if (baseBlock == null) {
            return null;
        }

        return new Vector3i(baseBlock.x, baseBlock.y, baseBlock.z);
    }

    private Window[] createBenchWindows(
            World world,
            Vector3i pos,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> playerEntityRef) {
        if (world == null || pos == null || commandBuffer == null || playerEntityRef == null) {
            return null;
        }

        BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null || blockType.getBench() == null) {
            return null;
        }

        ProcessingBenchBlock bench = BlockModule.get().getComponent(
                ProcessingBenchBlock.getComponentType(),
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ());

        if (bench == null) {
            return null;
        }

        BenchBlock benchBlock = BlockModule.get().getComponent(
                BenchBlock.getComponentType(),
                world,
                pos.getX(),
                pos.getY(),
                pos.getZ());

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
                bench, benchBlock, null,
                pos.getX(), pos.getY(), pos.getZ(), 0, blockType);
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
        if (windows == null) {
            return Page.Inventory;
        }
        for (Window window : windows) {
            if (window instanceof ProcessingBenchWindow) {
                return Page.Bench;
            }
        }
        return Page.Inventory;
    }
}
