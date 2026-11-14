package HyProTechTeam.item;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Persistent name and transfer priority for an item storage endpoint. */
public class ItemStorageConfigComponent implements Component<ChunkStore> {
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final StringCodec STRING_CODEC = new StringCodec();

    // Normalize decoded names and clamp priorities before storing them.
    public static final BuilderCodec<ItemStorageConfigComponent> CODEC =
            BuilderCodec.<ItemStorageConfigComponent>builder(
                            ItemStorageConfigComponent.class,
                            ItemStorageConfigComponent::new)
                    .addField(new KeyedCodec<>("Name", STRING_CODEC),
                            (component, value) -> component.name = normalizeName(value),
                            component -> component.name)
                    .addField(new KeyedCodec<>("Priority", INTEGER_CODEC),
                            (component, value) -> component.priority = ItemNodeComponent.clampPriority(
                                    value == null ? ItemNodeComponent.DEFAULT_PRIORITY : value),
                            component -> component.priority)
                    .build();

    private String name = "";
    private int priority = ItemNodeComponent.DEFAULT_PRIORITY;

    /** Returns the configured storage name. */
    public String getName() {
        return name;
    }

    /** Trims the storage name and converts null to an empty string. */
    public void setName(String name) {
        this.name = normalizeName(name);
    }

    /** Returns the configured routing priority. */
    public int getPriority() {
        return priority;
    }

    /** Stores a priority after applying the item-node priority bounds. */
    public void setPriority(int priority) {
        this.priority = ItemNodeComponent.clampPriority(priority);
    }

    /** Copies both configuration fields into a new component. */
    @Override
    public Component<ChunkStore> clone() {
        ItemStorageConfigComponent copy = new ItemStorageConfigComponent();
        copy.name = name;
        copy.priority = priority;
        return copy;
    }

    /** Produces the same field copy for serialization. */
    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }

    /** Trims a name, using an empty string for null or whitespace-only input. */
    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
