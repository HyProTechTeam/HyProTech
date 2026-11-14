package HyProTechTeam.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;

/** Carries an item-network priority UI action, index, and value. */
public class ItemNetworkPriorityEvent {
    // Decode the UI event fields from their named string values.
    public static final BuilderCodec<ItemNetworkPriorityEvent> CODEC =
            BuilderCodec.<ItemNetworkPriorityEvent>builder(
                            ItemNetworkPriorityEvent.class,
                            ItemNetworkPriorityEvent::new)
                    .addField(new KeyedCodec<>("Action", new StringCodec()),
                            (event, value) -> event.action = value,
                            event -> event.action)
                    .addField(new KeyedCodec<>("Index", new StringCodec()),
                            (event, value) -> event.index = value,
                            event -> event.index)
                    .addField(new KeyedCodec<>("@Value", new StringCodec()),
                            (event, value) -> event.value = value,
                            event -> event.value)
                    .build();

    private String action;
    private String index;
    private String value;

    /** Returns the decoded action. */
    public String getAction() {
        return action;
    }

    /** Returns the decoded index. */
    public String getIndex() {
        return index;
    }

    /** Returns the decoded value. */
    public String getValue() {
        return value;
    }
}
