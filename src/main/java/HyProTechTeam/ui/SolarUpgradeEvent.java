package HyProTechTeam.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;

/** Carries a solar upgrade UI action. */
public class SolarUpgradeEvent {
    // Decode the UI event fields from their named string values.
    public static final BuilderCodec<SolarUpgradeEvent> CODEC =
            BuilderCodec.<SolarUpgradeEvent>builder(SolarUpgradeEvent.class, SolarUpgradeEvent::new)
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
