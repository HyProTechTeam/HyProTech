package HyProTechTeam;

import com.hypixel.hytale.math.util.ChunkUtil;

/**
 * Compatibility helpers for {@code ChunkUtil} members that were renamed when Hytale moved to JOML
 * vectors / sectioned chunks. The formulas are identical (verified against the 0.7.5 bytecode):
 * <pre>
 *   indexBlockInColumn(x, y, z) == ChunkUtil.indexBlock(x, y, z)
 *   xFromBlockInColumn(i)       == ChunkUtil.xFromIndex(i)
 *   yFromBlockInColumn(i)       == ChunkUtil.yFromIndex(i)
 *   zFromBlockInColumn(i)       == ChunkUtil.zFromIndex(i)
 * </pre>
 */
public final class ChunkCompat {

    private ChunkCompat() {
    }

    public static int indexBlockInColumn(int x, int y, int z) {
        return ChunkUtil.indexBlock(x, y, z);
    }

    public static int xFromBlockInColumn(int index) {
        return ChunkUtil.xFromIndex(index);
    }

    public static int yFromBlockInColumn(int index) {
        return ChunkUtil.yFromIndex(index);
    }

    public static int zFromBlockInColumn(int index) {
        return ChunkUtil.zFromIndex(index);
    }
}
