package HyProTechTeam.ui;

import HyProTechTeam.energy.EnergyNodeComponent;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class FurnaceHud extends CustomUIHud {
    public static final String KEY = "HyProTech_FurnaceHud";

    private int lastEnergy = Integer.MIN_VALUE;
    private int lastCapacity = Integer.MIN_VALUE;
    private int lastProgress = Integer.MIN_VALUE;
    private int lastProgressMax = Integer.MIN_VALUE;

    public FurnaceHud(PlayerRef playerRef) {
        super(playerRef, KEY);
    }

    @Override
    protected void build(UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HyProTech_FurnaceHud.ui");
    }

    public void update(EnergyNodeComponent node) {
        if (node == null) {
            return;
        }

        UICommandBuilder update = new UICommandBuilder();
        boolean changed = false;

        int energy = node.getEnergy();
        int capacity = node.getCapacity();
        if (energy != lastEnergy || capacity != lastCapacity) {
            update.set("#FurnaceEnergy.Text",
                    "Energy: " + energy + " / " + capacity + " J");
            lastEnergy = energy;
            lastCapacity = capacity;
            changed = true;
        }

        int progress = node.getProgress();
        int progressMax = node.getProgressMax();
        if (progress != lastProgress || progressMax != lastProgressMax) {
            int percent = 0;
            if (progressMax > 0) {
                percent = (int) Math.round(100.0 * progress / progressMax);
                percent = Math.min(100, Math.max(0, percent));
            }
            update.set("#FurnaceProgress.Text", "Progress: " + percent + "%");
            lastProgress = progress;
            lastProgressMax = progressMax;
            changed = true;
        }

        if (changed) {
            update(false, update);
        }
    }

    public void resetCache() {
        lastEnergy = Integer.MIN_VALUE;
        lastCapacity = Integer.MIN_VALUE;
        lastProgress = Integer.MIN_VALUE;
        lastProgressMax = Integer.MIN_VALUE;
    }
}
