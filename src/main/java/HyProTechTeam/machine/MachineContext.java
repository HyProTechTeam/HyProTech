package HyProTechTeam.machine;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import HyProTechTeam.energy.EnergyNodeComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Supplies a machine tick with its world position, components, and dirty flag. */
public final class MachineContext {
    private final World world;
    private final ChunkStore chunkStore;
    private final BlockComponentChunk components;
    private final int x;
    private final int y;
    private final int z;
    private final MachineComponent machine;
    private final EnergyNodeComponent energy;
    private boolean dirty;

    public MachineContext(
            World world,
            ChunkStore chunkStore,
            BlockComponentChunk components,
            int x,
            int y,
            int z,
            MachineComponent machine,
            EnergyNodeComponent energy) {
        this.world = world;
        this.chunkStore = chunkStore;
        this.components = components;
        this.x = x;
        this.y = y;
        this.z = z;
        this.machine = machine;
        this.energy = energy;
    }

    public World getWorld() {
        return world;
    }

    public ChunkStore getChunkStore() {
        return chunkStore;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public MachineComponent getMachine() {
        return machine;
    }

    public EnergyNodeComponent getEnergyNode() {
        return energy;
    }

    /** Returns transactional storage, or an empty storage when the block has no energy node. */
    public EnergyStorage getEnergyStorage() {
        if (energy == null) {
            return EnergyStorage.empty();
        }
        return energy.getStorage(components::markNeedsSaving);
    }

    /** Looks up the inventory associated with this machine's block position. */
    public ItemContainer getItemContainer() {
        return MachineItemAccess.getContainer(world, x, y, z);
    }

    /** Requests that the machine system persist changes made during this tick. */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }
}
