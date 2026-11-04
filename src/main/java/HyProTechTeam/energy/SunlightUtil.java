package HyProTechTeam.energy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import java.time.LocalDateTime;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SunlightUtil {
    private static final double DAY_START_HOUR = 5.0;
    private static final double NIGHT_START_HOUR = 20.0;
    private static final double SUNSET_EXTENSION_HOURS = 1.0;
    private static final Map<World, TailState> TAILS = new IdentityHashMap<>();

    private SunlightUtil() {
    }

    public static double adjustedSunlightFactor(World world, WorldTimeResource timeResource, double baseFactor) {
        if (world == null || timeResource == null) {
            return baseFactor;
        }
        LocalDateTime time = timeResource.getGameDateTime();
        if (time == null) {
            return baseFactor;
        }
        int dayKey = dayKey(time);
        double hour = hourOfDay(time);

        synchronized (TAILS) {
            TailState state = TAILS.get(world);
            if (state == null) {
                state = new TailState();
                TAILS.put(world, state);
            }
            if (state.dayKey != dayKey) {
                state.dayKey = dayKey;
                state.lastNonZeroFactor = 0.0;
                state.sunsetStartHour = Double.NaN;
            }

            if (hour < DAY_START_HOUR || hour >= NIGHT_START_HOUR) {
                state.lastNonZeroFactor = 0.0;
                state.sunsetStartHour = Double.NaN;
                return 0.0;
            }

            if (baseFactor > 0.0) {
                state.lastNonZeroFactor = baseFactor;
                state.sunsetStartHour = Double.NaN;
                return baseFactor;
            }

            if (Double.isNaN(state.sunsetStartHour)) {
                state.sunsetStartHour = hour;
            }

            double end = state.sunsetStartHour + SUNSET_EXTENSION_HOURS;
            if (hour >= end || state.lastNonZeroFactor <= 0.0) {
                return 0.0;
            }

            double t = (end - hour) / (end - state.sunsetStartHour);
            double tail = state.lastNonZeroFactor * clamp01(t);
            return Math.max(baseFactor, tail);
        }
    }

    public static boolean hasSkyAccess(World world, int worldX, int worldY, int worldZ) {
        if (world == null) {
            return false;
        }
        if (worldY >= ChunkUtil.HEIGHT - 1) {
            return true;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
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
            if (id == null || !BlockType.EMPTY_KEY.equals(id)) {
                return false;
            }
        }
        return true;
    }

    private static int dayKey(LocalDateTime time) {
        return time.getYear() * 1000 + time.getDayOfYear();
    }

    private static double hourOfDay(LocalDateTime time) {
        return time.getHour()
                + (time.getMinute() / 60.0)
                + (time.getSecond() / 3600.0);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static final class TailState {
        private int dayKey = Integer.MIN_VALUE;
        private double lastNonZeroFactor;
        private double sunsetStartHour = Double.NaN;
    }
}
