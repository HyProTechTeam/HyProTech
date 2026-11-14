package HyProTechTeam.energy;

import HyProTechTeam.HyProTechIds;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

/** Determines whether a wind turbine has the required open sky around it. */
public final class WindUtil {
    private static final int SKY_CHECK_OFFSET_BLOCKS = 5;
    private static final int SKY_CLEARANCE_RADIUS = 3;

    private WindUtil() {
    }

    /** Returns full wind when the clearance check succeeds, otherwise zero. */
    public static double getWindFactor(World world, int worldX, int worldY, int worldZ) {
        if (world == null) {
            return 0.0;
        }
        if (!hasSkyClearanceRadius(world, worldX, worldY, worldZ)) {
            return 0.0;
        }
        return 1.0;
    }

    /** Checks sky access above a horizontal disk centered five blocks above the turbine. */
    private static boolean hasSkyClearanceRadius(World world, int worldX, int worldY, int worldZ) {
        int checkY = Math.min(ChunkUtil.HEIGHT - 1, worldY + SKY_CHECK_OFFSET_BLOCKS);
        int radiusSq = SKY_CLEARANCE_RADIUS * SKY_CLEARANCE_RADIUS;
        for (int dx = -SKY_CLEARANCE_RADIUS; dx <= SKY_CLEARANCE_RADIUS; dx++) {
            for (int dz = -SKY_CLEARANCE_RADIUS; dz <= SKY_CLEARANCE_RADIUS; dz++) {
                // Skip positions outside the circular clearance radius.
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                if (!hasSkyAccessIgnoringTurbine(world, worldX + dx, checkY, worldZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Scans upward in a loaded chunk, allowing empty blocks and turbine blocks. */
    private static boolean hasSkyAccessIgnoringTurbine(World world, int worldX, int worldY, int worldZ) {
        if (world == null) {
            return false;
        }
        if (worldY >= ChunkUtil.HEIGHT - 1) {
            return true;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return false;
        }
        for (int y = worldY + 1; y < ChunkUtil.HEIGHT; y++) {
            BlockType blockType = accessor.getBlockType(worldX, y, worldZ);
            if (blockType == null) {
                return false;
            }
            if (blockType == BlockType.EMPTY) {
                continue;
            }
            String id = blockType.getId();
            if (id == null) {
                return false;
            }
            if (isWindTurbineBlockId(id)) {
                continue;
            }
            if (!BlockType.EMPTY_KEY.equals(id)) {
                return false;
            }
        }
        return true;
    }

    

    /** Recognizes the turbine base ID and its variants when scanning for obstructions. */
    private static boolean isWindTurbineBlockId(String id) {
        if (id == null) {
            return false;
        }
        if (HyProTechIds.BLOCK_WIND_TURBINE.equals(id)) {
            return true;
        }
        if (id.startsWith(HyProTechIds.BLOCK_WIND_TURBINE)) {
            return true;
        }
        String lower = id.toLowerCase();
        return lower.contains("wind_turbine") || lower.contains("windturbine") || lower.contains("turbine");
    }
}
