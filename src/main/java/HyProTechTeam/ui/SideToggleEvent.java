package HyProTechTeam.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;

/** Carries a UI action, side, index, search query, and value. */
public class SideToggleEvent {
    // Decode the UI event fields from their named string values.
    public static final BuilderCodec<SideToggleEvent> CODEC =
            BuilderCodec.<SideToggleEvent>builder(SideToggleEvent.class, SideToggleEvent::new)
                    .addField(new KeyedCodec<>("Action", new StringCodec()),
                            (event, value) -> event.action = value,
                            event -> event.action)
                    .addField(new KeyedCodec<>("Side", new StringCodec()),
                            (event, value) -> event.side = value,
                            event -> event.side)
                    .addField(new KeyedCodec<>("Index", new StringCodec()),
                            (event, value) -> event.index = value,
                            event -> event.index)
                    .addField(new KeyedCodec<>("@SearchQuery", new StringCodec()),
                            (event, value) -> event.searchQuery = value,
                            event -> event.searchQuery)
                    .addField(new KeyedCodec<>("@Value", new StringCodec()),
                            (event, value) -> event.value = value,
                            event -> event.value)
                    .build();

    private String action;
    private String side;
    private String index;
    private String searchQuery;
    private String value;

    /** Returns the decoded action. */
    public String getAction() {
        return action;
    }

    /** Returns the decoded side. */
    public String getSide() {
        return side;
    }

    /** Returns the decoded index. */
    public String getIndex() {
        return index;
    }

    /** Returns the decoded search query. */
    public String getSearchQuery() {
        return searchQuery;
    }

    /** Returns the decoded value. */
    public String getValue() {
        return value;
    }
}
