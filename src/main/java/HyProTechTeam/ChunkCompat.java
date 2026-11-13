package HyProTechTeam;

import com.hypixel.hytale.math.util.ChunkUtil;

/**
 * Compatibility helpers for the removed {@code ChunkUtil.indexBlockInColumn} and its inverse
 * accessors.
 *
 * <p>The pre-0.7.5 API addressed a block by its <em>absolute position inside the chunk column</em>:
 * {@code (worldY << 10) | (localZ << 5) | localX} (see {@link BlockComponentCompat#index}). Hytale
 * 0.7.5 only exposes {@code ChunkUtil.indexBlock}, which is a <em>section-local</em> index with the
 * world Y masked to 5 bits, so it cannot be used as a drop-in replacement: persisted per-column keys
 * (e.g. {@code ItemStorageConfigChunk}) and block-component lookups would collide across sections.
 * These helpers keep the old absolute-in-column layout.</p>
 */
public final class ChunkCompat {

    private ChunkCompat() {
    }

    /** Absolute-in-column block index: {@code (worldY << 10) | (localZ << 5) | localX}. */
    public static int indexBlockInColumn(int localX, int worldY, int localZ) {
        return (worldY << ChunkUtil.BITS2)
                | ((localZ & ChunkUtil.SIZE_MASK) << ChunkUtil.BITS)
                | (localX & ChunkUtil.SIZE_MASK);
    }

    /** Local X (0..31) of an absolute-in-column block index. */
    public static int xFromBlockInColumn(int index) {
        return index & ChunkUtil.SIZE_MASK;
    }

    /** Absolute world Y of an absolute-in-column block index. */
    public static int yFromBlockInColumn(int index) {
        return index >>> ChunkUtil.BITS2;
    }

    /** Local Z (0..31) of an absolute-in-column block index. */
    public static int zFromBlockInColumn(int index) {
        return (index >>> ChunkUtil.BITS) & ChunkUtil.SIZE_MASK;
    }
}
