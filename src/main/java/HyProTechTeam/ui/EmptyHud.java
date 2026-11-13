package HyProTechTeam.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class EmptyHud extends CustomUIHud {
    public static final String KEY = "HyProTech_EmptyHud";

    public EmptyHud(PlayerRef playerRef) {
        super(playerRef, KEY);
    }

    @Override
    protected void build(UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HyProTech_EmptyHud.ui");
    }
}
