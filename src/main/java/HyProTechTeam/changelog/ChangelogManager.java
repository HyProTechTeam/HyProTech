package HyProTechTeam.changelog;

import HyProTechTeam.ui.ChangelogPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class ChangelogManager {
    public static final String CHANGELOG_VERSION = "1.5.0";
    public static final String CHANGELOG_TEXT = """
Changelog
Version 1.5.0 (from 1.4.1)

Updated for Hytale server 2026.03.26-89796e57b.
Migrated block state API: ProcessingBenchState → ProcessingBenchBlock,
ItemContainerState → ItemContainerBlock, removed deprecated BlockState/BlockStateModule calls.
Fixed int2ReferenceEntrySet usages across energy, item network and UI systems.

Also fixed:
Powders: Adamantite, Onyxium and Prisma


Changelog

Version 1.3.2 (from 1.3.1)
Fixed a crash when opening machines near the Quarry.
Fixed Ore Gen.
Improved Quarry speed when Backfill is enabled.
Fixed a teleport bug while the Quarry is running.

Quarry Border Setup (Detailed)
1) Click "Give Border Torch" to receive 2 Border Torches.
2) Place Torch 1 on the same Y level as the Quarry, right next to the Quarry block.
3) Place Torch 2 at the opposite corner of the desired area (X/Z rectangle).
4) The Quarry must be inside the rectangle formed by the two torches.
5) Torches must not share the same X or the same Z, and must be within 64 blocks of the Quarry.
6) Maximum Quarry size: 50x50.
See the image below for the correct placement.

Version 1.3.1 (from 1.3.0)
Added macOS support for modlist and config.
Added automatic config file generation inside the mods folder.
Completely reworked the Quarry feature due to bugs that (with border enabled) changed surrounding blocks and sometimes returned null.
Updated Quarry setup:
Click Give Border Torch and place 2 torches.
Torch 1 should be placed at the same height/width/length level, ideally right next to the Quarry.
Torch 2 should be placed at the opposite corner depending on the desired Quarry size.
Maximum Quarry size: 50x50.
In quarry you can set -BACKFILL OR NOT .. with backfill quarry is slower (you cant set forcebackfill in config)


Changelog Big Update
Version 1.3.1 (from 1.2.8 and 1.3.0)

IMPORTANT! IF THERE ARE MISSING/ERROR BLOCKS, THEY WILL NEED TO BE REBUILT. THERE HAVE BEEN CHANGES TO UI AND FUNCTIONALITY

Unlocked recipes from Bronze
Weapons
Armor

New content
Added the Alloy Smelter for advanced alloy production.
Added the Ore Crusher for ore doubling through powder processing.
Introduced Ore Powders as a new intermediate refining material.
Added a Bronze Ingot crafting recipe.
Implemented the Wind Turbine as a new renewable power source.

New ores added
Bauxite
Cassiterite
Chromite
Ilmenite
Manganese
Pentlandite
Quartzite
Scheelite
Spodumene
Uraninite
Vanadinite

New materials and ingots
Bauxite -> HyProTech_Ingot_Aluminum
Cassiterite -> HyProTech_Ingot_Tin
Chromite -> HyProTech_Ingot_Chromium
Ilmenite -> HyProTech_Ingot_Titanium
Manganese -> HyProTech_Ingot_Manganese
Pentlandite -> HyProTech_Ingot_Nickel
Quartzite -> HyProTech_Ingot_Silicon (raw silicon material, not an ingot)
Scheelite -> HyProTech_Ingot_Tungsten
Spodumene -> HyProTech_Ingot_Lithium
Uraninite -> HyProTech_Ingot_Uranium
Vanadinite -> HyProTech_Ingot_Vanadium

New alloys and advanced blends
Electrum Alloy
Steel Alloy
Invar Alloy
Constantan Alloy
High-Tier Alloy Blend
Composite Alloy Ingot
Superalloy Blend
Titanium-Vanadium Alloy

New core crafting components
Iron Gear
Reinforced Gear
Iron Plate
Reinforced Plate
Titanium Plate
Tungsten Plate
Iron Rod
Reinforced Rod

New items added
Machine Frame
Reinforced Machine Frame
Circuit Board
Spring

New machines and storage
Added the Large Battery for improved energy storage.
Added the Metal Press machine for advanced component crafting.

Visual and UI improvements
Updated the Electric Furnace model.
Improved general UI layout and usability.
Enhanced item filter UI for better inventory management.
Updated textures for energy cables and item transport cables.

Bug fixes
Fixed an issue where Solar Panels generated power even when covered or blocked.

Components and future expansion
Added a large set of new items and crafting components (currently placeholders, to be expanded in upcoming updates).
Early preparation work for upcoming Nuclear Science and Heat Science.
""";
    public static final String CHANGELOG_ID =
            CHANGELOG_VERSION + ":" + Integer.toHexString(CHANGELOG_TEXT.hashCode());

    private final JavaPlugin plugin;
    private final Config<ChangelogConfig> config;
    private final Path configPath;
    private ChangelogConfig state = new ChangelogConfig();
    private boolean loaded;

    public ChangelogManager(JavaPlugin plugin, Config<ChangelogConfig> config, Path configPath) {
        this.plugin = plugin;
        this.config = config;
        this.configPath = configPath;
    }

    public void load() {
        if (loaded) {
            return;
        }
        boolean exists = configPath != null && Files.exists(configPath);
        if (config == null) {
            loaded = true;
            return;
        }
        try {
            ChangelogConfig loadedConfig = config.load().join();
            if (loadedConfig != null) {
                state = loadedConfig;
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().atWarning().log(
                        "[HyProTech] Failed to load changelog state: %s", ex.getMessage());
            }
            state = new ChangelogConfig();
        }
        loaded = true;
        if (!exists) {
            save();
        }
    }

    public boolean shouldShow(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        ensureLoaded();
        String lastSeen = state.getLastSeen(uuid);
        return !CHANGELOG_ID.equalsIgnoreCase(lastSeen);
    }

    public void markSeen(UUID uuid) {
        if (uuid == null) {
            return;
        }
        ensureLoaded();
        state.markSeen(uuid, CHANGELOG_ID);
        save();
    }

    public boolean openForPlayer(Player player, PlayerRef playerRef, boolean force) {
        if (player == null || playerRef == null) {
            return false;
        }
        if (!force && !shouldShow(playerRef.getUuid())) {
            return false;
        }
        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return false;
        }
        if (pageManager.getCustomPage() != null) {
            return false;
        }
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null) {
            return false;
        }
        Store<EntityStore> store = entityRef.getStore();
        if (store == null) {
            return false;
        }
        pageManager.openCustomPage(entityRef, store, new ChangelogPage(playerRef, this));
        return true;
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void save() {
        if (config == null) {
            return;
        }
        config.save().exceptionally(ex -> {
            if (plugin != null) {
                plugin.getLogger().atWarning().log(
                        "[HyProTech] Failed to save changelog state: %s", ex.getMessage());
            }
            return null;
        });
    }
}
