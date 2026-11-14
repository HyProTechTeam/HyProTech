package HyProTechTeam.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;

/** Carries an action from the changelog UI. */
public final class ChangelogEvent {
    // Decode the UI event fields from their named string values.
    public static final BuilderCodec<ChangelogEvent> CODEC =
            BuilderCodec.<ChangelogEvent>builder(ChangelogEvent.class, ChangelogEvent::new)
                    .addField(new KeyedCodec<>("Action", new StringCodec()),
                            (event, value) -> event.action = value,
                            event -> event.action)
                    .build();

    private String action;

    /** Returns the decoded action. */
    public String getAction() {
        return action;
    }
}
