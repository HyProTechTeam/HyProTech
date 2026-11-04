package HyProTechTeam.sound;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.machine.MachineComponent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEventLayer;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HyProTechSounds {
    private static final boolean DISABLED = true;
    private static final boolean ONLY_WIND = false;
    public static final String EVENT_WIND_TURBINE = "HyProTech_Wind_Turbine";
    public static final String EVENT_SOLAR_PANEL = "HyProTech_Solar_Panel";
    public static final String EVENT_ELECTRIC_FURNACE = "HyProTech_Electric_Furnace";

    public static final String FILE_WIND_TURBINE = "Sounds/WindTurbine.ogg";
    public static final String FILE_SOLAR_PANEL = "Sounds/SolarPanel.ogg";
    public static final String FILE_ELECTRIC_FURNACE = "Sounds/ElectricFurnace.ogg";

    public static final long DEFAULT_LOOP_MS = 10000L;

    private static final Map<String, Integer> SOUND_INDEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOOP_MS_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REGISTERED_EVENTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_EVENTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> OVERRIDE_LOOP_MS = buildLoopOverrides();

    private HyProTechSounds() {
    }

    public static void registerDefaultsIfMissing() {
        if (DISABLED) {
            return;
        }
        if (!assetsReady()) {
            return;
        }
        if (isEnabledEvent(EVENT_WIND_TURBINE)) {
            resolveSoundIndex(EVENT_WIND_TURBINE, FILE_WIND_TURBINE);
        }
        if (isEnabledEvent(EVENT_SOLAR_PANEL)) {
            resolveSoundIndex(EVENT_SOLAR_PANEL, FILE_SOLAR_PANEL);
        }
        if (isEnabledEvent(EVENT_ELECTRIC_FURNACE)) {
            resolveSoundIndex(EVENT_ELECTRIC_FURNACE, FILE_ELECTRIC_FURNACE);
        }
    }

    public static void stopSound(World world, int x, int y, int z, String eventId, String soundFile) {
        if (DISABLED) {
            return;
        }
        if (!isEnabledEvent(eventId)) {
            return;
        }
        playSound(world, x, y, z, eventId, soundFile, 0f, 1f);
    }

    public static void tickLoop(
            World world,
            int x,
            int y,
            int z,
            String eventId,
            String soundFile,
            long fallbackMs,
            boolean working,
            MachineComponent machine) {
        if (machine == null) {
            return;
        }
        long next = machine.getNextSoundMs();
        long updated = tickLoopInternal(world, x, y, z, eventId, soundFile, fallbackMs, working, next);
        if (updated != next) {
            machine.setNextSoundMs(updated);
        }
    }

    public static void tickLoop(
            World world,
            int x,
            int y,
            int z,
            String eventId,
            String soundFile,
            long fallbackMs,
            boolean working,
            EnergyNodeComponent node) {
        if (node == null) {
            return;
        }
        long next = node.getNextSoundMs();
        long updated = tickLoopInternal(world, x, y, z, eventId, soundFile, fallbackMs, working, next);
        if (updated != next) {
            node.setNextSoundMs(updated);
        }
    }

    private static long tickLoopInternal(
            World world,
            int x,
            int y,
            int z,
            String eventId,
            String soundFile,
            long fallbackMs,
            boolean working,
            long nextSoundMs) {
        if (DISABLED) {
            return 0L;
        }
        if (!isEnabledEvent(eventId)) {
            return 0L;
        }
        if (!working) {
            if (nextSoundMs > 0L) {
                // Try to interrupt any currently playing instance.
                playSound(world, x, y, z, eventId, soundFile, 0f, 1f);
            }
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (nextSoundMs > 0L && now < nextSoundMs) {
            return nextSoundMs;
        }
        playSound(world, x, y, z, eventId, soundFile, 1f, 1f);
        long loopMs = resolveLoopMs(soundFile, fallbackMs);
        return now + loopMs;
    }

    private static void playSound(
            World world,
            int x,
            int y,
            int z,
            String eventId,
            String soundFile,
            float volume,
            float pitch) {
        if (DISABLED) {
            return;
        }
        if (!isEnabledEvent(eventId)) {
            return;
        }
        if (world == null || eventId == null || eventId.isEmpty()) {
            return;
        }
        int index = resolveSoundIndex(eventId, soundFile);
        if (index <= 0) {
            return;
        }
        world.execute(() -> {
            if (world.getEntityStore() == null) {
                return;
            }
            SoundUtil.playSoundEvent3d(
                    index,
                    SoundCategory.SFX,
                    x + 0.5,
                    y + 0.5,
                    z + 0.5,
                    volume,
                    pitch,
                    world.getEntityStore().getStore());
        });
    }

    private static int resolveSoundIndex(String eventId, String soundFile) {
        Integer cached = SOUND_INDEX_CACHE.get(eventId);
        if (cached != null && cached > 0) {
            return cached;
        }
        int index = SoundEvent.getAssetMap().getIndex(eventId);
        if (index <= 0 && soundFile != null && !soundFile.isEmpty() && assetsReady()) {
            registerFallbackSoundEvent(eventId, soundFile);
            index = SoundEvent.getAssetMap().getIndex(eventId);
        }
        if (index > 0) {
            SOUND_INDEX_CACHE.put(eventId, index);
        } else {
            warnMissingEvent(eventId, soundFile, index);
        }
        return index;
    }

    private static boolean assetsReady() {
        int baseIndex = SoundEvent.getAssetMap().getIndex("SFX_Cactus_Large_Hit");
        return baseIndex > 0;
    }

    private static void warnMissingEvent(String eventId, String soundFile, int index) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        if (!WARNED_EVENTS.add(eventId)) {
            return;
        }
        System.out.println("[HyProTech] Missing sound event '" + eventId
                + "' (index=" + index + ", file=" + soundFile
                + ", assetsReady=" + assetsReady() + ")");
    }

    private static void registerFallbackSoundEvent(String eventId, String soundFile) {
        if (!REGISTERED_EVENTS.add(eventId)) {
            return;
        }
        try {
            // TODO(0.7.5): 0.7.5 added a third float to this constructor; 0 keeps the old behaviour.
            SoundEventLayer layer = new SoundEventLayer(
                    1f,
                    0f,
                    0f,
                    false,
                    100,
                    1f,
                    SoundEventLayer.RandomSettings.DEFAULT,
                    new String[] { soundFile },
                    0);
            // TODO(0.7.5): 0.7.5 keeps four floats here (was six); mapped by intent.
            SoundEvent event = new SoundEvent(
                    eventId,
                    1f,
                    1f,
                    1f,
                    1f,
                    1,
                    false,
                    new SoundEventLayer[] { layer });
            SoundEvent.getAssetStore().loadAssets("HyProTech", Collections.singletonList(event));
        } catch (Exception ignored) {
            // Fall back to silent if asset registration fails.
        }
    }

    private static long resolveLoopMs(String soundFile, long fallbackMs) {
        if (soundFile == null || soundFile.isEmpty()) {
            return fallbackMs;
        }
        Long override = OVERRIDE_LOOP_MS.get(soundFile);
        if (override != null && override > 0L) {
            return override;
        }
        return LOOP_MS_CACHE.computeIfAbsent(soundFile, file -> {
            OggVorbisInfoCache.OggVorbisInfo info = OggVorbisInfoCache.getNow(file);
            if (info != null && info.duration > 0d) {
                long ms = Math.round(info.duration * 1000d);
                return Math.max(250L, ms);
            }
            return fallbackMs;
        });
    }

    private static Map<String, Long> buildLoopOverrides() {
        Map<String, Long> overrides = new HashMap<>();
        overrides.put(FILE_ELECTRIC_FURNACE, 49500L);
        overrides.put(FILE_SOLAR_PANEL, 10000L);
        overrides.put(FILE_WIND_TURBINE, 9000L);
        return overrides;
    }

    private static boolean isEnabledEvent(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return false;
        }
        if (!ONLY_WIND) {
            return true;
        }
        return EVENT_WIND_TURBINE.equals(eventId);
    }
}
