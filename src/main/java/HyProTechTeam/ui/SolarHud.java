package HyProTechTeam.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class SolarHud extends CustomUIHud {
    public static final String KEY = "HyProTech_SolarHud";

    private int lastOutput = Integer.MIN_VALUE;

    public SolarHud(PlayerRef playerRef) {
        super(playerRef, KEY);
    }

    @Override
    protected void build(UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HyProTech_SolarHud.ui");
    }

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
