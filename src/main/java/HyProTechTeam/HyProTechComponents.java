package HyProTechTeam;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.item.ItemNodeComponent;
import HyProTechTeam.item.ItemStorageConfigChunk;
import HyProTechTeam.item.ItemStorageConfigComponent;
import HyProTechTeam.machine.MachineComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Shared references to the component types registered during plugin initialization. */
public final class HyProTechComponents {
    public static ComponentType<ChunkStore, EnergyNodeComponent> ENERGY;
    public static ComponentType<ChunkStore, ItemNodeComponent> ITEM;
    public static ComponentType<ChunkStore, ItemStorageConfigComponent> ITEM_STORAGE;
    public static ComponentType<ChunkStore, ItemStorageConfigChunk> ITEM_STORAGE_CHUNK;
    public static ComponentType<ChunkStore, MachineComponent> MACHINE;

    private HyProTechComponents() {
    }

    /** Stores the registered component types for access by the rest of the plugin. */
    public static void init(
            ComponentType<ChunkStore, EnergyNodeComponent> energyType,
            ComponentType<ChunkStore, ItemNodeComponent> itemType,
            ComponentType<ChunkStore, ItemStorageConfigComponent> storageType,
            ComponentType<ChunkStore, ItemStorageConfigChunk> storageChunkType,
            ComponentType<ChunkStore, MachineComponent> machineType) {
        ENERGY = energyType;
        ITEM = itemType;
        ITEM_STORAGE = storageType;
        ITEM_STORAGE_CHUNK = storageChunkType;
        MACHINE = machineType;
    }
}
