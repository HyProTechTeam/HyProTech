package HyProTechTeam.machine;

import HyProTechTeam.BlockComponentCompat;
import HyProTechTeam.ChunkCompat;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import HyProTechTeam.BlockComponentCompat;
import com.hypixel.hytale.component.Store;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class QuarryAreaManager {
    private static final String BORDER_STATE_LINE = "Border_Line";
    private static final String BORDER_STATE_LINE_BACK = "Border_Line_Back";
    private static final String BORDER_STATE_LINE_BACK_FRONT = "Border_Line_BackFront";
    private static final String BORDER_STATE_LINE_BACK_UP = "Border_Line_BackUp";
    private static final String BORDER_STATE_LINE_SIDE = "Border_Line_Side";
    private static final String BORDER_STATE_LINE_SIDE_LEFT = "Border_Line_Side_Left";
    private static final String BORDER_STATE_LINE_SIDE_UP_LEFT = "Border_Line_Side_Up_Left";
    private static final String BORDER_STATE_LINE_SIDE_UP_RIGHT = "Border_Line_Side_Up_Right";
    private static final String BORDER_STATE_CORNER_LEFT_FRONT = "Border_Corner_LeftFront";
    private static final String BORDER_STATE_CORNER_LEFT_BACK = "Border_Corner_LeftBack";
    private static final String BORDER_STATE_CORNER_RIGHT_FRONT = "Border_Corner_RightFront";
    private static final String BORDER_STATE_CORNER_RIGHT_BACK = "Border_Corner_RightBack";
    private static final String BORDER_STATE_CORNER_MIDDLE_LEFT_FRONT = "Border_Corner_Middle_LeftFront";
    private static final String BORDER_STATE_CORNER_MIDDLE_LEFT_BACK = "Border_Corner_Middle_LeftBack";
    private static final String BORDER_STATE_CORNER_MIDDLE_RIGHT_FRONT = "Border_Corner_Middle_RightFront";
    private static final String BORDER_STATE_CORNER_MIDDLE_RIGHT_BACK = "Border_Corner_Middle_RightBack";
    private static final String BORDER_STATE_CORNER_UP = "Border_Corner_Up";
    private static final String BORDER_STATE_CORNER_UP_LEFT_FRONT = "Border_Corner_Up_LeftFront";
    private static final String BORDER_STATE_CORNER_UP_LEFT_BACK = "Border_Corner_Up_LeftBack";
    private static final String BORDER_STATE_CORNER_UP_RIGHT_FRONT = "Border_Corner_Up_RightFront";
    private static final String BORDER_STATE_CORNER_UP_RIGHT_BACK = "Border_Corner_Up_RightBack";
    private static final int BORDER_HEIGHT_BLOCKS = 3;
    private static final int BORDER_OFFSET_Y = -1;
    private static final int BORDER_OFFSET_Z = -2;
    private static final int BORDER_ROTATION_INDEX = RotationTuple.NONE_INDEX;
    private static final String BORDER_PARTICLE_ID = "Server/Particles/Weapon/LaserRifle/Laser_Impact";
    private static final int TORCH_SCAN_RADIUS = 64;
    private static final long TORCH_CACHE_MS = 1000L;
    private static final Map<World, Map<String, BlockSnapshot>> STORED_BLOCKS = new WeakHashMap<>();
    private static final Map<World, Map<String, BorderRecord>> BORDER_RECORDS = new WeakHashMap<>();
    private static final Map<World, Map<String, BorderRecord>> BORDER_RECORDS_BY_XZ = new WeakHashMap<>();
    private static final Map<World, Map<String, TorchBoundsCache>> TORCH_BOUNDS_CACHE = new WeakHashMap<>();

    private QuarryAreaManager() {
    }

    public static void showArea(World world, Vector3i origin, int width, int depth) {
        // Disabled: border placement causes unload/load issues.
    }

    public static void hideArea(World world, Vector3i origin, int width, int depth) {
        clearGhostArea(world, origin, width, depth);
    }

    public static void updateArea(
            World world,
            Vector3i origin,
            int oldWidth,
            int oldDepth,
            int newWidth,
            int newDepth) {
        // Disabled: border placement causes unload/load issues.
    }

    public static void spawnBorderParticles(World world, Vector3i origin, int width, int depth) {
        if (world == null || origin == null) {
            return;
        }
        world.execute(() -> {
            TorchBounds torchBounds = getTorchBoundsInternal(world, origin);
            if (torchBounds != null) {
                spawnBorderParticlesInternal(world, torchBounds);
            } else {
                spawnBorderParticlesInternal(world, origin, width, depth);
            }
        });
    }

    public static TorchBounds getTorchBounds(World world, Vector3i origin) {
        if (world == null || origin == null) {
            return null;
        }
        return getTorchBoundsInternal(world, origin);
    }

    private static void clearGhostArea(World world, Vector3i origin, int width, int depth) {
        if (world == null || origin == null) {
            return;
        }
        world.execute(() -> clearGhostAreaInternal(world, origin, width, depth));
    }

    private static void clearGhostAreaInternal(World world, Vector3i origin, int width, int depth) {
        int w = Math.max(1, width);
        int d = Math.max(1, depth);
        int baseY = origin.getY() + 1 + BORDER_OFFSET_Y;
        if (baseY < ChunkUtil.MIN_Y || baseY >= ChunkUtil.HEIGHT) {
            return;
        }
        int topY = Math.min(baseY + BORDER_HEIGHT_BLOCKS - 1, ChunkUtil.HEIGHT - 1);
        Bounds bounds = computeBounds(w, d);
        if (BORDER_OFFSET_Z != 0) {
            bounds = new Bounds(bounds.minX, bounds.maxX, bounds.minZ + BORDER_OFFSET_Z, bounds.maxZ + BORDER_OFFSET_Z);
        }
        clearBorderAreaSimple(world, origin, w, d, baseY, topY);
    }

    private static TorchBounds getTorchBoundsInternal(World world, Vector3i origin) {
        Map<String, TorchBoundsCache> cache = getTorchBoundsCache(world);
        String key = originKeyXZ(origin);
        TorchBoundsCache cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.lastUpdatedMs < TORCH_CACHE_MS) {
            return cached.bounds;
        }
        TorchBounds bounds = computeTorchBounds(world, origin);
        cache.put(key, new TorchBoundsCache(bounds, now));
        return bounds;
    }

    private static TorchBounds computeTorchBounds(World world, Vector3i origin) {
        int originX = origin.getX();
        int originY = origin.getY();
        int originZ = origin.getZ();
        return computeTorchBoundsAnyHeight(world, originX, originY, originZ);
    }

    private static TorchBounds computeTorchBoundsAnyHeight(
            World world,
            int originX,
            int originY,
            int originZ) {
        Map<String, Vector3i> torches = new HashMap<>();

        int startX = originX - TORCH_SCAN_RADIUS;
        int endX = originX + TORCH_SCAN_RADIUS;
        int startZ = originZ - TORCH_SCAN_RADIUS;
        int endZ = originZ + TORCH_SCAN_RADIUS;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
                if (accessor == null) {
                    continue;
                }
                int foundY = Integer.MIN_VALUE;
                for (int y = ChunkUtil.MIN_Y; y < ChunkUtil.HEIGHT; y++) {
                    BlockType blockType = accessor.getBlockType(x, y, z);
                    if (isBorderTorch(blockType)) {
                        foundY = y;
                        break;
                    }
                }
                if (foundY == Integer.MIN_VALUE) {
                    continue;
                }
                torches.put(blockKey(x, foundY, z), new Vector3i(x, foundY, z));
            }
        }

        if (torches.size() < 2) {
            return null;
        }

        TorchBounds best = null;
        int bestCornerCount = -1;
        long bestArea = Long.MAX_VALUE;
        Vector3i[] torchArray = torches.values().toArray(new Vector3i[0]);
        for (int i = 0; i < torchArray.length; i++) {
            Vector3i a = torchArray[i];
            for (int j = i + 1; j < torchArray.length; j++) {
                Vector3i b = torchArray[j];
                if (a.getX() == b.getX() || a.getZ() == b.getZ()) {
                    continue;
                }
                int minX = Math.min(a.getX(), b.getX());
                int maxX = Math.max(a.getX(), b.getX());
                int minZ = Math.min(a.getZ(), b.getZ());
                int maxZ = Math.max(a.getZ(), b.getZ());
                if (originX < minX || originX > maxX || originZ < minZ || originZ > maxZ) {
                    continue;
                }
                boolean hasMinMin = hasTorchAtXZ(torches, minX, minZ);
                boolean hasMinMax = hasTorchAtXZ(torches, minX, maxZ);
                boolean hasMaxMin = hasTorchAtXZ(torches, maxX, minZ);
                boolean hasMaxMax = hasTorchAtXZ(torches, maxX, maxZ);
                int cornerCount = 0;
                if (hasMinMin) {
                    cornerCount++;
                }
                if (hasMinMax) {
                    cornerCount++;
                }
                if (hasMaxMin) {
                    cornerCount++;
                }
                if (hasMaxMax) {
                    cornerCount++;
                }
                boolean hasOppositeCorners = (hasMinMin && hasMaxMax) || (hasMinMax && hasMaxMin);
                if (!hasOppositeCorners && cornerCount < 4) {
                    continue;
                }
                long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
                if (cornerCount > bestCornerCount || (cornerCount == bestCornerCount && area < bestArea)) {
                    bestCornerCount = cornerCount;
                    bestArea = area;
                    best = new TorchBounds(minX, maxX, minZ, maxZ, originY);
                }
            }
        }

        return best;
    }

    private static Map<String, TorchBoundsCache> getTorchBoundsCache(World world) {
        Map<String, TorchBoundsCache> cache = TORCH_BOUNDS_CACHE.get(world);
        if (cache == null) {
            cache = new HashMap<>();
            TORCH_BOUNDS_CACHE.put(world, cache);
        }
        return cache;
    }

    private static boolean hasTorchAtXZ(Map<String, Vector3i> torches, int x, int z) {
        for (Vector3i pos : torches.values()) {
            if (pos.getX() == x && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTorchAtX(Map<String, Vector3i> torches, int x) {
        for (Vector3i pos : torches.values()) {
            if (pos.getX() == x) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTorchAtZ(Map<String, Vector3i> torches, int z) {
        for (Vector3i pos : torches.values()) {
            if (pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private static void spawnBorderParticlesInternal(World world, Vector3i origin, int width, int depth) {
        int w = Math.max(1, width);
        int d = Math.max(1, depth);
        int baseY = origin.getY() + 1 + BORDER_OFFSET_Y;
        if (baseY < ChunkUtil.MIN_Y || baseY >= ChunkUtil.HEIGHT) {
            return;
        }

        Rotation yaw = getBlockYaw(world, origin);
        Bounds bounds = computeBounds(w, d);
        if (BORDER_OFFSET_Z != 0) {
            bounds = new Bounds(bounds.minX, bounds.maxX, bounds.minZ + BORDER_OFFSET_Z, bounds.maxZ + BORDER_OFFSET_Z);
        }
        Bounds stateBounds = rotateBounds(bounds, yaw);

        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }

        int step = 2;
        for (int x = bounds.minX; x <= bounds.maxX; x += step) {
            spawnAtEdge(world, origin, stateBounds, x, bounds.minZ, baseY, yaw, store);
            if (bounds.maxZ != bounds.minZ) {
                spawnAtEdge(world, origin, stateBounds, x, bounds.maxZ, baseY, yaw, store);
            }
        }
        for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
            if (z == bounds.minZ || z == bounds.maxZ) {
                continue;
            }
            spawnAtEdge(world, origin, stateBounds, bounds.minX, z, baseY, yaw, store);
            if (bounds.maxX != bounds.minX) {
                spawnAtEdge(world, origin, stateBounds, bounds.maxX, z, baseY, yaw, store);
            }
        }
    }

    private static void spawnBorderParticlesInternal(World world, TorchBounds bounds) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        int y = bounds.y + 1 + BORDER_OFFSET_Y;
        int step = 2;
        for (int x = bounds.minX; x <= bounds.maxX; x += step) {
            spawnParticleAt(x, y, bounds.minZ, store);
            if (bounds.maxZ != bounds.minZ) {
                spawnParticleAt(x, y, bounds.maxZ, store);
            }
        }
        for (int z = bounds.minZ; z <= bounds.maxZ; z += step) {
            if (z == bounds.minZ || z == bounds.maxZ) {
                continue;
            }
            spawnParticleAt(bounds.minX, y, z, store);
            if (bounds.maxX != bounds.minX) {
                spawnParticleAt(bounds.maxX, y, z, store);
            }
        }
    }

    private static void spawnParticleAt(int x, int y, int z, Store<EntityStore> store) {
        Vector3d pos = new Vector3d(x + 0.5, y + 0.6, z + 0.5);
        ParticleUtil.spawnParticleEffect(BORDER_PARTICLE_ID, pos, store);
    }

    private static void spawnAtEdge(
            World world,
            Vector3i origin,
            Bounds stateBounds,
            int localX,
            int localZ,
            int y,
            Rotation yaw,
            Store<EntityStore> store) {
        Vector3i offset = rotateLocalOffset(localX, localZ, yaw);
        int worldX = origin.getX() + offset.getX();
        int worldZ = origin.getZ() + offset.getZ();
        spawnParticleAt(worldX, y, worldZ, store);
    }

    private static boolean placeBorderBlock(
            World world,
            BlockAccessor accessor,
            int x,
            int y,
            int z,
            String stateName) {
        BlockType current = accessor.getBlockType(x, y, z);
        if (isQuarry(current)) {
            return false;
        }
        if (!isBorder(current) && !isEmpty(current)) {
            return false;
        }
        if (!isBorder(current) && hasBlockComponents(world, x, y, z)) {
            return false;
        }
        if (isBorder(current)) {
            accessor.setBlockInteractionState(x, y, z, current, stateName, false);
            return true;
        }
        if (!isBorder(current)) {
            storeSnapshot(world, x, y, z, current);
        }

        accessor.setBlock(x, y, z, HyProTechIds.BLOCK_QUARRY_BORDER, BORDER_ROTATION_INDEX);
        BlockType borderType = accessor.getBlockType(x, y, z);
        if (isBorder(borderType)) {
            accessor.setBlockInteractionState(x, y, z, borderType, stateName, false);
            return true;
        }
        return false;
    }

    private static String lineState(
            Bounds bounds,
            int x,
            int z,
            boolean hasWidth,
            boolean hasDepth,
            boolean isTop) {
        if (!hasWidth && hasDepth) {
            return isTop ? BORDER_STATE_LINE_SIDE_UP_RIGHT : BORDER_STATE_LINE_SIDE;
        }
        if (!hasDepth && hasWidth) {
            return isTop ? BORDER_STATE_LINE_BACK_FRONT : BORDER_STATE_LINE;
        }
        if (!hasWidth && !hasDepth) {
            return BORDER_STATE_LINE;
        }
        if (x == bounds.minX) {
            return isTop ? BORDER_STATE_LINE_SIDE_UP_LEFT : BORDER_STATE_LINE_SIDE_LEFT;
        }
        if (x == bounds.maxX) {
            return isTop ? BORDER_STATE_LINE_SIDE_UP_RIGHT : BORDER_STATE_LINE_SIDE;
        }
        if (z == bounds.maxZ) {
            return isTop ? BORDER_STATE_LINE_BACK_UP : BORDER_STATE_LINE_BACK;
        }
        return isTop ? BORDER_STATE_LINE_BACK_FRONT : BORDER_STATE_LINE;
    }

    private static String cornerState(Bounds bounds, int x, int z) {
        if (x == bounds.minX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_LEFT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_RIGHT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.maxZ) {
            return BORDER_STATE_CORNER_RIGHT_BACK;
        }
        return BORDER_STATE_CORNER_LEFT_BACK;
    }

    private static String cornerUpState(Bounds bounds, int x, int z) {
        if (x == bounds.minX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_UP_LEFT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_UP_RIGHT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.maxZ) {
            return BORDER_STATE_CORNER_UP_RIGHT_BACK;
        }
        return BORDER_STATE_CORNER_UP_LEFT_BACK;
    }

    private static String cornerMiddleState(Bounds bounds, int x, int z) {
        if (x == bounds.minX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_MIDDLE_LEFT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.minZ) {
            return BORDER_STATE_CORNER_MIDDLE_RIGHT_FRONT;
        }
        if (x == bounds.maxX && z == bounds.maxZ) {
            return BORDER_STATE_CORNER_MIDDLE_RIGHT_BACK;
        }
        return BORDER_STATE_CORNER_MIDDLE_LEFT_BACK;
    }

    private static boolean isEmpty(BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return true;
        }
        String id = blockType.getId();
        return id == null || id.isEmpty() || BlockType.EMPTY_KEY.equals(id);
    }

    private static boolean isBorder(BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        if (id == null) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_QUARRY_BORDER;
        return id.equalsIgnoreCase(baseId)
                || id.regionMatches(true, 0, baseId, 0, baseId.length())
                || containsIgnoreCase(id, baseId);
    }

    private static boolean isBorderTorch(BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        if (id == null) {
            return false;
        }
        String baseId = HyProTechIds.BLOCK_BORDER_TORCH;
        return id.equalsIgnoreCase(baseId)
                || id.regionMatches(true, 0, baseId, 0, baseId.length())
                || containsIgnoreCase(id, baseId);
    }

    private static boolean isQuarry(BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        return id != null && TieredIdUtil.isTieredId(id, HyProTechIds.BLOCK_QUARRY);
    }

    private static Rotation getBlockYaw(World world, Vector3i origin) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(origin.getX(), origin.getZ());
        if (BlockAccessor.loaded(world, chunkIndex) == null) {
            return Rotation.None;
        }

        RotationTuple rotation = RotationTuple.get(
                BlockAccessor.rotationIndexAt(world, origin.getX(), origin.getY(), origin.getZ()));
        if (rotation == null || rotation.yaw() == null) {
            return Rotation.None;
        }

        return rotation.yaw();
    }

    private static Vector3i rotateLocalOffset(int localX, int localZ, Rotation yaw) {
        if (yaw == Rotation.Ninety) {
            return new Vector3i(localZ, 0, -localX);
        }
        if (yaw == Rotation.OneEighty) {
            return new Vector3i(-localX, 0, -localZ);
        }
        if (yaw == Rotation.TwoSeventy) {
            return new Vector3i(-localZ, 0, localX);
        }
        return new Vector3i(localX, 0, localZ);
    }

    private static Bounds rotateBounds(Bounds bounds, Rotation yaw) {
        if (yaw == Rotation.Ninety) {
            return new Bounds(
                    bounds.minZ,
                    bounds.maxZ,
                    -bounds.maxX,
                    -bounds.minX);
        }
        if (yaw == Rotation.OneEighty) {
            return new Bounds(
                    -bounds.maxX,
                    -bounds.minX,
                    -bounds.maxZ,
                    -bounds.minZ);
        }
        if (yaw == Rotation.TwoSeventy) {
            return new Bounds(
                    -bounds.maxZ,
                    -bounds.minZ,
                    bounds.minX,
                    bounds.maxX);
        }
        return bounds;
    }

    private static void clearBorderArea(World world, Vector3i origin, int width, int depth, int baseY, int topY) {
        Bounds bounds = computeBounds(width, depth);
        if (BORDER_OFFSET_Z != 0) {
            bounds = new Bounds(bounds.minX, bounds.maxX, bounds.minZ + BORDER_OFFSET_Z, bounds.maxZ + BORDER_OFFSET_Z);
        }
        int maxX = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
        int maxZ = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
        int radius = Math.max(maxX, maxZ) + Math.abs(BORDER_OFFSET_Z) + 2;
        int minX = origin.getX() - radius;
        int maxXWorld = origin.getX() + radius;
        int minZ = origin.getZ() - radius;
        int maxZWorld = origin.getZ() + radius;
        Map<String, BlockSnapshot> snapshots = getSnapshotMap(world);

        for (int x = minX; x <= maxXWorld; x++) {
            for (int z = minZ; z <= maxZWorld; z++) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
                if (accessor == null) {
                    continue;
                }
                for (int y = baseY; y <= topY; y++) {
                    String key = blockKey(x, y, z);
                    BlockType current = accessor.getBlockType(x, y, z);
                    if (isBorder(current)) {
                        restoreOrClear(world, accessor, x, y, z);
                    } else if (snapshots.containsKey(key)) {
                        snapshots.remove(key);
                    }
                }
            }
        }
    }

    private static boolean clearBorderFromRecord(World world, Vector3i origin) {
        BorderRecord record = consumeBorderRecord(world, origin);
        if (record == null) {
            return false;
        }
        Map<String, BlockSnapshot> snapshots = getSnapshotMap(world);
        for (Vector3i pos : record.positions.values()) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
            if (accessor == null) {
                continue;
            }
            BlockType current = accessor.getBlockType(pos.getX(), pos.getY(), pos.getZ());
            if (isBorder(current)) {
                restoreOrClear(world, accessor, pos.getX(), pos.getY(), pos.getZ());
            } else {
                snapshots.remove(blockKey(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return true;
    }

    private static void storeSnapshot(World world, int x, int y, int z, BlockType current) {
        if (isEmpty(current)) {
            return;
        }
        Map<String, BlockSnapshot> snapshots = getSnapshotMap(world);
        String key = blockKey(x, y, z);
        if (snapshots.containsKey(key)) {
            return;
        }
        int rotationIndex = BlockAccessor.rotationIndexAt(world, x, y, z);
        snapshots.put(key, new BlockSnapshot(current.getId(), rotationIndex));
    }

    private static void restoreOrClear(World world, BlockAccessor accessor, int x, int y, int z) {
        Map<String, BlockSnapshot> snapshots = getSnapshotMap(world);
        String key = blockKey(x, y, z);
        BlockSnapshot snapshot = snapshots.remove(key);
        if (snapshot != null && snapshot.blockId != null && !snapshot.blockId.isEmpty()) {
            setBlockWithRotation(accessor, x, y, z, snapshot.blockId, snapshot.rotationIndex);
        } else {
            accessor.setBlock(x, y, z, BlockType.EMPTY);
        }
    }

    private static void clearBorderAreaSimple(
            World world,
            Vector3i origin,
            int width,
            int depth,
            int baseY,
            int topY) {
        Bounds bounds = computeBounds(width, depth);
        if (BORDER_OFFSET_Z != 0) {
            bounds = new Bounds(bounds.minX, bounds.maxX, bounds.minZ + BORDER_OFFSET_Z, bounds.maxZ + BORDER_OFFSET_Z);
        }
        int maxX = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
        int maxZ = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
        int radius = Math.max(maxX, maxZ) + Math.abs(BORDER_OFFSET_Z) + 2;
        int minX = origin.getX() - radius;
        int maxXWorld = origin.getX() + radius;
        int minZ = origin.getZ() - radius;
        int maxZWorld = origin.getZ() + radius;

        for (int x = minX; x <= maxXWorld; x++) {
            for (int z = minZ; z <= maxZWorld; z++) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
                BlockAccessor accessor = BlockAccessor.loaded(world, chunkIndex);
                if (accessor == null) {
                    continue;
                }
                for (int y = baseY; y <= topY; y++) {
                    BlockType current = accessor.getBlockType(x, y, z);
                    if (isBorder(current) && !hasBlockComponents(world, x, y, z)) {
                        accessor.setBlock(x, y, z, BlockType.EMPTY);
                    }
                }
            }
        }
    }

    private static boolean hasBlockComponents(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockComponentCompat.ChunkView components =
                BlockComponentCompat.ChunkView.of(world, chunkIndex);
        if (components == null) {
            return false;
        }
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = BlockComponentCompat.index(localX, y, localZ);
        return components.getEntityHolder(blockIndex) != null;
    }

    private static Map<String, BlockSnapshot> getSnapshotMap(World world) {
        Map<String, BlockSnapshot> snapshots = STORED_BLOCKS.get(world);
        if (snapshots == null) {
            snapshots = new HashMap<>();
            STORED_BLOCKS.put(world, snapshots);
        }
        return snapshots;
    }

    private static void setBlockWithRotation(
            BlockAccessor accessor,
            int x,
            int y,
            int z,
            String blockId,
            int rotationIndex) {
        if (accessor == null || blockId == null || blockId.isEmpty()) {
            return;
        }
        int blockIndex = BlockType.getAssetMap().getIndex(blockId);
        if (blockIndex == Integer.MIN_VALUE) {
            return;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockIndex);
        if (blockType == null) {
            return;
        }
        accessor.setBlock(x, y, z, blockIndex, blockType, rotationIndex, 0, 0);
    }

    private static void startBorderRecord(World world, Vector3i origin) {
        Map<String, BorderRecord> records = getBorderRecordMap(world);
        BorderRecord record = new BorderRecord();
        String key = originKey(origin);
        records.put(key, record);
        getBorderRecordMapByXZ(world).put(originKeyXZ(origin), record);
    }

    private static void recordBorderPosition(World world, Vector3i origin, int x, int y, int z) {
        BorderRecord record = getBorderRecord(world, origin);
        if (record == null) {
            return;
        }
        record.positions.put(blockKey(x, y, z), new Vector3i(x, y, z));
    }

    private static BorderRecord consumeBorderRecord(World world, Vector3i origin) {
        Map<String, BorderRecord> records = getBorderRecordMap(world);
        BorderRecord record = records.remove(originKey(origin));
        if (record != null) {
            getBorderRecordMapByXZ(world).remove(originKeyXZ(origin));
            return record;
        }
        return consumeBorderRecordByXZ(world, origin);
    }

    private static BorderRecord consumeBorderRecordByXZ(World world, Vector3i origin) {
        Map<String, BorderRecord> records = getBorderRecordMapByXZ(world);
        BorderRecord record = records.remove(originKeyXZ(origin));
        if (record == null) {
            return null;
        }
        return record;
    }

    private static BorderRecord getBorderRecord(World world, Vector3i origin) {
        Map<String, BorderRecord> records = getBorderRecordMap(world);
        return records.get(originKey(origin));
    }

    private static Map<String, BorderRecord> getBorderRecordMap(World world) {
        Map<String, BorderRecord> records = BORDER_RECORDS.get(world);
        if (records == null) {
            records = new HashMap<>();
            BORDER_RECORDS.put(world, records);
        }
        return records;
    }

    private static Map<String, BorderRecord> getBorderRecordMapByXZ(World world) {
        Map<String, BorderRecord> records = BORDER_RECORDS_BY_XZ.get(world);
        if (records == null) {
            records = new HashMap<>();
            BORDER_RECORDS_BY_XZ.put(world, records);
        }
        return records;
    }

    private static String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static String originKey(Vector3i origin) {
        return origin.getX() + ":" + origin.getY() + ":" + origin.getZ();
    }

    private static String originKeyXZ(Vector3i origin) {
        return origin.getX() + ":" + origin.getZ();
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null || needle.isEmpty()) {
            return false;
        }
        int limit = value.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    private static Bounds computeBounds(int width, int depth) {
        int halfWidth = width / 2;
        int minX = -halfWidth;
        int maxX = width - 1 - halfWidth;
        int maxZ = 0;
        int minZ = maxZ - (Math.max(1, depth) - 1);
        return new Bounds(minX, maxX, minZ, maxZ);
    }

    private static final class Bounds {
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private Bounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    private static final class BlockSnapshot {
        private final String blockId;
        private final int rotationIndex;

        private BlockSnapshot(String blockId, int rotationIndex) {
            this.blockId = blockId;
            this.rotationIndex = rotationIndex;
        }
    }

    private static final class BorderRecord {
        private final Map<String, Vector3i> positions = new HashMap<>();
    }

    public static final class TorchBounds {
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;
        public final int y;

        private TorchBounds(int minX, int maxX, int minZ, int maxZ, int y) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.y = y;
        }

        public int getWidth() {
            return Math.max(1, maxX - minX + 1);
        }

        public int getDepth() {
            return Math.max(1, maxZ - minZ + 1);
        }
    }

    private static final class TorchBoundsCache {
        private final TorchBounds bounds;
        private final long lastUpdatedMs;

        private TorchBoundsCache(TorchBounds bounds, long lastUpdatedMs) {
            this.bounds = bounds;
            this.lastUpdatedMs = lastUpdatedMs;
        }
    }
}
