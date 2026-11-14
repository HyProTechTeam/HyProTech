package HyProTechTeam.item;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.Int2ObjectMapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Stores item endpoint configurations by block index within a chunk. */
public class ItemStorageConfigChunk implements Component<ChunkStore> {
    private static final Int2ObjectMapCodec<ItemStorageConfigComponent> CONFIG_CODEC =
            new Int2ObjectMapCodec<>(ItemStorageConfigComponent.CODEC, Int2ObjectOpenHashMap::new);

    // Decode the map into a mutable map while retaining its component values.
    public static final BuilderCodec<ItemStorageConfigChunk> CODEC =
            BuilderCodec.<ItemStorageConfigChunk>builder(ItemStorageConfigChunk.class, ItemStorageConfigChunk::new)
                    .addField(new KeyedCodec<>("Configs", CONFIG_CODEC),
                            (component, value) -> component.configs = copyConfigs(value),
                            component -> component.configs)
                    .build();

    private Int2ObjectMap<ItemStorageConfigComponent> configs = new Int2ObjectOpenHashMap<>();

    /** Looks up a configuration by chunk-local block index. */
    public ItemStorageConfigComponent getConfig(int blockIndex) {
        return configs.get(blockIndex);
    }

    /** Replaces a block's configuration or removes it when the supplied value is null. */
    public void setConfig(int blockIndex, ItemStorageConfigComponent config) {
        ensureMutable();
        if (config == null) {
            configs.remove(blockIndex);
        } else {
            configs.put(blockIndex, config);
        }
    }

    /** Copies the map and clones each non-null configuration component. */
    @Override
    public Component<ChunkStore> clone() {
        ItemStorageConfigChunk copy = new ItemStorageConfigChunk();
        if (!configs.isEmpty()) {
            for (Int2ObjectMap.Entry<ItemStorageConfigComponent> entry : configs.int2ObjectEntrySet()) {
                ItemStorageConfigComponent value = entry.getValue();
                copy.configs.put(entry.getIntKey(), value == null ? null : (ItemStorageConfigComponent) value.clone());
            }
        }
        return copy;
    }

    /** Uses the same cloned map for serialization. */
    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }

    /** Copies the map when its current implementation is not the expected mutable map. */
    private void ensureMutable() {
        if (configs instanceof Int2ObjectOpenHashMap) {
            return;
        }
        configs = copyConfigs(configs);
    }

    /** Copies map entries into a new mutable map without cloning their values. */
    private static Int2ObjectMap<ItemStorageConfigComponent> copyConfigs(
            Int2ObjectMap<ItemStorageConfigComponent> source) {
        Int2ObjectOpenHashMap<ItemStorageConfigComponent> copy = new Int2ObjectOpenHashMap<>();
        if (source != null) {
            for (Int2ObjectMap.Entry<ItemStorageConfigComponent> entry : source.int2ObjectEntrySet()) {
                copy.put(entry.getIntKey(), entry.getValue());
            }
        }
        return copy;
    }
}
