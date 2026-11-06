package com.hypixel.hytale.server.core.universe.world.accessor;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Compatibility shim for the removed 2026.x {@code BlockAccessor} interface.
 *
 * <p>Hytale deleted this type and split it up: reads moved to {@link SectionReader} /
 * {@link BlockReader}, writes to {@link BlockOperations}, and {@code BlockAccessor.loaded(World, long)}
 * was removed in favour of component access through {@link ChunkStore}. The upstream HyProTech code
 * is written against the old interface, so this class re-implements the old surface on top of the
 * current API. Call sites use {@link #loaded(World, long)} where they previously called
 * {@code BlockAccessor.loaded(world, chunkIndex)}; the null-for-unloaded contract is preserved.</p>
 */
public final class BlockAccessor {

    private final World world;
    private final ChunkStore chunkStore;
    private final Ref<ChunkStore> chunkRef;
    private final SectionReader reader;

    private BlockAccessor(World world, ChunkStore chunkStore, Ref<ChunkStore> chunkRef) {
        this.world = world;
        this.chunkStore = chunkStore;
        this.chunkRef = chunkRef;
        this.reader = new SectionReader(chunkStore);
    }

    /** Equivalent of the removed {@code BlockAccessor.loaded(world, chunkIndex)}. */
    public static BlockAccessor loaded(World world, long chunkIndex) {
        if (world == null) {
            return null;
        }
        ChunkStore store = world.getChunkStore();
        if (store == null) {
            return null;
        }
        Ref<ChunkStore> ref = store.getChunkReference(chunkIndex);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return new BlockAccessor(world, store, ref);
    }

    public World getWorld() {
        return world;
    }

    public Ref<ChunkStore> getChunkRef() {
        return chunkRef;
    }

    // ---------------------------------------------------------------- reads

    /** Old {@code BlockAccessor.blockTypeAt(World, x, y, z)} replacement. */
    public static BlockType blockTypeAt(World world, int x, int y, int z) {
        if (world == null) {
            return BlockType.EMPTY;
        }
        int id = new SectionReader(world.getChunkStore()).getBlock(x, y, z);
        return byId(id);
    }

    /** Old {@code BlockAccessor.rotationIndexAt(World, x, y, z)} replacement. */
    public static int rotationIndexAt(World world, int x, int y, int z) {
        if (world == null) {
            return 0;
        }
        return new SectionReader(world.getChunkStore()).getRotationIndex(x, y, z);
    }

    /** Old {@code BlockAccessor.resolveBaseBlock(World, BlockPosition)} replacement. */
    public static com.hypixel.hytale.protocol.BlockPosition resolveBaseBlock(World world,
            com.hypixel.hytale.protocol.BlockPosition position) {
        if (world == null || position == null) {
            return position;
        }
        return BlockOperations.resolveBaseBlockPosition(world.getChunkStore(), position);
    }

    public BlockType getBlockType(int x, int y, int z) {
        return byId(reader.getBlock(x, y, z));
    }

    public int getBlockId(int x, int y, int z) {
        return reader.getBlock(x, y, z);
    }

    public int getRotationIndex(int x, int y, int z) {
        return reader.getRotationIndex(x, y, z);
    }

    public Holder<ChunkStore> getBlockComponentHolder(int x, int y, int z) {
        return reader.getBlockComponentHolder(x, y, z);
    }

    // --------------------------------------------------------------- writes

    public boolean setBlock(int x, int y, int z, BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        return setBlock(x, y, z, indexOf(blockType.getId()), blockType, 0, 0, 0);
    }

    /** Old {@code setBlock(x, y, z, blockId, rotationIndex)} convenience overload. */
    public boolean setBlock(int x, int y, int z, String blockId, int rotationIndex) {
        int index = indexOf(blockId);
        BlockType type = index == BlockType.EMPTY_ID
                ? BlockType.EMPTY
                : BlockType.getAssetMap().getAsset(index);
        if (type == null) {
            return false;
        }
        return setBlock(x, y, z, index, type, rotationIndex, 0, 0);
    }

    public boolean setBlock(int x, int y, int z, int blockIndex, BlockType blockType,
            int rotationIndex, int filler, int settings) {
        try {
            return BlockOperations.setBlock(chunkStore, chunkRef, x, y, z, blockIndex, blockType,
                    rotationIndex, filler, settings);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void setBlockInteractionState(int x, int y, int z, BlockType blockType, String state,
            boolean force) {
        try {
            BlockOperations.setBlockInteractionState(chunkStore, chunkRef, x, y, z, blockType, state,
                    force);
        } catch (RuntimeException ignored) {
            // best effort, matches the old interface's lenient behaviour
        }
    }

    /**
     * Best-effort port of the old per-block ticking toggle. The current API keeps ticking flags on
     * {@code BlockSection}; the only caller wraps this in a try/catch for queue clean-up, so a
     * no-op is safe here.
     */
    public void setTicking(int x, int y, int z, boolean ticking) {
        // TODO(0.7.5): 0.7.5 keeps the ticking queue on the section/tick systems, not on the block,
        // so there is no per-block toggle to write. All callers use this for queue clean-up only;
        // a no-op is safe, but a public API for it should replace this if one is exposed.
    }

    // -------------------------------------------------------------- helpers

    private static BlockType byId(int blockId) {
        if (blockId == BlockType.EMPTY_ID) {
            return BlockType.EMPTY;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        return type == null ? BlockType.EMPTY : type;
    }

    private static int indexOf(String blockId) {
        if (blockId == null) {
            return BlockType.EMPTY_ID;
        }
        int index = BlockType.getAssetMap().getIndex(blockId);
        return index == Integer.MIN_VALUE ? BlockType.EMPTY_ID : index;
    }
}
