package HyProTechTeam.interaction;

import HyProTechTeam.BlockComponentCompat;
import HyProTechTeam.HyProTechComponents;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.EnergySideMode;
import HyProTechTeam.item.ItemMode;
import HyProTechTeam.item.ItemNodeComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3fc;

public class CableSideToolInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<CableSideToolInteraction> CODEC =
            BuilderCodec.builder(
                            CableSideToolInteraction.class,
                            CableSideToolInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Toggles HyProTech cable side between input and output.")
                    .build();
    private static final boolean DEBUG = true;

    public CableSideToolInteraction() {
        super();
    }

    @Override
    protected void interactWithBlock(
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            org.joml.Vector3i blockPos,
            CooldownHandler cooldownHandler) {
        if (world == null || context == null || blockPos == null) {
            debug(commandBuffer, context, "CableTool: invalid context.");
            return;
        }

        EnergySide side = resolveSide(context);
        if (side == null) {
            debug(commandBuffer, context, "CableTool: missing side (no face/normal).");
            return;
        }

        int blockX = blockPos.x;
        int y = blockPos.y;
        int blockZ = blockPos.z;
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            debug(commandBuffer, context, "CableTool: Y out of bounds.");
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
        if (accessor == null) {
            debug(commandBuffer, context, "CableTool: chunk not loaded.");
            return;
        }

        BlockType blockType = accessor.getBlockType(blockX, y, blockZ);
        String blockId = blockType == null ? "unknown" : blockType.getId();

        if (HyProTechComponents.ENERGY == null || HyProTechComponents.ITEM == null) {
            debug(commandBuffer, context, "CableTool: component types not initialized.");
            return;
        }

        boolean updated = false;
        ItemNodeComponent itemNode =
                BlockComponentCompat.get(world, blockX, y, blockZ, HyProTechComponents.ITEM);
        if (itemNode != null) {
            toggleItemSide(itemNode, side);
            BlockComponentCompat.put(world, blockX, y, blockZ, HyProTechComponents.ITEM, itemNode);
            updated = true;
        } else {
            EnergyNodeComponent energyNode =
                    BlockComponentCompat.get(world, blockX, y, blockZ, HyProTechComponents.ENERGY);
            if (energyNode != null && energyNode.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                toggleEnergySide(energyNode, side);
                BlockComponentCompat.put(world, blockX, y, blockZ, HyProTechComponents.ENERGY, energyNode);
                updated = true;
            }
        }

        if (updated) {
            debug(commandBuffer, context, "CableTool: toggled " + side.label() + " on " + blockId + ".");
        } else {
            debug(commandBuffer, context, "CableTool: not a HyProTech cable (" + blockId + ").");
        }
    }

    @Override
    protected void simulateInteractWithBlock(
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            World world,
            org.joml.Vector3i blockPos) {
        // Server-side only, and simulation must never mutate the world.
    }

    private EnergySide resolveSide(InteractionContext context) {
        EnergySide side = resolveSideFromState(context.getState());
        if (side != null) {
            return side;
        }
        return resolveSideFromState(context.getClientState());
    }

    private EnergySide resolveSideFromState(InteractionSyncData state) {
        if (state == null) {
            return null;
        }
        BlockFace face = state.blockFace;
        EnergySide fromFace = resolveSideFromFace(face);
        if (fromFace != null) {
            return fromFace;
        }
        return resolveSideFromNormal(state.raycastNormal);
    }

    private EnergySide resolveSideFromFace(BlockFace face) {
        if (face == null || face == BlockFace.None) {
            return null;
        }

        switch (face) {
            case East:
                return EnergySide.EAST;
            case West:
                return EnergySide.WEST;
            case Up:
                return EnergySide.UP;
            case Down:
                return EnergySide.DOWN;
            case South:
                return EnergySide.SOUTH;
            case North:
                return EnergySide.NORTH;
            default:
                return null;
        }
    }

    private EnergySide resolveSideFromNormal(Vector3fc normal) {
        if (normal == null) {
            return null;
        }

        float ax = Math.abs(normal.x());
        float ay = Math.abs(normal.y());
        float az = Math.abs(normal.z());

        if (ax >= ay && ax >= az) {
            return normal.x() >= 0f ? EnergySide.EAST : EnergySide.WEST;
        }
        if (ay >= ax && ay >= az) {
            return normal.y() >= 0f ? EnergySide.UP : EnergySide.DOWN;
        }
        return normal.z() >= 0f ? EnergySide.SOUTH : EnergySide.NORTH;
    }

    private void debug(CommandBuffer<EntityStore> commandBuffer, InteractionContext context, String message) {
        if (!DEBUG || commandBuffer == null || context == null) {
            return;
        }
        if (context.getEntity() == null) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(context.getEntity(), PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        playerRef.sendMessage(Message.raw(message));
    }

    private void toggleEnergySide(EnergyNodeComponent node, EnergySide side) {
        EnergySideMode mode = node.getSideMode(side);
        if (mode == EnergySideMode.OUTPUT) {
            node.setSideMode(side, EnergySideMode.INPUT);
        } else {
            node.setSideMode(side, EnergySideMode.OUTPUT);
        }
    }

    private void toggleItemSide(ItemNodeComponent node, EnergySide side) {
        ItemMode mode = node.getSideMode(side);
        if (mode == ItemMode.PUT) {
            node.setSideMode(side, ItemMode.TAKE);
        } else {
            node.setSideMode(side, ItemMode.PUT);
        }
    }
}
