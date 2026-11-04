package HyProTechTeam;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;

/**
 * 0.7.5 compatibility wrapper for per-block components.
 *
 * <p>Hytale replaced the chunk-wide, {@code int}-keyed {@code BlockComponentChunk} store with
 * per-section {@link BlockComponentSection}s keyed by a 15-bit section-local index
 * ({@code ChunkUtil.indexBlock}). This class re-creates the read/write operations HyProTech used to
 * call on {@code BlockComponentChunk} ({@code getComponent}, {@code storeEntityHolder},
 * {@code getEntityReference}, {@code markNeedsSaving}, ...) on top of the new storage.</p>
 *
 * <p>Read access goes through {@link SectionReader#getBlockComponentHolder(int, int, int)}, which
 * returns a <em>copy</em>; writing must therefore go back through the section (or through the
 * block's ECS reference when one is loaded) — {@link #put} handles both.</p>
 */
public final class BlockComponentCompat {

    private BlockComponentCompat() {
    }

    /** Read-only copy of the block's component holder, or {@code null} when the block has none. */
    public static Holder<ChunkStore> holder(World world, int x, int y, int z) {
        return world == null ? null : holder(world.getChunkStore(), x, y, z);
    }

    /** {@link #holder(World, int, int, int)} for callers that already hold the {@link ChunkStore}. */
    public static Holder<ChunkStore> holder(ChunkStore chunkStore, int x, int y, int z) {
        if (chunkStore == null || y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return null;
        }
        return new SectionReader(chunkStore).getBlockComponentHolder(x, y, z);
    }

    /** Reads a component attached to the given block, or {@code null}. */
    public static <T extends Component<ChunkStore>> T get(
            World world, int x, int y, int z, ComponentType<ChunkStore, T> type) {
        return get(world == null ? null : world.getChunkStore(), x, y, z, type);
    }

    /** {@link #get(World, int, int, int, ComponentType)} for callers holding a {@link ChunkStore}. */
    public static <T extends Component<ChunkStore>> T get(
            ChunkStore chunkStore, int x, int y, int z, ComponentType<ChunkStore, T> type) {
        if (type == null) {
            return null;
        }
        Holder<ChunkStore> holder = holder(chunkStore, x, y, z);
        return holder == null ? null : holder.getComponent(type);
    }

    /** Stores (adds or replaces) a component on the given block and marks it for saving. */
    public static <T extends Component<ChunkStore>> void put(
            World world, int x, int y, int z, ComponentType<ChunkStore, T> type, T value) {
        if (world == null || type == null || value == null) {
            return;
        }
        BlockComponentSection section = section(world, x, y, z);
        if (section == null) {
            return;
        }
        int index = ChunkUtil.indexBlock(x, y, z);
        Ref<ChunkStore> blockRef = section.getBlockReference(index);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        if (blockRef != null && store != null && blockRef.isValid()) {
            try {
                store.putComponent(blockRef, type, value);
                section.markBlockNeedsSaving(index);
                return;
            } catch (IllegalStateException ignored) {
                // Reference went stale; fall through to the holder path.
            }
        }
        Holder<ChunkStore> holder = section.getBlockHolder(index);
        if (holder == null) {
            holder = ChunkStore.REGISTRY.newHolder();
            holder.putComponent(type, value);
            section.addBlockHolder(index, holder);
        } else {
            holder.putComponent(type, value);
        }
        section.markBlockNeedsSaving(index);
    }

    /** Removes a component from the given block, returning {@code true} when one was present. */
    public static <T extends Component<ChunkStore>> boolean remove(
            World world, int x, int y, int z, ComponentType<ChunkStore, T> type) {
        if (world == null || type == null) {
            return false;
        }
        BlockComponentSection section = section(world, x, y, z);
        if (section == null) {
            return false;
        }
        int index = ChunkUtil.indexBlock(x, y, z);
        boolean removed = false;
        Ref<ChunkStore> blockRef = section.getBlockReference(index);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        if (blockRef != null && store != null && blockRef.isValid()) {
            try {
                removed = store.removeComponentIfExists(blockRef, type);
            } catch (IllegalStateException ignored) {
                // Reference went stale; the holder path below still clears any stored copy.
            }
        }
        Holder<ChunkStore> holder = section.getBlockHolder(index);
        if (holder != null) {
            removed = holder.tryRemoveComponent(type) || removed;
        }
        if (removed) {
            section.markBlockNeedsSaving(index);
        }
        return removed;
    }

    /** The block's ECS reference when the engine has it loaded as an entity, else {@code null}. */
    public static Ref<ChunkStore> blockReference(World world, int x, int y, int z) {
        BlockComponentSection section = section(world, x, y, z);
        if (section == null) {
            return null;
        }
        int index = ChunkUtil.indexBlock(x, y, z);
        Ref<ChunkStore> blockRef = section.getBlockReference(index);
        return blockRef != null && blockRef.isValid() ? blockRef : null;
    }

    /** Marks the block's stored components as needing to be persisted. */
    public static void markDirty(World world, int x, int y, int z) {
        BlockComponentSection section = section(world, x, y, z);
        if (section != null) {
            section.markBlockNeedsSaving(ChunkUtil.indexBlock(x, y, z));
        }
    }

    /**
     * Finds the world position of the block owning the given block-entity reference.
     *
     * <p>0.7.5 keeps no reverse {@code Ref -> block index} lookup (the old
     * {@code BlockComponentChunk.getEntityReferences()} map is gone), so this scans the loaded
     * chunks' sections with {@link #forEachBlock}. That is the only way to see both storage forms:
     * a block whose components are a section {@link Holder}, and a block whose components live on
     * its block-entity {@link Ref} (which is how HyProTech's own blocks are stored).</p>
     *
     * <p>TODO(0.7.5): replace with an engine reverse lookup if one is ever exposed; this scan is
     * O(loaded sections) and is intended for low-frequency callers such as UI page creation.</p>
     */
    public static Vector3i findBlockPosition(World world, Ref<ChunkStore> blockRef) {
        if (world == null || blockRef == null || !blockRef.isValid()) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return null;
        }
        int targetIndex = blockRef.getIndex();
        for (long chunkIndex : chunkStore.getChunkIndexes()) {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                continue;
            }
            int chunkX = ChunkUtil.xOfChunkIndex(chunkIndex);
            int chunkZ = ChunkUtil.zOfChunkIndex(chunkIndex);
            Vector3i[] found = new Vector3i[1];
            forEachBlock(store, chunkRef, (blockIndex, holder, ref) -> {
                if (found[0] != null || ref == null || ref.getIndex() != targetIndex) {
                    return;
                }
                found[0] = new Vector3i(
                        ChunkUtil.worldCoordFromLocalCoord(chunkX, localX(blockIndex)),
                        localY(blockIndex),
                        ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ(blockIndex)));
            });
            if (found[0] != null) {
                return found[0];
            }
        }
        return null;
    }

    /** The {@link BlockComponentSection} holding the given block, or {@code null}. */
    private static BlockComponentSection section(World world, int x, int y, int z) {
        if (world == null || y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return null;
        }
        Store<ChunkStore> store = chunkStore.getStore();
        if (store == null) {
            return null;
        }
        Ref<ChunkStore> sectionRef = new SectionReader(chunkStore).sectionAt(x, y, z);
        if (sectionRef == null) {
            return null;
        }
        return store.getComponent(sectionRef, BlockComponentSection.getComponentType());
    }

    // ---------------------------------------------------- column index helpers

    /** Shift of the world Y inside an absolute-in-column block index. */
    private static final int COLUMN_BITS = 10;

    /**
     * Absolute-in-column block index: {@code (worldY << 10) | (localZ << 5) | localX}.
     *
     * <p>This is the Feb-2026 {@code ChunkUtil.indexBlockInColumn(x, y, z)} layout (absolute world
     * Y) the mod's chunk-level code was written against. 0.7.5's {@code ChunkUtil.indexBlock} is a
     * section-local index, so code that keeps per-column indices uses this helper instead.</p>
     */
    public static int index(int localX, int worldY, int localZ) {
        return (worldY << COLUMN_BITS) | ((localZ & ChunkUtil.SIZE_MASK) << ChunkUtil.BITS)
                | (localX & ChunkUtil.SIZE_MASK);
    }

    /** Local X (0..31) of an absolute-in-column block index. */
    public static int localX(int blockIndex) {
        return blockIndex & ChunkUtil.SIZE_MASK;
    }

    /** Absolute world Y of an absolute-in-column block index. */
    public static int localY(int blockIndex) {
        return blockIndex >>> COLUMN_BITS;
    }

    /** Local Z (0..31) of an absolute-in-column block index. */
    public static int localZ(int blockIndex) {
        return (blockIndex >>> ChunkUtil.BITS) & ChunkUtil.SIZE_MASK;
    }

    private static int sectionIndex(int blockIndex) {
        return ChunkUtil.indexBlock(localX(blockIndex), localY(blockIndex), localZ(blockIndex));
    }

    private static int sectionY(int blockIndex) {
        return localY(blockIndex) >> ChunkUtil.BITS;
    }

    // ------------------------------------------------------- chunk enumeration

    /** Visitor used by {@link #forEachBlock}; exactly one of {@code holder}/{@code ref} is set. */
    public interface BlockVisitor {
        void visit(int blockIndex, Holder<ChunkStore> holder, Ref<ChunkStore> ref);
    }

    /**
     * Enumerates the blocks that have components in one loaded chunk column.
     *
     * <p>Replacement for the removed {@code BlockComponentChunk.getEntityHolders()} +
     * {@code getEntityReferences()} iteration: walks the chunk's {@link ChunkColumn} sections and
     * reports every holder entry and every block-entity reference, converting the section-local
     * index back to an absolute-in-column index ({@link #index(int, int, int)}).</p>
     */
    public static void forEachBlock(
            Store<ChunkStore> store, Ref<ChunkStore> chunkRef, BlockVisitor visitor) {
        if (store == null || chunkRef == null || !chunkRef.isValid() || visitor == null) {
            return;
        }
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) {
            return;
        }
        for (Ref<ChunkStore> sectionRef : column.getSections()) {
            if (sectionRef == null || !sectionRef.isValid()) {
                continue;
            }
            BlockComponentSection section =
                    store.getComponent(sectionRef, BlockComponentSection.getComponentType());
            if (section == null) {
                continue;
            }
            ChunkSection chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType());
            if (chunkSection == null) {
                continue;
            }

            Short2ObjectMap<Holder<ChunkStore>> holders = section.getBlockHolders();
            int holderCount = holders.size();
            if (holderCount > 0) {
                // Snapshot the entries: the visitor may add/remove components while it runs.
                short[] keys = new short[holderCount];
                Holder<ChunkStore>[] values = new Holder[holderCount];
                int index = 0;
                for (Short2ObjectMap.Entry<Holder<ChunkStore>> entry : holders.short2ObjectEntrySet()) {
                    keys[index] = entry.getShortKey();
                    values[index] = entry.getValue();
                    index++;
                }
                for (int i = 0; i < index; i++) {
                    visitor.visit(absoluteIndex(chunkSection, keys[i]), values[i], null);
                }
            }

            // 0.7.5 exposes no read-only accessor for the block references; takeBlockReferences()
            // is the only enumeration route. It copies the map and clears the section's, so put it
            // straight back with loadBlockReference(), which does not mark anything dirty.
            // TODO(0.7.5): replace with an engine reference iterator if one is ever exposed.
            Short2ObjectMap<Ref<ChunkStore>> references = section.takeBlockReferences();
            if (references == null) {
                continue;
            }
            for (Short2ObjectMap.Entry<Ref<ChunkStore>> entry : references.short2ObjectEntrySet()) {
                Ref<ChunkStore> blockRef = entry.getValue();
                if (blockRef != null && blockRef.isValid()) {
                    section.loadBlockReference(entry.getShortKey(), blockRef);
                }
            }
            for (Short2ObjectMap.Entry<Ref<ChunkStore>> entry : references.short2ObjectEntrySet()) {
                visitor.visit(absoluteIndex(chunkSection, entry.getShortKey()), null, entry.getValue());
            }
        }
    }

    private static int absoluteIndex(ChunkSection chunkSection, short sectionBlockIndex) {
        int localX = sectionBlockIndex & ChunkUtil.SIZE_MASK;
        int localZ = (sectionBlockIndex >>> ChunkUtil.BITS) & ChunkUtil.SIZE_MASK;
        int localY = (sectionBlockIndex >>> COLUMN_BITS) & ChunkUtil.SIZE_MASK;
        int worldY = chunkSection.getY() * ChunkUtil.SIZE + localY;
        return index(localX, worldY, localZ);
    }

    /**
     * Removes block references whose entity has become invalid (the block entity was unloaded or
     * removed while the section kept the reference), returning the number of cleaned entries.
     */
    public static int removeInvalidReferences(World world, long chunkIndex) {
        if (world == null) {
            return 0;
        }
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) {
            return 0;
        }
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (store == null || chunkRef == null || !chunkRef.isValid()) {
            return 0;
        }
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) {
            return 0;
        }
        int removed = 0;
        for (Ref<ChunkStore> sectionRef : column.getSections()) {
            if (sectionRef == null || !sectionRef.isValid()) {
                continue;
            }
            BlockComponentSection section =
                    store.getComponent(sectionRef, BlockComponentSection.getComponentType());
            if (section == null) {
                continue;
            }
            Short2ObjectMap<Ref<ChunkStore>> references = section.takeBlockReferences();
            if (references == null) {
                continue;
            }
            boolean changed = false;
            for (Short2ObjectMap.Entry<Ref<ChunkStore>> entry : references.short2ObjectEntrySet()) {
                Ref<ChunkStore> blockRef = entry.getValue();
                if (blockRef != null && blockRef.isValid()) {
                    section.loadBlockReference(entry.getShortKey(), blockRef);
                } else {
                    section.tombstoneBlock(entry.getShortKey());
                    removed++;
                    changed = true;
                }
            }
            if (changed) {
                section.markNeedsSaving();
            }
        }
        return removed;
    }

    // --------------------------------------------------------------- chunk view

    /**
     * Adapter around one loaded chunk column that re-creates the operations the mod used to call on
     * the removed {@code BlockComponentChunk}. Block indices passed to its methods are
     * absolute-in-column indices ({@link #index(int, int, int)}).
     */
    public static final class ChunkView {
        private final World world;
        private final ChunkStore chunkStore;
        private final Store<ChunkStore> store;
        private final Ref<ChunkStore> chunkRef;
        private final int chunkX;
        private final int chunkZ;
        private final long chunkIndex;

        private ChunkView(
                World world,
                ChunkStore chunkStore,
                Ref<ChunkStore> chunkRef,
                int chunkX,
                int chunkZ,
                long chunkIndex) {
            this.world = world;
            this.chunkStore = chunkStore;
            this.store = chunkStore.getStore();
            this.chunkRef = chunkRef;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkIndex = chunkIndex;
        }

        /** Returns a view for the given loaded chunk column, or {@code null} when it is not loaded. */
        public static ChunkView of(World world, long chunkIndex) {
            if (world == null) {
                return null;
            }
            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null || chunkStore.getStore() == null) {
                return null;
            }
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                return null;
            }
            return new ChunkView(
                    world,
                    chunkStore,
                    chunkRef,
                    ChunkUtil.xOfChunkIndex(chunkIndex),
                    ChunkUtil.zOfChunkIndex(chunkIndex),
                    chunkIndex);
        }

        /** Returns a view for the given loaded chunk column, or {@code null} when it is not loaded. */
        public static ChunkView of(World world, int chunkX, int chunkZ) {
            return of(world, ChunkUtil.indexChunk(chunkX, chunkZ));
        }

        public World getWorld() {
            return world;
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        public long getChunkIndex() {
            return chunkIndex;
        }

        private BlockComponentSection sectionAt(int sectionY) {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReference(chunkX, sectionY, chunkZ);
            if (sectionRef == null || !sectionRef.isValid()) {
                return null;
            }
            return store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        }

        private BlockComponentSection sectionFor(int blockIndex) {
            int worldY = localY(blockIndex);
            if (worldY < ChunkUtil.MIN_Y || worldY >= ChunkUtil.HEIGHT) {
                return null;
            }
            return sectionAt(sectionY(blockIndex));
        }

        /** Reads a block component (block entity first, then the section holder). */
        public <T extends Component<ChunkStore>> T getComponent(
                int blockIndex, ComponentType<ChunkStore, T> type) {
            if (type == null) {
                return null;
            }
            BlockComponentSection section = sectionFor(blockIndex);
            if (section == null) {
                return null;
            }
            int sectionIndex = sectionIndex(blockIndex);
            Ref<ChunkStore> blockRef = section.getBlockReference(sectionIndex);
            if (blockRef != null) {
                return blockRef.isValid() ? store.getComponent(blockRef, type) : null;
            }
            Holder<ChunkStore> holder = section.getBlockHolder(sectionIndex);
            return holder == null ? null : holder.getComponent(type);
        }

        /** The block's per-section holder, or {@code null} when a block entity owns its components. */
        public Holder<ChunkStore> getEntityHolder(int blockIndex) {
            BlockComponentSection section = sectionFor(blockIndex);
            return section == null ? null : section.getBlockHolder(sectionIndex(blockIndex));
        }

        /** The block's ECS reference, or {@code null} when it has no block entity. */
        public Ref<ChunkStore> getEntityReference(int blockIndex) {
            BlockComponentSection section = sectionFor(blockIndex);
            return section == null ? null : section.getBlockReference(sectionIndex(blockIndex));
        }

        public void removeEntityReference(int blockIndex, Ref<ChunkStore> ref) {
            BlockComponentSection section = sectionFor(blockIndex);
            if (section != null && ref != null) {
                section.removeBlockReference(sectionIndex(blockIndex), ref);
            }
        }

        /** Stores (replaces) the per-section holder for the block (old storeEntityHolder). */
        public void storeEntityHolder(int blockIndex, Holder<ChunkStore> holder) {
            BlockComponentSection section = sectionFor(blockIndex);
            if (section != null && holder != null) {
                int index = sectionIndex(blockIndex);
                section.storeBlockHolder(index, holder);
                section.markBlockNeedsSaving(index);
            }
        }

        public Holder<ChunkStore> removeEntityHolder(int blockIndex) {
            BlockComponentSection section = sectionFor(blockIndex);
            return section == null ? null : section.removeBlockHolder(sectionIndex(blockIndex));
        }

        public void addEntityReference(int blockIndex, Ref<ChunkStore> ref) {
            BlockComponentSection section = sectionFor(blockIndex);
            if (section != null && ref != null) {
                section.addBlockReference(sectionIndex(blockIndex), ref);
            }
        }

        /**
         * Writes a component for the given block: to the block entity when it exists, otherwise to
         * the per-section holder (creating one when the block has no block components yet).
         */
        public <T extends Component<ChunkStore>> boolean putComponent(
                int blockIndex, ComponentType<ChunkStore, T> type, T value) {
            if (type == null || value == null) {
                return false;
            }
            BlockComponentSection section = sectionFor(blockIndex);
            if (section == null) {
                return false;
            }
            int index = sectionIndex(blockIndex);
            Ref<ChunkStore> blockRef = section.getBlockReference(index);
            if (blockRef != null) {
                if (!blockRef.isValid()) {
                    section.removeBlockReference(index, blockRef);
                } else {
                    store.putComponent(blockRef, type, value);
                    section.markBlockNeedsSaving(index);
                    return true;
                }
            }
            Holder<ChunkStore> holder = section.getBlockHolder(index);
            if (holder == null) {
                holder = ChunkStore.REGISTRY.newHolder();
                holder.putComponent(type, value);
                section.addBlockHolder(index, holder);
            } else {
                holder.putComponent(type, value);
                section.markBlockNeedsSaving(index);
            }
            return true;
        }

        /** Drops every block component (holder and block-entity reference) stored for the block. */
        public boolean removeAllBlockComponents(int blockIndex) {
            BlockComponentSection section = sectionFor(blockIndex);
            if (section == null) {
                return false;
            }
            int index = sectionIndex(blockIndex);
            boolean changed = false;
            Ref<ChunkStore> blockRef = section.getBlockReference(index);
            if (blockRef != null) {
                section.removeBlockReference(index, blockRef);
                changed = true;
            }
            if (section.getBlockHolder(index) != null) {
                section.removeBlockHolder(index);
                changed = true;
            }
            return changed;
        }

        /** Marks the components of one block as needing to be persisted. */
        public void markNeedsSaving(int blockIndex) {
            BlockComponentSection section = sectionFor(blockIndex);
            if (section != null) {
                section.markBlockNeedsSaving(sectionIndex(blockIndex));
            }
        }

        /** Marks all block components of this chunk column as needing to be persisted. */
        public void markNeedsSaving() {
            ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
            if (column != null) {
                for (Ref<ChunkStore> sectionRef : column.getSections()) {
                    if (sectionRef == null || !sectionRef.isValid()) {
                        continue;
                    }
                    BlockComponentSection section =
                            store.getComponent(sectionRef, BlockComponentSection.getComponentType());
                    if (section != null) {
                        section.markAllBlocksNeedsSaving();
                    }
                }
                return;
            }
            WorldChunk worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType());
            if (worldChunk != null) {
                worldChunk.markNeedsSaving();
            }
        }
    }
}
