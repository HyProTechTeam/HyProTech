package HyProTechTeam.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Displays solar output and suppresses duplicate UI updates. */
public class SolarHud extends CustomUIHud {
    private int lastOutput = Integer.MIN_VALUE;

    /** Associates the HUD with a player. */
    public SolarHud(PlayerRef playerRef) {
        super(playerRef);
    }

    /** Loads the solar HUD layout. */
    @Override
    protected void build(UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HyProTech_SolarHud.ui");
    }

    /** Sends new output text only when the value differs from the last update. */
    public void updateOutput(int output) {
        if (output == lastOutput) {
            return;
        }
        lastOutput = output;
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#SolarOutput.Text", "Solar Output: " + output);
        update(false, builder);
    }
}
