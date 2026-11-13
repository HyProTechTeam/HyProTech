package HyProTechTeam.interaction;

import HyProTechTeam.BlockComponentCompat;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import HyProTechTeam.HyProTechComponents;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.ui.CableUpgradePage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class CableNetworkUpgradeInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<CableNetworkUpgradeInteraction> CODEC =
            BuilderCodec.builder(
                            CableNetworkUpgradeInteraction.class,
                            CableNetworkUpgradeInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Opens the cable network upgrade UI.")
                    .build();

    public CableNetworkUpgradeInteraction() {
        super();
    }

    @Override
    protected void interactWithBlock(
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            InteractionType interactionType,
            InteractionContext context,
            com.hypixel.hytale.server.core.inventory.ItemStack itemStack,
            org.joml.Vector3i blockPos,
            CooldownHandler cooldownHandler) {
        if (world == null || context == null || commandBuffer == null) {
            return;
        }

        Ref<EntityStore> playerEntityRef = context.getEntity();
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

        Vector3i targetPos = resolveTargetBlockPosition(context, world);
        if (targetPos == null) {
            return;
        }

        CableUpgradePage.CableType cableType = resolveCableType(world, targetPos);
        if (cableType == null) {
            playerRef.sendMessage(Message.raw("Target is not a HyProTech cable."));
            return;
        }

        CableUpgradePage page = new CableUpgradePage(playerRef, targetPos, cableType);
        pageManager.openCustomPage(playerEntityRef, commandBuffer.getStore(), page);
    }

    @Override
    protected void simulateInteractWithBlock(
            InteractionType interactionType,
            InteractionContext context,
            com.hypixel.hytale.server.core.inventory.ItemStack itemStack,
            World world,
            org.joml.Vector3i blockPos) {
        // Server-side only, and simulation must never mutate the world.
    }

    @SuppressWarnings("removal")
    private Vector3i resolveTargetBlockPosition(InteractionContext context, World world) {
        BlockPosition target = context.getTargetBlock();
        if (target == null || world == null) {
            return null;
        }

        BlockPosition baseBlock = BlockAccessor.resolveBaseBlock(world, target);
        if (baseBlock == null) {
            return null;
        }

        return new Vector3i(baseBlock.x, baseBlock.y, baseBlock.z);
    }

    private CableUpgradePage.CableType resolveCableType(World world, Vector3i pos) {
        if (HyProTechComponents.ENERGY == null || HyProTechComponents.ITEM == null) {
            return null;
        }
        int y = pos.getY();
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return null;
        }
        EnergyNodeComponent energyNode =
                BlockComponentCompat.get(world, pos.getX(), y, pos.getZ(), HyProTechComponents.ENERGY);
        if (energyNode != null && energyNode.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
            return CableUpgradePage.CableType.ENERGY;
        }
        ItemNodeComponent itemNode =
                BlockComponentCompat.get(world, pos.getX(), y, pos.getZ(), HyProTechComponents.ITEM);
        if (itemNode != null) {
            return CableUpgradePage.CableType.ITEM;
        }
        return null;
    }
}
