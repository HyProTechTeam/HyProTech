package HyProTechTeam.item;

import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.CableUpgradeConfig;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemNodeComponent implements Component<ChunkStore> {
    private static final IntegerCodec INTEGER_CODEC = new IntegerCodec();
    private static final StringCodec STRING_CODEC = new StringCodec();

    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 9;
    public static final int DEFAULT_PRIORITY = 0;

    public static final BuilderCodec<ItemNodeComponent> CODEC =
            BuilderCodec.<ItemNodeComponent>builder(ItemNodeComponent.class, ItemNodeComponent::new)
                    .addField(new KeyedCodec<>("Mode", new EnumCodec<>(ItemMode.class)),
                            (component, value) -> component.mode = value != null ? value : ItemMode.BOTH,
                            component -> component.mode)
                    .addField(new KeyedCodec<>("Target", new EnumCodec<>(ItemTarget.class)),
                            (component, value) -> component.target = value != null ? value : ItemTarget.AUTO,
                            component -> component.target)
                    .addField(new KeyedCodec<>("Distribution", new EnumCodec<>(ItemDistributionMode.class)),
                            (component, value) -> component.distributionMode =
                                    value != null ? value : ItemDistributionMode.ROUND_ROBIN,
                            component -> component.distributionMode)
                    .addField(new KeyedCodec<>("Priority", INTEGER_CODEC),
                            (component, value) -> component.priority = clampPriority(
                                    value == null ? DEFAULT_PRIORITY : value),
                            component -> component.priority)
                    .addField(new KeyedCodec<>("CableTier", INTEGER_CODEC),
                            (component, value) -> component.cableTier =
                                    value != null ? value : CableUpgradeConfig.MIN_TIER,
                            component -> component.cableTier)
                    .addField(new KeyedCodec<>("MaxTransfer", INTEGER_CODEC),
                            (component, value) -> component.maxTransfer = value != null ? value : 0,
                            component -> component.maxTransfer)
                    .addField(new KeyedCodec<>("InputMask", INTEGER_CODEC),
                            (component, value) -> component.inputMask =
                                    value != null ? value : EnergySide.ALL_MASK,
                            component -> component.inputMask)
                    .addField(new KeyedCodec<>("OutputMask", INTEGER_CODEC),
                            (component, value) -> component.outputMask =
                                    value != null ? value : EnergySide.ALL_MASK,
                            component -> component.outputMask)
                    .addField(new KeyedCodec<>("Filters", STRING_CODEC),
                            (component, value) -> component.deserializeFilters(value),
                            ItemNodeComponent::serializeFilters)
                    .addField(new KeyedCodec<>("FilterModes", STRING_CODEC),
                            (component, value) -> component.deserializeFilterModes(value),
                            ItemNodeComponent::serializeFilterModes)
                    .addField(new KeyedCodec<>("FilterPresets", STRING_CODEC),
                            (component, value) -> component.deserializeFilterPresets(value),
                            ItemNodeComponent::serializeFilterPresets)
                    .build();

    private ItemMode mode = ItemMode.BOTH;
    private ItemTarget target = ItemTarget.AUTO;
    private ItemDistributionMode distributionMode = ItemDistributionMode.ROUND_ROBIN;
    private int priority = DEFAULT_PRIORITY;
    private int cableTier = CableUpgradeConfig.MIN_TIER;
    private int maxTransfer;
    private int inputMask = EnergySide.ALL_MASK;
    private int outputMask = EnergySide.ALL_MASK;
    private transient String lastCableState = "";
    private final EnumMap<EnergySide, LinkedHashSet<String>> filtersBySide =
            new EnumMap<>(EnergySide.class);
    private final EnumMap<EnergySide, FilterMode> filterModesBySide =
            new EnumMap<>(EnergySide.class);
    private final LinkedHashMap<String, LinkedHashSet<String>> filterPresets =
            new LinkedHashMap<>();

    public ItemMode getMode() {
        return mode;
    }

    public void setMode(ItemMode mode) {
        this.mode = mode == null ? ItemMode.BOTH : mode;
    }

    public ItemTarget getTarget() {
        return target;
    }

    public void setTarget(ItemTarget target) {
        this.target = target == null ? ItemTarget.AUTO : target;
    }

    public ItemDistributionMode getDistributionMode() {
        return distributionMode;
    }

    public void setDistributionMode(ItemDistributionMode distributionMode) {
        this.distributionMode = distributionMode == null ? ItemDistributionMode.ROUND_ROBIN : distributionMode;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = clampPriority(priority);
    }

    public static int clampPriority(int priority) {
        if (priority < MIN_PRIORITY) {
            return MIN_PRIORITY;
        }
        if (priority > MAX_PRIORITY) {
            return MAX_PRIORITY;
        }
        return priority;
    }

    public int getCableTier() {
        return cableTier;
    }

    public void setCableTier(int cableTier) {
        this.cableTier = cableTier;
    }

    public int getMaxTransfer() {
        return maxTransfer;
    }

    public void setMaxTransfer(int maxTransfer) {
        this.maxTransfer = maxTransfer;
    }

    public int getInputMask() {
        return inputMask;
    }

    public void setInputMask(int inputMask) {
        this.inputMask = inputMask & EnergySide.ALL_MASK;
    }

    public int getOutputMask() {
        return outputMask;
    }

    public void setOutputMask(int outputMask) {
        this.outputMask = outputMask & EnergySide.ALL_MASK;
    }

    public String getLastCableState() {
        return lastCableState == null ? "" : lastCableState;
    }

    public void setLastCableState(String lastCableState) {
        this.lastCableState = lastCableState == null ? "" : lastCableState;
    }

    public Set<String> getFilters(EnergySide side) {
        if (side == null) {
            return Collections.emptySet();
        }
        Set<String> filters = filtersBySide.get(side);
        return filters == null ? Collections.emptySet() : Collections.unmodifiableSet(filters);
    }

    public void setFilters(EnergySide side, Set<String> items) {
        if (side == null) {
            return;
        }
        if (items == null || items.isEmpty()) {
            filtersBySide.remove(side);
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(items);
        filtersBySide.put(side, next);
    }

    public void clearFilters(EnergySide side) {
        if (side == null) {
            return;
        }
        filtersBySide.remove(side);
    }

    public int getFilterCount(EnergySide side) {
        return getFilters(side).size();
    }

    public FilterMode getFilterMode(EnergySide side) {
        if (side == null) {
            return FilterMode.WHITELIST;
        }
        FilterMode mode = filterModesBySide.get(side);
        return mode == null ? FilterMode.WHITELIST : mode;
    }

    public void setFilterMode(EnergySide side, FilterMode mode) {
        if (side == null) {
            return;
        }
        FilterMode normalized = mode == null ? FilterMode.WHITELIST : mode;
        if (normalized == FilterMode.WHITELIST) {
            filterModesBySide.remove(side);
        } else {
            filterModesBySide.put(side, normalized);
        }
    }

    public FilterMode toggleFilterMode(EnergySide side) {
        FilterMode next = getFilterMode(side).next();
        setFilterMode(side, next);
        return next;
    }

    public boolean allowsItem(EnergySide side, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        Set<String> filters = getFilters(side);
        if (filters.isEmpty()) {
            return true;
        }
        boolean contains = filters.contains(itemId);
        return getFilterMode(side) == FilterMode.BLACKLIST ? !contains : contains;
    }

    public boolean toggleFilterItem(EnergySide side, String itemId) {
        if (side == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> filters = filtersBySide.computeIfAbsent(side, key -> new LinkedHashSet<>());
        if (filters.contains(itemId)) {
            filters.remove(itemId);
            if (filters.isEmpty()) {
                filtersBySide.remove(side);
            }
            return true;
        }
        filters.add(itemId);
        return true;
    }

    public Map<String, Set<String>> getFilterPresets() {
        if (filterPresets.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : filterPresets.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public List<String> getFilterPresetNames() {
        return new java.util.ArrayList<>(filterPresets.keySet());
    }

    public Set<String> getFilterPreset(String name) {
        if (name == null || name.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> preset = filterPresets.get(name);
        return preset == null ? Collections.emptySet() : Collections.unmodifiableSet(preset);
    }

    public void saveFilterPreset(String name, Set<String> items) {
        if (name == null || name.trim().isEmpty() || items == null || items.isEmpty()) {
            return;
        }
        LinkedHashSet<String> stored = new LinkedHashSet<>(items);
        filterPresets.put(name.trim(), stored);
    }

    public boolean removeFilterPreset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return filterPresets.remove(name.trim()) != null;
    }

    public boolean allowsTake(EnergySide side) {
        return (inputMask & side.mask()) != 0;
    }

    public boolean allowsPut(EnergySide side) {
        return (outputMask & side.mask()) != 0;
    }

    public ItemMode getSideMode(EnergySide side) {
        boolean take = allowsTake(side);
        boolean put = allowsPut(side);
        if (take && put) {
            return ItemMode.BOTH;
        }
        if (take) {
            return ItemMode.TAKE;
        }
        if (put) {
            return ItemMode.PUT;
        }
        return ItemMode.OFF;
    }

    public void setSideMode(EnergySide side, ItemMode mode) {
        int mask = side.mask();
        switch (mode) {
            case OFF:
                inputMask &= ~mask;
                outputMask &= ~mask;
                break;
            case TAKE:
                inputMask |= mask;
                outputMask &= ~mask;
                break;
            case PUT:
                inputMask &= ~mask;
                outputMask |= mask;
                break;
            case BOTH:
                inputMask |= mask;
                outputMask |= mask;
                break;
            default:
                break;
        }
    }

    public ItemMode cycleSideMode(EnergySide side) {
        ItemMode next = getSideMode(side).next();
        setSideMode(side, next);
        return next;
    }

    public void applyFrom(ItemNodeComponent other) {
        if (other == null) {
            return;
        }
        mode = other.mode;
        target = other.target;
        distributionMode = other.distributionMode;
        priority = other.priority;
        cableTier = other.cableTier;
        maxTransfer = other.maxTransfer;
        inputMask = other.inputMask;
        outputMask = other.outputMask;
        filtersBySide.clear();
        filterModesBySide.clear();
        for (EnergySide side : EnergySide.VALUES) {
            LinkedHashSet<String> filters = other.filtersBySide.get(side);
            if (filters != null && !filters.isEmpty()) {
                filtersBySide.put(side, new LinkedHashSet<>(filters));
            }
            FilterMode mode = other.filterModesBySide.get(side);
            if (mode != null && mode != FilterMode.WHITELIST) {
                filterModesBySide.put(side, mode);
            }
        }
    }

    @Override
    public Component<ChunkStore> clone() {
        ItemNodeComponent copy = new ItemNodeComponent();
        copy.mode = mode;
        copy.target = target;
        copy.distributionMode = distributionMode;
        copy.priority = priority;
        copy.cableTier = cableTier;
        copy.maxTransfer = maxTransfer;
        copy.inputMask = inputMask;
        copy.outputMask = outputMask;
        copy.lastCableState = lastCableState;
        for (EnergySide side : EnergySide.VALUES) {
            LinkedHashSet<String> filters = filtersBySide.get(side);
            if (filters != null && !filters.isEmpty()) {
                copy.filtersBySide.put(side, new LinkedHashSet<>(filters));
            }
            FilterMode mode = filterModesBySide.get(side);
            if (mode != null && mode != FilterMode.WHITELIST) {
                copy.filterModesBySide.put(side, mode);
            }
        }
        if (!filterPresets.isEmpty()) {
            for (Map.Entry<String, LinkedHashSet<String>> entry : filterPresets.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) {
                    continue;
                }
                LinkedHashSet<String> items = entry.getValue();
                if (items == null || items.isEmpty()) {
                    continue;
                }
                copy.filterPresets.put(entry.getKey(), new LinkedHashSet<>(items));
            }
        }
        return copy;
    }

    @Override
    public Component<ChunkStore> cloneSerializable() {
        return clone();
    }

    private void deserializeFilters(String value) {
        filtersBySide.clear();
        if (value == null || value.isEmpty()) {
            return;
        }
        String[] entries = value.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            String sideName = entry.substring(0, eq);
            EnergySide side = EnergySide.fromName(sideName);
            if (side == null) {
                continue;
            }
            String itemsPart = entry.substring(eq + 1);
            if (itemsPart.isEmpty()) {
                continue;
            }
            LinkedHashSet<String> items = new LinkedHashSet<>();
            for (String itemId : itemsPart.split(",")) {
                if (itemId == null) {
                    continue;
                }
                String trimmed = itemId.trim();
                if (!trimmed.isEmpty()) {
                    items.add(trimmed);
                }
            }
            if (!items.isEmpty()) {
                filtersBySide.put(side, items);
            }
        }
    }

    private void deserializeFilterModes(String value) {
        filterModesBySide.clear();
        if (value == null || value.isEmpty()) {
            return;
        }
        String[] entries = value.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            String sideName = entry.substring(0, eq);
            EnergySide side = EnergySide.fromName(sideName);
            if (side == null) {
                continue;
            }
            String modeName = entry.substring(eq + 1);
            FilterMode mode = FilterMode.fromName(modeName);
            if (mode != null && mode != FilterMode.WHITELIST) {
                filterModesBySide.put(side, mode);
            }
        }
    }

    private void deserializeFilterPresets(String value) {
        filterPresets.clear();
        if (value == null || value.isEmpty()) {
            return;
        }
        String[] entries = value.split(";");
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            String presetName = entry.substring(0, eq).trim();
            if (presetName.isEmpty()) {
                continue;
            }
            String itemsPart = entry.substring(eq + 1);
            if (itemsPart.isEmpty()) {
                continue;
            }
            LinkedHashSet<String> items = new LinkedHashSet<>();
            for (String itemId : itemsPart.split("\\|")) {
                if (itemId == null) {
                    continue;
                }
                String trimmed = itemId.trim();
                if (!trimmed.isEmpty()) {
                    items.add(trimmed);
                }
            }
            if (!items.isEmpty()) {
                filterPresets.put(presetName, items);
            }
        }
    }

    private String serializeFilters() {
        StringBuilder out = new StringBuilder();
        for (EnergySide side : EnergySide.VALUES) {
            LinkedHashSet<String> items = filtersBySide.get(side);
            if (items == null || items.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(side.name()).append('=');
            boolean first = true;
            for (String itemId : items) {
                if (itemId == null || itemId.isEmpty()) {
                    continue;
                }
                if (!first) {
                    out.append(',');
                }
                out.append(itemId);
                first = false;
            }
        }
        return out.toString();
    }

    private String serializeFilterModes() {
        StringBuilder out = new StringBuilder();
        for (EnergySide side : EnergySide.VALUES) {
            FilterMode mode = filterModesBySide.get(side);
            if (mode == null || mode == FilterMode.WHITELIST) {
                continue;
            }
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(side.name()).append('=').append(mode.name());
        }
        return out.toString();
    }

    private String serializeFilterPresets() {
        if (filterPresets.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, LinkedHashSet<String>> entry : filterPresets.entrySet()) {
            String name = entry.getKey();
            LinkedHashSet<String> items = entry.getValue();
            if (name == null || name.isEmpty() || items == null || items.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(name).append('=');
            boolean first = true;
            for (String itemId : items) {
                if (itemId == null || itemId.isEmpty()) {
                    continue;
                }
                if (!first) {
                    out.append('|');
                }
                out.append(itemId);
                first = false;
            }
        }
        return out.toString();
    }
}
