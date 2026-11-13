package HyProTechTeam.machine;

import HyProTechTeam.BlockComponentCompat;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import HyProTechTeam.energy.EnergyNodeComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import HyProTechTeam.BlockComponentCompat;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class MachineContext {
    private final World world;
    private final ChunkStore chunkStore;
    private final BlockComponentCompat.ChunkView components;
    private final int x;
    private final int y;
    private final int z;
    private final MachineComponent machine;
    private final EnergyNodeComponent energy;
    private boolean dirty;

    public MachineContext(
            World world,
            ChunkStore chunkStore,
            BlockComponentCompat.ChunkView components,
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

    public EnergyStorage getEnergyStorage() {
        if (energy == null) {
            return EnergyStorage.empty();
        }
        return energy.getStorage(components::markNeedsSaving);
    }

    public ItemContainer getItemContainer() {
        return MachineItemAccess.getContainer(world, x, y, z);
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }
}
