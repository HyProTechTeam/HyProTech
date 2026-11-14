package HyProTechTeam.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Loads the empty HUD layout for a player. */
public class EmptyHud extends CustomUIHud {
    /** Associates the empty HUD with a player. */
    public EmptyHud(PlayerRef playerRef) {
        super(playerRef);
    }

    /** Appends the empty HUD layout. */
    @Override
    protected void build(UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HyProTech_EmptyHud.ui");
    }
}
