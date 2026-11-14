package HyProTechTeam.interaction;

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
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Switches the targeted face of an item or energy cable between its two directional modes. */
public class CableSideToolInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<CableSideToolInteraction> CODEC =
            BuilderCodec.builder(
                            CableSideToolInteraction.class,
                            CableSideToolInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Toggles HyProTech cable side between input and output.")
                    .build();
    private static final boolean DEBUG = true;

    /** Creates the block interaction. */
    public CableSideToolInteraction() {
        super();
    }

    /** Resolves the clicked face and cable component, toggles it, and marks the chunk dirty. */
    @Override
    protected void interactWithBlock(
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            Vector3i blockPos,
            CooldownHandler cooldownHandler) {
        if (world == null || context == null || blockPos == null) {
            debug(commandBuffer, context, "CableTool: invalid context.");
            return;
        }

        // Prefer the interaction's block face, falling back to the raycast normal.
        EnergySide side = resolveSide(context);
        if (side == null) {
            debug(commandBuffer, context, "CableTool: missing side (no face/normal).");
            return;
        }

        int y = blockPos.getY();
        if (y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            debug(commandBuffer, context, "CableTool: Y out of bounds.");
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.getX(), blockPos.getZ());
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            debug(commandBuffer, context, "CableTool: chunk not loaded.");
            return;
        }

        BlockType blockType = accessor.getBlockType(blockPos.getX(), y, blockPos.getZ());
        String blockId = blockType == null ? "unknown" : blockType.getId();

        if (HyProTechComponents.ENERGY == null || HyProTechComponents.ITEM == null) {
            debug(commandBuffer, context, "CableTool: component types not initialized.");
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        BlockComponentChunk blockComponents =
                chunkStore.getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            debug(commandBuffer, context, "CableTool: missing BlockComponentChunk.");
            return;
        }

        int localX = ChunkUtil.localCoordinate((long) blockPos.getX());
        int localZ = ChunkUtil.localCoordinate((long) blockPos.getZ());
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);

        // Item cable components take precedence; otherwise check for an energy cable.
        boolean updated = false;
        ItemNodeComponent itemNode = blockComponents.getComponent(blockIndex, HyProTechComponents.ITEM);
        if (itemNode != null) {
            toggleItemSide(itemNode, side);
            updated = true;
        } else {
            EnergyNodeComponent energyNode = blockComponents.getComponent(blockIndex, HyProTechComponents.ENERGY);
            if (energyNode != null && energyNode.getNodeType() == EnergyNodeComponent.NodeType.CABLE) {
                toggleEnergySide(energyNode, side);
                updated = true;
            }
        }

        if (updated) {
            blockComponents.markNeedsSaving();
            debug(commandBuffer, context, "CableTool: toggled " + side.label() + " on " + blockId + ".");
        } else {
            debug(commandBuffer, context, "CableTool: not a HyProTech cable (" + blockId + ").");
        }
    }

    /** Client simulation intentionally performs no state changes. */
    @Override
    protected void simulateInteractWithBlock(
            InteractionType interactionType,
            InteractionContext context,
            ItemStack itemStack,
            World world,
            Vector3i blockPos) {
        // Server-side only.
    }

    /** Tries the server interaction state first, then the client state. */
    private EnergySide resolveSide(InteractionContext context) {
        EnergySide side = resolveSideFromState(context.getState());
        if (side != null) {
            return side;
        }
        return resolveSideFromState(context.getClientState());
    }

    /** Uses an explicit block face when available, otherwise the raycast normal. */
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

    /** Maps protocol block faces to the corresponding energy sides. */
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

    /** Chooses the axis with the largest absolute normal component and uses its sign. */
    private EnergySide resolveSideFromNormal(Vector3f normal) {
        if (normal == null) {
            return null;
        }

        float ax = Math.abs(normal.x);
        float ay = Math.abs(normal.y);
        float az = Math.abs(normal.z);

        if (ax >= ay && ax >= az) {
            return normal.x >= 0f ? EnergySide.EAST : EnergySide.WEST;
        }
        if (ay >= ax && ay >= az) {
            return normal.y >= 0f ? EnergySide.UP : EnergySide.DOWN;
        }
        return normal.z >= 0f ? EnergySide.SOUTH : EnergySide.NORTH;
    }

    /** Sends a debug message when debugging and the player context are available. */
    private void debug(CommandBuffer<EntityStore> commandBuffer, InteractionContext context, String message) {
        if (!DEBUG || commandBuffer == null || context == null) {
            return;
        }
        if (context.getEntity() == null) {
            return;
        }
        Player player = commandBuffer.getComponent(context.getEntity(), Player.getComponentType());
        if (player == null) {
            return;
        }
        player.sendMessage(Message.raw(message));
    }

    /** Switches OUTPUT to INPUT; all other modes become OUTPUT. */
    private void toggleEnergySide(EnergyNodeComponent node, EnergySide side) {
        EnergySideMode mode = node.getSideMode(side);
        if (mode == EnergySideMode.OUTPUT) {
            node.setSideMode(side, EnergySideMode.INPUT);
        } else {
            node.setSideMode(side, EnergySideMode.OUTPUT);
        }
    }

    /** Switches PUT to TAKE; all other modes become PUT. */
    private void toggleItemSide(ItemNodeComponent node, EnergySide side) {
        ItemMode mode = node.getSideMode(side);
        if (mode == ItemMode.PUT) {
            node.setSideMode(side, ItemMode.TAKE);
        } else {
            node.setSideMode(side, ItemMode.PUT);
        }
    }
}
