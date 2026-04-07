package HyProTechTeam.machine;

import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.energy.EnergyNodeComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class MachineSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, MachineComponent> machineType;
    private final ComponentType<ChunkStore, EnergyNodeComponent> energyType;

    public MachineSystem(
            ComponentType<ChunkStore, MachineComponent> machineType,
            ComponentType<ChunkStore, EnergyNodeComponent> energyType) {
        this.machineType = machineType;
        this.energyType = energyType;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Archetype.of(WorldChunk.getComponentType(), BlockComponentChunk.getComponentType());
    }

    @Override
    public boolean isParallel(int total, int chunkSize) {
        return false;
    }

    @Override
    public void tick(
            float delta,
            int entityIndex,
            ArchetypeChunk<ChunkStore> chunk,
            Store<ChunkStore> store,
            CommandBuffer<ChunkStore> commandBuffer) {
        WorldChunk worldChunk = chunk.getComponent(entityIndex, WorldChunk.getComponentType());
        BlockComponentChunk blockComponents = chunk.getComponent(entityIndex, BlockComponentChunk.getComponentType());
        if (worldChunk == null || blockComponents == null) {
            return;
        }

        World world = worldChunk.getWorld();
        ChunkStore chunkStore = world == null ? null : world.getChunkStore();
        if (chunkStore == null) {
            return;
        }

        int chunkX = worldChunk.getX();
        int chunkZ = worldChunk.getZ();

        IntArrayList duplicateHolders = null;
        IntArrayList invalidReferences = null;
        boolean referencesChanged = false;
        for (Int2ObjectMap.Entry<Holder<ChunkStore>> entry : blockComponents.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            Holder<ChunkStore> holder = entry.getValue();
            Ref<ChunkStore> existingRef = blockComponents.getEntityReference(blockIndex);
            if (existingRef != null) {
                if (!existingRef.isValid()) {
                    blockComponents.removeEntityReference(blockIndex, existingRef);
                    referencesChanged = true;
                } else {
                    if (store != null && holder != null) {
                        MachineComponent holderMachine = holder.getComponent(machineType);
                        if (holderMachine != null) {
                            store.putComponent(existingRef, machineType, holderMachine);
                        }
                        EnergyNodeComponent holderEnergy = holder.getComponent(energyType);
                        if (holderEnergy != null) {
                            store.putComponent(existingRef, energyType, holderEnergy);
                        }
                    }
                    if (duplicateHolders == null) {
                        duplicateHolders = new IntArrayList();
                    }
                    duplicateHolders.add(blockIndex);
                    continue;
                }
            }
            processBlock(blockComponents, commandBuffer, chunkStore, world, chunkX, chunkZ, blockIndex, holder, null, delta);
        }

        for (it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry<Ref<ChunkStore>> entry : blockComponents.getEntityReferences().int2ReferenceEntrySet()) {
            int blockIndex = entry.getIntKey();
            Ref<ChunkStore> ref = entry.getValue();
            if (ref == null || !ref.isValid()) {
                if (invalidReferences == null) {
                    invalidReferences = new IntArrayList();
                }
                invalidReferences.add(blockIndex);
                continue;
            }
            processBlock(blockComponents, commandBuffer, chunkStore, world, chunkX, chunkZ, blockIndex, null, ref, delta);
        }

        if (invalidReferences != null && !invalidReferences.isEmpty()) {
            for (int i = 0; i < invalidReferences.size(); i++) {
                int blockIndex = invalidReferences.getInt(i);
                Ref<ChunkStore> ref = blockComponents.getEntityReference(blockIndex);
                if (ref != null && !ref.isValid()) {
                    blockComponents.removeEntityReference(blockIndex, ref);
                    disableTickingAt(world, chunkX, chunkZ, blockIndex);
                    referencesChanged = true;
                }
            }
        }

        if (duplicateHolders != null && !duplicateHolders.isEmpty()) {
            for (int i = 0; i < duplicateHolders.size(); i++) {
                blockComponents.removeEntityHolder(duplicateHolders.getInt(i));
            }
            referencesChanged = true;
        }

        if (referencesChanged) {
            blockComponents.markNeedsSaving();
        }
    }

    private void disableTickingAt(World world, int chunkX, int chunkZ, int blockIndex) {
        if (world == null) {
            return;
        }
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return;
        }
        try {
            accessor.setTicking(worldX, localY, worldZ, false);
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    private void processBlock(
            BlockComponentChunk blockComponents,
            CommandBuffer<ChunkStore> commandBuffer,
            ChunkStore chunkStore,
            World world,
            int chunkX,
            int chunkZ,
            int blockIndex,
            Holder<ChunkStore> holder,
            Ref<ChunkStore> ref,
            float deltaSeconds) {
        if (world == null || (holder == null && ref == null)) {
            return;
        }

        BlockType blockType = getBlockTypeAt(world, chunkX, chunkZ, blockIndex);
        String blockId = blockType == null ? null : blockType.getId();

        MachineComponent machine = blockComponents.getComponent(blockIndex, machineType);
        MachineDefinition definition = machine == null ? null : MachineRegistry.getById(machine.getMachineId());
        if (definition == null) {
            definition = MachineRegistry.findByBlockId(blockId);
        }
        if (definition == null) {
            return;
        }

        boolean changed = false;
        if (machine == null) {
            machine = definition.createMachineComponent();
            if (machine.getMachineId() == null || machine.getMachineId().isEmpty()) {
                machine.setMachineId(definition.id());
            }
            putComponent(blockComponents, commandBuffer, holder, ref, machineType, machine);
            changed = true;
        } else if (!definition.id().equals(machine.getMachineId())) {
            machine.setMachineId(definition.id());
            changed = true;
        }

        EnergyNodeComponent energy = blockComponents.getComponent(blockIndex, energyType);
        if (energy == null) {
            energy = definition.createEnergyNode();
            putComponent(blockComponents, commandBuffer, holder, ref, energyType, energy);
            changed = true;
        }

        if (definition instanceof QuarryMachine) {
            int parsedTier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_QUARRY);
            if (parsedTier > 0 && machine.getTier() != parsedTier) {
                machine.setTier(parsedTier);
                changed = true;
            }
        } else if (definition instanceof OreCrusherMachine) {
            int parsedTier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ORE_CRUSHER);
            if (parsedTier > 0 && machine.getTier() != parsedTier) {
                machine.setTier(parsedTier);
                changed = true;
            }
        } else if (definition instanceof AlloySmelterMachine) {
            int parsedTier = TieredIdUtil.parseTierSuffix(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER);
            if (parsedTier > 0 && machine.getTier() != parsedTier) {
                machine.setTier(parsedTier);
                changed = true;
            }
        }

        EnergyNodeComponent.NodeType beforeNodeType = energy == null ? null : energy.getNodeType();
        int beforeCapacity = energy == null ? 0 : energy.getCapacity();
        int beforeMaxTransfer = energy == null ? 0 : energy.getMaxTransfer();
        int beforeInputMask = energy == null ? 0 : energy.getInputMask();
        int beforeOutputMask = energy == null ? 0 : energy.getOutputMask();
        int beforeProgressMax = machine == null ? 0 : machine.getProgressMax();

        definition.configureDefaults(machine, energy);

        if (energy != null) {
            if (energy.getNodeType() != beforeNodeType
                    || energy.getCapacity() != beforeCapacity
                    || energy.getMaxTransfer() != beforeMaxTransfer
                    || energy.getInputMask() != beforeInputMask
                    || energy.getOutputMask() != beforeOutputMask) {
                changed = true;
            }
        }
        if (machine != null && machine.getProgressMax() != beforeProgressMax) {
            changed = true;
        }

        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, ChunkUtil.xFromBlockInColumn(blockIndex));
        int worldY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, ChunkUtil.zFromBlockInColumn(blockIndex));

        if (blockType == null || blockType == BlockType.EMPTY || blockId == null) {
            return;
        }
        if (!definition.matchesBlockId(blockId)) {
            return;
        }
        MachineContext context = new MachineContext(
                world,
                chunkStore,
                blockComponents,
                worldX,
                worldY,
                worldZ,
                machine,
                energy);
        boolean tickChanged = definition.tick(context, deltaSeconds);
        if (tickChanged || context.isDirty()) {
            changed = true;
        }

        if (changed) {
            if (ref != null && commandBuffer != null) {
                if (machine != null) {
                    commandBuffer.putComponent(ref, machineType, machine);
                }
                if (energy != null) {
                    commandBuffer.putComponent(ref, energyType, energy);
                }
            } else if (holder != null) {
                if (machine != null) {
                    holder.putComponent(machineType, machine);
                }
                if (energy != null) {
                    holder.putComponent(energyType, energy);
                }
            }
            blockComponents.markNeedsSaving();
        }
    }

    private BlockType getBlockTypeAt(World world, int chunkX, int chunkZ, int blockIndex) {
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
        int worldY = localY;

        long chunkIndex = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        BlockAccessor accessor = world.getChunkIfLoaded(chunkIndex);
        if (accessor == null) {
            return null;
        }

        return accessor.getBlockType(worldX, worldY, worldZ);
    }

    private <T extends Component<ChunkStore>> void putComponent(
            BlockComponentChunk blockComponents,
            CommandBuffer<ChunkStore> commandBuffer,
            Holder<ChunkStore> holder,
            Ref<ChunkStore> ref,
            ComponentType<ChunkStore, T> type,
            T component) {
        if (holder != null) {
            holder.putComponent(type, component);
            blockComponents.markNeedsSaving();
            return;
        }
        if (commandBuffer != null && ref != null) {
            commandBuffer.putComponent(ref, type, component);
        }
    }
}
