package HyProTechTeam.mac;

import HyProTechTeam.energy.BatteryUpgradeConfig;
import HyProTechTeam.energy.CableUpgradeConfig;
import HyProTechTeam.energy.SolarUpgradeConfig;
import HyProTechTeam.energy.WindUpgradeConfig;
import HyProTechTeam.furnace.FurnaceConfig;
import HyProTechTeam.machine.AlloySmelterConfig;
import HyProTechTeam.machine.OreCrusherConfig;
import HyProTechTeam.machine.QuarryConfig;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.util.Config;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;
import java.util.function.Supplier;

/** Integrates the optional MAC configuration UI without a compile-time dependency on MAC. */
public final class MacConfigBridge {
    // Resolve MAC types by name so the plugin can also run when MAC is absent.
    private static final String PLUGIN_CONFIG_CLASS = "net.lyivx.mac.config.PluginConfig";
    private static final String CONFIG_BUILDER_CLASS = "net.lyivx.mac.config.ConfigBuilder";
    private static final String CONFIG_FIELD_CLASS = "net.lyivx.mac.config.ConfigField";

    private MacConfigBridge() {
    }

    /** Groups the plugin's native config handles for conversion and persistence. */
    public static final class ConfigBundle {
        public final Config<BatteryUpgradeConfig.ConfigData> battery;
        public final Config<SolarUpgradeConfig.ConfigData> solar;
        public final Config<WindUpgradeConfig.ConfigData> wind;
        public final Config<CableUpgradeConfig.ConfigData> cable;
        public final Config<FurnaceConfig.ConfigData> furnace;
        public final Config<OreCrusherConfig.ConfigData> oreCrusher;
        public final Config<AlloySmelterConfig.ConfigData> alloySmelter;
        public final Config<QuarryConfig.ConfigData> quarry;

        /** Retains the native configuration handles that the MAC settings update. */
        public ConfigBundle(
                Config<BatteryUpgradeConfig.ConfigData> battery,
                Config<SolarUpgradeConfig.ConfigData> solar,
                Config<WindUpgradeConfig.ConfigData> wind,
                Config<CableUpgradeConfig.ConfigData> cable,
                Config<FurnaceConfig.ConfigData> furnace,
                Config<OreCrusherConfig.ConfigData> oreCrusher,
                Config<AlloySmelterConfig.ConfigData> alloySmelter,
                Config<QuarryConfig.ConfigData> quarry) {
            this.battery = battery;
            this.solar = solar;
            this.wind = wind;
            this.cable = cable;
            this.furnace = furnace;
            this.oreCrusher = oreCrusher;
            this.alloySmelter = alloySmelter;
            this.quarry = quarry;
        }
    }

    /** Registers the optional schema, merges its settings with defaults and applies them. */
    public static void applyIfPresent(JavaPlugin plugin, ConfigBundle bundle) {
        if (plugin == null || bundle == null) {
            return;
        }
        Class<?> pluginConfigClass = loadClass(PLUGIN_CONFIG_CLASS);
        if (pluginConfigClass == null) {
            return;
        }
        Class<?> configBuilderClass = loadClass(CONFIG_BUILDER_CLASS);
        Class<?> configFieldClass = loadClass(CONFIG_FIELD_CLASS);
        if (configBuilderClass == null || configFieldClass == null) {
            plugin.getLogger().atWarning().log("[HyProTech] MAC detected but missing config classes.");
            return;
        }
        MacSettings defaults = buildDefaults();
        Object pluginConfig;
        try {
            pluginConfig = registerConfig(
                    plugin,
                    pluginConfigClass,
                    configBuilderClass,
                    configFieldClass,
                    defaults);
        } catch (Exception ex) {
            plugin.getLogger().atWarning().log("[HyProTech] MAC config registration failed: %s", ex.getMessage());
            return;
        }
        // Preserve defaults for missing settings before updating MAC and the native configs.
        MacSettings settings = readSettings(pluginConfig);
        MacSettings merged = mergeSettings(settings, defaults);
        if (merged == null) {
            return;
        }
        persistMacConfig(plugin, pluginConfig, merged);
        applySettings(plugin, bundle, merged, defaults);
    }

    /** Returns null when an optional MAC class is not installed. */
    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /** Finds a compatible five-argument MAC registration overload via reflection. */
    private static Object registerConfig(
            JavaPlugin plugin,
            Class<?> pluginConfigClass,
            Class<?> configBuilderClass,
            Class<?> configFieldClass,
            MacSettings defaults) throws Exception {
        HytaleLogger logger = plugin.getLogger();
        PluginIdentifier identifier = new PluginIdentifier(plugin.getManifest());
        Supplier<MacSettings> supplier = () -> copySettings(defaults);
        Consumer<Object> builderConsumer =
                builder -> buildSchema(builder, configBuilderClass, configFieldClass, defaults);

        for (Method method : pluginConfigClass.getMethods()) {
            if (!method.getName().equals("register") || method.getParameterCount() != 5) {
                continue;
            }
            try {
                return method.invoke(
                        null,
                        logger,
                        identifier,
                        MacSettings.class,
                        supplier,
                        (Consumer<?>) builderConsumer);
            } catch (IllegalArgumentException ex) {
                // Try next overload.
            }
        }
        throw new IllegalStateException("PluginConfig.register overload not found");
    }

    /** Builds the Energy and Machines sections from current plugin defaults. */
    private static void buildSchema(
            Object builderObj,
            Class<?> configBuilderClass,
            Class<?> configFieldClass,
            MacSettings defaults) {
        if (builderObj == null) {
            return;
        }
        MacSchemaBuilder builder = new MacSchemaBuilder(builderObj, configBuilderClass, configFieldClass);
        MacFieldFactory fields = new MacFieldFactory(configBuilderClass, configFieldClass);

        builder.section("Energy");
        builder.objectField("Battery", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addBatterySchema(nestedBuilder, fields, defaults == null ? null : defaults.battery);
        });
        builder.objectField("Solar", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addSolarSchema(nestedBuilder, fields, defaults == null ? null : defaults.solar);
        });
        builder.objectField("Wind", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addWindSchema(nestedBuilder, fields, defaults == null ? null : defaults.wind);
        });
        builder.objectField("Cable", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addCableSchema(nestedBuilder, fields, defaults == null ? null : defaults.cable);
        });

        builder.section("Machines");
        builder.objectField("Furnace", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addFurnaceSchema(nestedBuilder, defaults == null ? null : defaults.furnace);
        });
        builder.objectField("Ore Crusher", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addOreCrusherSchema(nestedBuilder, fields, defaults == null ? null : defaults.oreCrusher);
        });
        builder.objectField("Alloy Smelter", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addAlloySmelterSchema(nestedBuilder, fields, defaults == null ? null : defaults.alloySmelter);
        });
        builder.objectField("Quarry", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addQuarrySchema(nestedBuilder, fields, defaults == null ? null : defaults.quarry);
        });
    }
    /** Exposes battery tier names, capacity, transfer rates and upgrade costs. */
    private static void addBatterySchema(MacSchemaBuilder builder, MacFieldFactory fields, MacSettings.Battery defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Battery safe = defaults != null ? defaults : new MacSettings.Battery();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Max Transfer", fields.numberField("Value"), safe.maxTransfer);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
    }

    /** Exposes solar tiers, generation, capacity and upgrade costs. */
    private static void addSolarSchema(MacSchemaBuilder builder, MacFieldFactory fields, MacSettings.Solar defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Solar safe = defaults != null ? defaults : new MacSettings.Solar();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Generation", fields.numberField("Value"), safe.generation);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
    }

    /** Exposes wind-generator tiers, generation, capacity and upgrade costs. */
    private static void addWindSchema(MacSchemaBuilder builder, MacFieldFactory fields, MacSettings.Wind defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Wind safe = defaults != null ? defaults : new MacSettings.Wind();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Generation", fields.numberField("Value"), safe.generation);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
    }

    /** Exposes energy and item cable limits and tier upgrade costs. */
    private static void addCableSchema(MacSchemaBuilder builder, MacFieldFactory fields, MacSettings.Cable defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Cable safe = defaults != null ? defaults : new MacSettings.Cable();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Energy Capacity", fields.numberField("Value"), safe.energyCapacity);
        builder.arrayField("Energy Max Transfer", fields.numberField("Value"), safe.energyMaxTransfer);
        builder.arrayField("Item Max Transfer", fields.numberField("Value"), safe.itemMaxTransfer);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
    }

    /** Exposes the electric furnace consumption multiplier. */
    private static void addFurnaceSchema(MacSchemaBuilder builder, MacSettings.Furnace defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Furnace safe = defaults != null ? defaults : new MacSettings.Furnace();
        Object field = builder.numberField("Consumption Multiplier");
        builder.defaultsTo(field, safe.consumptionMultiplier);
    }

    /** Exposes crusher processing, energy, upgrade and bonus-drop settings. */
    private static void addOreCrusherSchema(
            MacSchemaBuilder builder,
            MacFieldFactory fields,
            MacSettings.OreCrusher defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.OreCrusher safe = defaults != null ? defaults : new MacSettings.OreCrusher();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Consumption Per Second", fields.numberField("Value"), safe.consumptionPerSecond);
        builder.arrayField("Output Multiplier", fields.numberField("Value"), safe.outputMultiplier);
        builder.arrayField("Processing Seconds", fields.numberField("Value"), safe.processingSeconds);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
        builder.arrayField("Bonus Drops", fields.bonusDropField(), safe.bonusDrops);
        builder.objectField("Powder Bonus Drop", nested -> {
            MacSchemaBuilder nestedBuilder = builder.child(nested);
            addBonusDropFields(nestedBuilder, safe.powderBonusDrop);
        });
    }

    /** Exposes alloy-smelter processing, energy, upgrade and bonus-drop settings. */
    private static void addAlloySmelterSchema(
            MacSchemaBuilder builder,
            MacFieldFactory fields,
            MacSettings.AlloySmelter defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.AlloySmelter safe = defaults != null ? defaults : new MacSettings.AlloySmelter();
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Consumption Per Second", fields.numberField("Value"), safe.consumptionPerSecond);
        builder.arrayField("Output Multiplier", fields.numberField("Value"), safe.outputMultiplier);
        builder.arrayField("Processing Seconds", fields.numberField("Value"), safe.processingSeconds);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
        builder.arrayField("Bonus Drops", fields.bonusDropField(), safe.bonusDrops);
    }

    /** Exposes quarry dimensions, speed, energy, backfill timing and upgrade costs. */
    private static void addQuarrySchema(
            MacSchemaBuilder builder,
            MacFieldFactory fields,
            MacSettings.Quarry defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.Quarry safe = defaults != null ? defaults : new MacSettings.Quarry();
        Object baseField = builder.numberField("Base Area");
        builder.defaultsTo(baseField, safe.baseArea);
        Object minField = builder.numberField("Min Area");
        builder.defaultsTo(minField, safe.minArea);
        Object basicField = builder.numberField("Basic Speed Seconds");
        builder.defaultsTo(basicField, safe.basicSpeedSeconds);
        Object quantumField = builder.numberField("Quantum Speed Seconds");
        builder.defaultsTo(quantumField, safe.quantumSpeedSeconds);
        Object backfillPlaceField = builder.numberField("Backfill Place Delay Ticks");
        builder.defaultsTo(backfillPlaceField, safe.backfillPlaceDelayTicks);
        Object backfillPostField = builder.numberField("Backfill Post Delay Ticks");
        builder.defaultsTo(backfillPostField, safe.backfillPostDelayTicks);
        builder.arrayField("Tier Names", fields.stringField("Name"), safe.tierNames);
        builder.arrayField("Capacity", fields.numberField("Value"), safe.capacity);
        builder.arrayField("Consumption Per Second", fields.numberField("Value"), safe.consumptionPerSecond);
        builder.arrayField("Max Area", fields.numberField("Value"), safe.maxArea);
        builder.arrayField("Upgrade Requirements", fields.requirementGroupField(), safe.upgradeRequirements);
        Object forceField = builder.booleanField("Force Replace Blocks");
        builder.defaultsTo(forceField, safe.forceReplaceBlocks);
    }

    /** Defines the item, quantity and chance fields shared by bonus-drop objects. */
    private static void addBonusDropFields(MacSchemaBuilder builder, MacSettings.BonusDrop defaults) {
        if (builder == null) {
            return;
        }
        MacSettings.BonusDrop safe = defaults != null ? defaults : new MacSettings.BonusDrop();
        Object itemField = builder.stringField("Item Id");
        builder.defaultsTo(itemField, safe.itemId);
        Object qtyField = builder.numberField("Quantity");
        builder.defaultsTo(qtyField, safe.quantity);
        Object baseField = builder.numberField("Base Chance");
        builder.defaultsTo(baseField, safe.baseChance);
        Object perField = builder.numberField("Per Tier Chance");
        builder.defaultsTo(perField, safe.perTierChance);
    }
    /** Creates reusable primitive and nested MAC fields through reflection. */
    private static final class MacFieldFactory {
        private final Class<?> configBuilderClass;
        private final Class<?> configFieldClass;

        private MacFieldFactory(Class<?> configBuilderClass, Class<?> configFieldClass) {
            this.configBuilderClass = configBuilderClass;
            this.configFieldClass = configFieldClass;
        }

        /** Creates a reusable MAC string field through the reflected factory. */
        Object stringField(String label) {
            return invokeStatic(configFieldClass, "stringField", label);
        }

        /** Creates a reusable MAC numeric field through the reflected factory. */
        Object numberField(String label) {
            return invokeStatic(configFieldClass, "numberField", label);
        }

        /** Builds a group containing an array of item-and-quantity requirements. */
        Object requirementGroupField() {
            Object requirementField = buildObjectField("Requirement", builder -> {
                MacSchemaBuilder schema = new MacSchemaBuilder(builder, configBuilderClass, configFieldClass);
                Object itemField = schema.stringField("Item Id");
                schema.defaultsTo(itemField, "");
                Object qtyField = schema.numberField("Quantity");
                schema.defaultsTo(qtyField, 0);
            });
            if (requirementField == null) {
                return null;
            }
            return buildObjectField("Requirement Group", builder -> {
                MacSchemaBuilder schema = new MacSchemaBuilder(builder, configBuilderClass, configFieldClass);
                schema.arrayField("Items", requirementField, null);
            });
        }

        /** Builds an object field for one bonus-drop rule. */
        Object bonusDropField() {
            return buildObjectField("Bonus Drop", builder -> {
                MacSchemaBuilder schema = new MacSchemaBuilder(builder, configBuilderClass, configFieldClass);
                addBonusDropFields(schema, null);
            });
        }

        /** Builds a nested schema before wrapping it as an object field. */
        private Object buildObjectField(String label, Consumer<Object> nestedConsumer) {
            Object nestedBuilder = newConfigBuilder(configBuilderClass);
            if (nestedBuilder == null) {
                return null;
            }
            nestedConsumer.accept(nestedBuilder);
            Object built = buildConfigBuilder(nestedBuilder);
            if (built == null) {
                return null;
            }
            return invokeStatic(configFieldClass, "objectField", label, built);
        }
    }

    /** Adapts MAC builder and field APIs that may differ between installed versions. */
    private static final class MacSchemaBuilder {
        private final Object builder;
        private final Object adder;
        private final Class<?> configBuilderClass;
        private final Class<?> configFieldClass;

        private MacSchemaBuilder(Object builder, Class<?> configBuilderClass, Class<?> configFieldClass) {
            this.builder = builder;
            this.configBuilderClass = configBuilderClass;
            this.configFieldClass = configFieldClass;
            this.adder = resolveAdder(builder);
        }

        /** Wraps a nested schema builder with the same reflected MAC types. */
        MacSchemaBuilder child(Object nested) {
            return new MacSchemaBuilder(nested, configBuilderClass, configFieldClass);
        }

        /** Adds a named section through the resolved builder facade. */
        void section(String title) {
            invoke(adder, "section", title);
        }

        /** Adds a string field through the resolved builder facade. */
        Object stringField(String label) {
            return invoke(adder, "stringField", label);
        }

        /** Adds a numeric field through the resolved builder facade. */
        Object numberField(String label) {
            return invoke(adder, "numberField", label);
        }

        /** Adds a boolean field through the resolved builder facade. */
        Object booleanField(String label) {
            return invoke(adder, "booleanField", label);
        }

        /** Adds an array only when its element field was created successfully. */
        Object arrayField(String label, Object elementField) {
            if (elementField == null) {
                return null;
            }
            return invoke(adder, "arrayField", label, elementField);
        }

        /** Adds an array field and tries to apply its default values. */
        void arrayField(String label, Object elementField, Object defaults) {
            Object field = arrayField(label, elementField);
            defaultsTo(field, defaults);
        }

        /** Supports object-field overloads accepting either a consumer or a built schema. */
        void objectField(String label, Consumer<Object> nestedConsumer) {
            if (adder == null || nestedConsumer == null) {
                return;
            }
            Method method = findObjectFieldMethod(adder.getClass());
            if (method == null) {
                return;
            }
            Class<?> paramType = method.getParameterTypes()[1];
            try {
                if (Consumer.class.isAssignableFrom(paramType)) {
                    method.invoke(adder, label, (Consumer<Object>) nestedConsumer);
                    return;
                }
                Object nestedBuilder = newConfigBuilder(configBuilderClass);
                if (nestedBuilder == null) {
                    return;
                }
                nestedConsumer.accept(nestedBuilder);
                Object arg = nestedBuilder;
                if (!paramType.isInstance(nestedBuilder)) {
                    Object built = buildConfigBuilder(nestedBuilder);
                    if (built != null) {
                        arg = built;
                    }
                }
                method.invoke(adder, label, arg);
            } catch (Exception ex) {
                // Ignore schema errors.
            }
        }

        /** Tries the original default value, then compatible array representations for lists. */
        void defaultsTo(Object field, Object value) {
            if (field == null || value == null) {
                return;
            }
            if (invokeDefaults(field, value)) {
                return;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                Object typedArray = listToTypedArray(list);
                if (typedArray != null && invokeDefaults(field, typedArray)) {
                    return;
                }
                if (tryPrimitiveDefaults(field, list)) {
                    return;
                }
                Object objectArray = list.toArray();
                invokeDefaults(field, objectArray);
                return;
            }
            if (value.getClass().isArray()) {
                Object boxed = boxPrimitiveArray(value);
                if (boxed != null) {
                    invokeDefaults(field, boxed);
                }
            }
        }
    }

    /** Instantiates an optional MAC builder, returning null if its API is unavailable. */
    private static Object newConfigBuilder(Class<?> configBuilderClass) {
        if (configBuilderClass == null) {
            return null;
        }
        try {
            return configBuilderClass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Calls the optional builder's no-argument build method. */
    private static Object buildConfigBuilder(Object builder) {
        if (builder == null) {
            return null;
        }
        try {
            Method method = builder.getClass().getMethod("build");
            return method.invoke(builder);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Finds the builder's add facade through a field or method, falling back to the builder. */
    private static Object resolveAdder(Object builder) {
        if (builder == null) {
            return null;
        }
        try {
            Field field = builder.getClass().getField("add");
            return field.get(builder);
        } catch (Exception ex) {
            // ignore
        }
        try {
            Field field = builder.getClass().getDeclaredField("add");
            field.setAccessible(true);
            return field.get(builder);
        } catch (Exception ex) {
            // ignore
        }
        try {
            Method method = builder.getClass().getMethod("add");
            if (method.getParameterCount() == 0) {
                return method.invoke(builder);
            }
        } catch (Exception ex) {
            // ignore
        }
        return builder;
    }

    /** Prefers the consumer-based objectField overload, with another two-argument fallback. */
    private static Method findObjectFieldMethod(Class<?> type) {
        Method fallback = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("objectField") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[1];
            if (Consumer.class.isAssignableFrom(param)) {
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        return fallback;
    }

    /** Invokes a compatible public instance method, returning null on lookup or invocation failure. */
    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = findCompatibleMethod(target.getClass(), name, args);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Invokes a compatible public static method, returning null if it cannot be used. */
    private static Object invokeStatic(Class<?> type, String name, Object... args) {
        if (type == null) {
            return null;
        }
        Method method = findCompatibleMethod(type, name, args);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Finds the first public overload whose parameter types accept the supplied arguments. */
    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) {
                if (!isCompatible(params[i], args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    /** Matches null only to reference types and compares primitive parameters through wrappers. */
    private static boolean isCompatible(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive();
        }
        Class<?> wrapped = wrapPrimitive(paramType);
        return wrapped.isAssignableFrom(arg.getClass());
    }

    /** Maps Java primitive types to their reflection argument wrapper classes. */
    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    /** Tries a compatible defaultsTo overload and reports whether it was invoked. */
    private static boolean invokeDefaults(Object field, Object value) {
        if (field == null) {
            return false;
        }
        Method method = findCompatibleMethod(field.getClass(), "defaultsTo", new Object[]{value});
        if (method == null) {
            return false;
        }
        try {
            method.invoke(field, value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Converts a list to an array based on its first non-null element's type. */
    private static Object listToTypedArray(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Class<?> elementType = null;
        for (Object item : list) {
            if (item != null) {
                elementType = item.getClass();
                break;
            }
        }
        if (elementType == null) {
            return null;
        }
        Object array = Array.newInstance(elementType, list.size());
        for (int i = 0; i < list.size(); i++) {
            Array.set(array, i, list.get(i));
        }
        return array;
    }

    /** Tries primitive array overloads when a MAC field does not accept the original list. */
    private static boolean tryPrimitiveDefaults(Object field, List<?> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        Object sample = null;
        for (Object item : list) {
            if (item != null) {
                sample = item;
                break;
            }
        }
        if (sample == null) {
            return false;
        }
        if (sample instanceof Number) {
            int[] intArray = new int[list.size()];
            long[] longArray = new long[list.size()];
            double[] doubleArray = new double[list.size()];
            float[] floatArray = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    Number number = (Number) item;
                    intArray[i] = number.intValue();
                    longArray[i] = number.longValue();
                    doubleArray[i] = number.doubleValue();
                    floatArray[i] = number.floatValue();
                }
            }
            if (invokeDefaults(field, intArray)) {
                return true;
            }
            if (invokeDefaults(field, longArray)) {
                return true;
            }
            if (invokeDefaults(field, doubleArray)) {
                return true;
            }
            return invokeDefaults(field, floatArray);
        }
        if (sample instanceof Boolean) {
            boolean[] boolArray = new boolean[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Boolean) {
                    boolArray[i] = (Boolean) item;
                }
            }
            return invokeDefaults(field, boolArray);
        }
        if (sample instanceof Character) {
            char[] charArray = new char[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Character) {
                    charArray[i] = (Character) item;
                }
            }
            return invokeDefaults(field, charArray);
        }
        return false;
    }

    /** Copies a primitive array into a boxed object array for alternate overloads. */
    private static Object boxPrimitiveArray(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        if (!type.isArray() || !type.getComponentType().isPrimitive()) {
            return null;
        }
        int length = Array.getLength(value);
        Object[] boxed = new Object[length];
        for (int i = 0; i < length; i++) {
            boxed[i] = Array.get(value, i);
        }
        return boxed;
    }

    /** Finds a settings accessor by common names, then by a compatible return type. */
    private static MacSettings readSettings(Object pluginConfig) {
        if (pluginConfig == null) {
            return null;
        }
        Method candidate = null;
        String[] preferred = {"get", "getSettings", "getConfig", "getValue", "value"};
        for (String name : preferred) {
            for (Method method : pluginConfig.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    candidate = method;
                    break;
                }
            }
            if (candidate != null) {
                break;
            }
        }
        if (candidate == null) {
            for (Method method : pluginConfig.getClass().getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (MacSettings.class.isAssignableFrom(returnType)
                        || Optional.class.isAssignableFrom(returnType)
                        || CompletableFuture.class.isAssignableFrom(returnType)) {
                    candidate = method;
                    break;
                }
            }
        }
        if (candidate == null) {
            return null;
        }
        try {
            Object value = candidate.invoke(pluginConfig);
            return unwrapSettings(value);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Unwraps direct or optional settings, waiting for a future when necessary. */
    private static MacSettings unwrapSettings(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MacSettings) {
            return (MacSettings) value;
        }
        if (value instanceof Optional) {
            Object inner = ((Optional<?>) value).orElse(null);
            return unwrapSettings(inner);
        }
        if (value instanceof CompletableFuture) {
            Object inner = ((CompletableFuture<?>) value).join();
            return unwrapSettings(inner);
        }
        return null;
    }

    /** Converts each settings section into native config data, applies it and saves it. */
    private static void applySettings(
            JavaPlugin plugin,
            ConfigBundle bundle,
            MacSettings settings,
            MacSettings defaults) {
        BatteryUpgradeConfig.ConfigData batteryData = buildBatteryConfig(settings, defaults);
        BatteryUpgradeConfig.applyConfig(batteryData);
        persistConfig(plugin, "battery-upgrades", bundle.battery, batteryData);

        SolarUpgradeConfig.ConfigData solarData = buildSolarConfig(settings, defaults);
        SolarUpgradeConfig.applyConfig(solarData);
        persistConfig(plugin, "solar-upgrades", bundle.solar, solarData);

        WindUpgradeConfig.ConfigData windData = buildWindConfig(settings, defaults);
        WindUpgradeConfig.applyConfig(windData);
        persistConfig(plugin, "wind-upgrades", bundle.wind, windData);

        CableUpgradeConfig.ConfigData cableData = buildCableConfig(settings, defaults);
        CableUpgradeConfig.applyConfig(cableData);
        persistConfig(plugin, "cable-upgrades", bundle.cable, cableData);

        FurnaceConfig.ConfigData furnaceData = buildFurnaceConfig(settings, defaults);
        FurnaceConfig.applyConfig(furnaceData);
        persistConfig(plugin, "furnace", bundle.furnace, furnaceData);

        OreCrusherConfig.ConfigData oreCrusherData = buildOreCrusherConfig(settings, defaults);
        OreCrusherConfig.applyConfig(oreCrusherData);
        persistConfig(plugin, "ore-crusher", bundle.oreCrusher, oreCrusherData);

        AlloySmelterConfig.ConfigData alloySmelterData = buildAlloySmelterConfig(settings, defaults);
        AlloySmelterConfig.applyConfig(alloySmelterData);
        persistConfig(plugin, "alloy-smelter", bundle.alloySmelter, alloySmelterData);

        QuarryConfig.ConfigData quarryData = buildQuarryConfig(settings, defaults);
        QuarryConfig.applyConfig(quarryData);
        persistConfig(plugin, "quarry", bundle.quarry, quarryData);
    }

    /** Writes merged settings back to MAC and requests persistence when supported. */
    private static void persistMacConfig(JavaPlugin plugin, Object pluginConfig, MacSettings settings) {
        if (pluginConfig == null || settings == null) {
            return;
        }
        boolean updated = setMacConfigValue(pluginConfig, settings);
        boolean saved = saveMacConfig(plugin, pluginConfig);
        if (!updated && !saved && plugin != null) {
            plugin.getLogger().atWarning().log("[HyProTech] Unable to update MAC config values.");
        }
    }

    /** Tries known setter names and then known backing fields across MAC versions. */
    private static boolean setMacConfigValue(Object pluginConfig, MacSettings settings) {
        if (pluginConfig == null || settings == null) {
            return false;
        }
        String[] methodNames = {
                "set",
                "setSettings",
                "setConfig",
                "setValue",
                "update",
                "replace"
        };
        for (String name : methodNames) {
            Method method = findCompatibleMethod(pluginConfig.getClass(), name, new Object[]{settings});
            if (method == null) {
                continue;
            }
            try {
                method.invoke(pluginConfig, settings);
                return true;
            } catch (Exception ex) {
                // Try next method.
            }
        }
        String[] fieldNames = {"settings", "config", "value"};
        for (String fieldName : fieldNames) {
            try {
                Field field = pluginConfig.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.getType().isAssignableFrom(MacSettings.class)) {
                    field.set(pluginConfig, settings);
                    return true;
                }
            } catch (Exception ex) {
                // Try next field.
            }
        }
        return false;
    }

    /** Invokes a supported save method and logs asynchronous failures when possible. */
    private static boolean saveMacConfig(JavaPlugin plugin, Object pluginConfig) {
        String[] methodNames = {"save", "saveAsync", "write", "writeAsync", "flush"};
        for (String name : methodNames) {
            Method method = findCompatibleMethod(pluginConfig.getClass(), name, new Object[0]);
            if (method == null) {
                continue;
            }
            try {
                Object result = method.invoke(pluginConfig);
                if (result instanceof CompletableFuture) {
                    ((CompletableFuture<?>) result).exceptionally(ex -> {
                        if (plugin != null) {
                            plugin.getLogger().atWarning().log(
                                    "[HyProTech] Failed to save MAC config: %s",
                                    ex.getMessage());
                        }
                        return null;
                    });
                }
                return true;
            } catch (Exception ex) {
                if (plugin != null) {
                    plugin.getLogger().atWarning().log(
                            "[HyProTech] Failed to save MAC config: %s",
                            ex.getMessage());
                }
                return false;
            }
        }
        return false;
    }

    /** Updates a native config handle and reports failures from its asynchronous save. */
    private static <T> void persistConfig(JavaPlugin plugin, String label, Config<T> config, T data) {
        if (config == null || data == null) {
            return;
        }
        boolean updated = setConfigValue(config, data);
        if (!updated && plugin != null) {
            plugin.getLogger().atWarning().log("[HyProTech] Unable to update config %s from MAC settings.", label);
        }
        config.save().exceptionally(ex -> {
            if (plugin != null) {
                plugin.getLogger().atWarning().log(
                        "[HyProTech] Failed to save config %s: %s",
                        label,
                        ex.getMessage());
            }
            return null;
        });
    }

    /** Replaces the native config's backing value through reflection. */
    private static <T> boolean setConfigValue(Config<T> config, T data) {
        try {
            Field field = config.getClass().getDeclaredField("config");
            field.setAccessible(true);
            field.set(config, data);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Constructs a machine-specific requirement from normalized MAC fields. */
    private interface RequirementFactory<R> {
        /** Creates one requirement for the given item and quantity. */
        R create(String itemId, int quantity);
    }

    /** Constructs a machine-specific bonus-drop rule from normalized MAC fields. */
    private interface BonusDropFactory<D> {
        /** Creates one bonus-drop rule with base and per-tier chance values. */
        D create(String itemId, int quantity, double baseChance, double perTierChance);
    }

    /** Converts battery tiers, energy limits and costs into the native config representation. */
    private static BatteryUpgradeConfig.ConfigData buildBatteryConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Battery value = settings == null ? null : settings.battery;
        MacSettings.Battery base = defaults == null ? null : defaults.battery;
        int tierCount = BatteryUpgradeConfig.MAX_TIER - BatteryUpgradeConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] maxTransfer = mergeIntList(
                value == null ? null : value.maxTransfer,
                base == null ? null : base.maxTransfer,
                tierCount,
                0);
        BatteryUpgradeConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                BatteryUpgradeConfig.Requirement::new,
                BatteryUpgradeConfig.Requirement.class);

        BatteryUpgradeConfig.ConfigData data = new BatteryUpgradeConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "maxTransfer", maxTransfer);
        setField(data, "upgradeRequirements", requirements);
        return data;
    }

    /** Converts solar tiers, generation and upgrade costs into native config data. */
    private static SolarUpgradeConfig.ConfigData buildSolarConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Solar value = settings == null ? null : settings.solar;
        MacSettings.Solar base = defaults == null ? null : defaults.solar;
        int tierCount = SolarUpgradeConfig.MAX_TIER - SolarUpgradeConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] generation = mergeIntList(
                value == null ? null : value.generation,
                base == null ? null : base.generation,
                tierCount,
                0);
        SolarUpgradeConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                SolarUpgradeConfig.Requirement::new,
                SolarUpgradeConfig.Requirement.class);

        SolarUpgradeConfig.ConfigData data = new SolarUpgradeConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "generation", generation);
        setField(data, "upgradeRequirements", requirements);
        return data;
    }

    /** Converts wind tiers, generation and upgrade costs into native config data. */
    private static WindUpgradeConfig.ConfigData buildWindConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Wind value = settings == null ? null : settings.wind;
        MacSettings.Wind base = defaults == null ? null : defaults.wind;
        int tierCount = WindUpgradeConfig.MAX_TIER - WindUpgradeConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] generation = mergeIntList(
                value == null ? null : value.generation,
                base == null ? null : base.generation,
                tierCount,
                0);
        WindUpgradeConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                WindUpgradeConfig.Requirement::new,
                WindUpgradeConfig.Requirement.class);

        WindUpgradeConfig.ConfigData data = new WindUpgradeConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "generation", generation);
        setField(data, "upgradeRequirements", requirements);
        return data;
    }

    /** Converts cable tier transfer limits and upgrade costs into native config data. */
    private static CableUpgradeConfig.ConfigData buildCableConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Cable value = settings == null ? null : settings.cable;
        MacSettings.Cable base = defaults == null ? null : defaults.cable;
        int tierCount = CableUpgradeConfig.MAX_TIER - CableUpgradeConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] energyCapacity = mergeIntList(
                value == null ? null : value.energyCapacity,
                base == null ? null : base.energyCapacity,
                tierCount,
                0);
        int[] energyMaxTransfer = mergeIntList(
                value == null ? null : value.energyMaxTransfer,
                base == null ? null : base.energyMaxTransfer,
                tierCount,
                0);
        int[] itemMaxTransfer = mergeIntList(
                value == null ? null : value.itemMaxTransfer,
                base == null ? null : base.itemMaxTransfer,
                tierCount,
                0);
        CableUpgradeConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                CableUpgradeConfig.Requirement::new,
                CableUpgradeConfig.Requirement.class);

        CableUpgradeConfig.ConfigData data = new CableUpgradeConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "energyCapacity", energyCapacity);
        setField(data, "energyMaxTransfer", energyMaxTransfer);
        setField(data, "itemMaxTransfer", itemMaxTransfer);
        setField(data, "upgradeRequirements", requirements);
        return data;
    }

    /** Converts the furnace consumption multiplier into native config data. */
    private static FurnaceConfig.ConfigData buildFurnaceConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Furnace value = settings == null ? null : settings.furnace;
        MacSettings.Furnace base = defaults == null ? null : defaults.furnace;
        Integer multiplier = value != null && value.consumptionMultiplier != null
                ? value.consumptionMultiplier
                : base == null ? null : base.consumptionMultiplier;

        FurnaceConfig.ConfigData data = new FurnaceConfig.ConfigData();
        setField(data, "consumptionMultiplier", multiplier);
        return data;
    }

    /** Converts crusher tiers, processing parameters, costs and bonus drops. */
    private static OreCrusherConfig.ConfigData buildOreCrusherConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.OreCrusher value = settings == null ? null : settings.oreCrusher;
        MacSettings.OreCrusher base = defaults == null ? null : defaults.oreCrusher;
        int tierCount = OreCrusherConfig.MAX_TIER - OreCrusherConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] consumptionPerSecond = mergeIntList(
                value == null ? null : value.consumptionPerSecond,
                base == null ? null : base.consumptionPerSecond,
                tierCount,
                0);
        int[] outputMultiplier = mergeIntList(
                value == null ? null : value.outputMultiplier,
                base == null ? null : base.outputMultiplier,
                tierCount,
                1);
        double[] processingSeconds = mergeDoubleList(
                value == null ? null : value.processingSeconds,
                base == null ? null : base.processingSeconds,
                tierCount,
                0.01);

        OreCrusherConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                OreCrusherConfig.Requirement::new,
                OreCrusherConfig.Requirement.class);

        OreCrusherConfig.BonusDrop[] bonusDrops = buildBonusDropArray(
                value == null ? null : value.bonusDrops,
                base == null ? null : base.bonusDrops,
                OreCrusherConfig.BonusDrop::new,
                OreCrusherConfig.BonusDrop.class);

        OreCrusherConfig.BonusDrop powderDrop = buildSingleBonusDrop(
                value == null ? null : value.powderBonusDrop,
                base == null ? null : base.powderBonusDrop,
                OreCrusherConfig.BonusDrop::new);

        OreCrusherConfig.ConfigData data = new OreCrusherConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "consumptionPerSecond", consumptionPerSecond);
        setField(data, "outputMultiplier", outputMultiplier);
        setField(data, "processingSeconds", processingSeconds);
        setField(data, "upgradeRequirements", requirements);
        setField(data, "bonusDrops", bonusDrops);
        setField(data, "powderBonusDrop", powderDrop);
        return data;
    }

    /** Converts smelter tiers, processing parameters, costs and bonus drops. */
    private static AlloySmelterConfig.ConfigData buildAlloySmelterConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.AlloySmelter value = settings == null ? null : settings.alloySmelter;
        MacSettings.AlloySmelter base = defaults == null ? null : defaults.alloySmelter;
        int tierCount = AlloySmelterConfig.MAX_TIER - AlloySmelterConfig.MIN_TIER + 1;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] consumptionPerSecond = mergeIntList(
                value == null ? null : value.consumptionPerSecond,
                base == null ? null : base.consumptionPerSecond,
                tierCount,
                0);
        int[] outputMultiplier = mergeIntList(
                value == null ? null : value.outputMultiplier,
                base == null ? null : base.outputMultiplier,
                tierCount,
                1);
        double[] processingSeconds = mergeDoubleList(
                value == null ? null : value.processingSeconds,
                base == null ? null : base.processingSeconds,
                tierCount,
                0.01);

        AlloySmelterConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                AlloySmelterConfig.Requirement::new,
                AlloySmelterConfig.Requirement.class);

        AlloySmelterConfig.BonusDrop[] bonusDrops = buildBonusDropArray(
                value == null ? null : value.bonusDrops,
                base == null ? null : base.bonusDrops,
                AlloySmelterConfig.BonusDrop::new,
                AlloySmelterConfig.BonusDrop.class);

        AlloySmelterConfig.ConfigData data = new AlloySmelterConfig.ConfigData();
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "consumptionPerSecond", consumptionPerSecond);
        setField(data, "outputMultiplier", outputMultiplier);
        setField(data, "processingSeconds", processingSeconds);
        setField(data, "upgradeRequirements", requirements);
        setField(data, "bonusDrops", bonusDrops);
        return data;
    }

    /** Converts quarry area, timing, energy, replacement and tier settings. */
    private static QuarryConfig.ConfigData buildQuarryConfig(
            MacSettings settings,
            MacSettings defaults) {
        MacSettings.Quarry value = settings == null ? null : settings.quarry;
        MacSettings.Quarry base = defaults == null ? null : defaults.quarry;
        int tierCount = QuarryConfig.MAX_TIER - QuarryConfig.MIN_TIER + 1;

        Integer baseArea = value != null && value.baseArea != null
                ? value.baseArea
                : base == null ? null : base.baseArea;
        Integer minArea = value != null && value.minArea != null
                ? value.minArea
                : base == null ? null : base.minArea;
        Double basicSpeed = value != null && value.basicSpeedSeconds != null
                ? value.basicSpeedSeconds
                : base == null ? null : base.basicSpeedSeconds;
        Double quantumSpeed = value != null && value.quantumSpeedSeconds != null
                ? value.quantumSpeedSeconds
                : base == null ? null : base.quantumSpeedSeconds;
        Integer backfillPlaceDelay = value != null && value.backfillPlaceDelayTicks != null
                ? value.backfillPlaceDelayTicks
                : base == null ? null : base.backfillPlaceDelayTicks;
        Integer backfillPostDelay = value != null && value.backfillPostDelayTicks != null
                ? value.backfillPostDelayTicks
                : base == null ? null : base.backfillPostDelayTicks;
        Boolean forceReplace = value != null && value.forceReplaceBlocks != null
                ? value.forceReplaceBlocks
                : base == null ? null : base.forceReplaceBlocks;

        String[] tierNames = mergeStringList(
                value == null ? null : value.tierNames,
                base == null ? null : base.tierNames,
                tierCount);
        int[] capacity = mergeIntList(
                value == null ? null : value.capacity,
                base == null ? null : base.capacity,
                tierCount,
                0);
        int[] consumptionPerSecond = mergeIntList(
                value == null ? null : value.consumptionPerSecond,
                base == null ? null : base.consumptionPerSecond,
                tierCount,
                0);
        int minAreaValue = minArea != null ? Math.max(1, minArea) : Math.max(1, QuarryConfig.MIN_AREA);
        int[] maxArea = mergeIntList(
                value == null ? null : value.maxArea,
                base == null ? null : base.maxArea,
                tierCount,
                minAreaValue);

        QuarryConfig.Requirement[][] requirements = buildRequirementTable(
                value == null ? null : value.upgradeRequirements,
                base == null ? null : base.upgradeRequirements,
                tierCount,
                QuarryConfig.Requirement::new,
                QuarryConfig.Requirement.class);

        QuarryConfig.ConfigData data = new QuarryConfig.ConfigData();
        setField(data, "baseArea", baseArea);
        setField(data, "minArea", minArea);
        setField(data, "basicSpeedSeconds", basicSpeed);
        setField(data, "quantumSpeedSeconds", quantumSpeed);
        setField(data, "backfillPlaceDelayTicks", backfillPlaceDelay);
        setField(data, "backfillPostDelayTicks", backfillPostDelay);
        setField(data, "tierNames", tierNames);
        setField(data, "capacity", capacity);
        setField(data, "consumptionPerSecond", consumptionPerSecond);
        setField(data, "maxArea", maxArea);
        setField(data, "upgradeRequirements", requirements);
        setField(data, "forceReplaceBlocks", forceReplace);
        return data;
    }
    /** Sets a native config field reflectively when the field and value are available. */
    private static void setField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null || value == null) {
            return;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            // ignore
        }
    }

    /** Builds one normalized requirement array per tier, using the matching default when needed. */
    private static <R> R[][] buildRequirementTable(
            List<MacSettings.RequirementGroup> value,
            List<MacSettings.RequirementGroup> defaults,
            int length,
            RequirementFactory<R> factory,
            Class<R> type) {
        @SuppressWarnings("unchecked")
        R[][] table = (R[][]) Array.newInstance(type, length, 0);
        for (int i = 0; i < length; i++) {
            List<MacSettings.Requirement> items = resolveRequirementItems(value, defaults, i);
            table[i] = toRequirementArray(items, factory, type);
        }
        return table;
    }

    /** Selects the requested tier's requirements, falling back to the default group. */
    private static List<MacSettings.Requirement> resolveRequirementItems(
            List<MacSettings.RequirementGroup> value,
            List<MacSettings.RequirementGroup> defaults,
            int index) {
        MacSettings.RequirementGroup group = getListItem(value, index);
        if (group == null || group.items == null) {
            group = getListItem(defaults, index);
        }
        if (group == null || group.items == null) {
            return Collections.emptyList();
        }
        return group.items;
    }

    /** Drops invalid requirements and converts valid item IDs and quantities with the factory. */
    private static <R> R[] toRequirementArray(
            List<MacSettings.Requirement> items,
            RequirementFactory<R> factory,
            Class<R> type) {
        if (items == null || items.isEmpty()) {
            @SuppressWarnings("unchecked")
            R[] empty = (R[]) Array.newInstance(type, 0);
            return empty;
        }
        List<R> result = new ArrayList<>(items.size());
        for (MacSettings.Requirement item : items) {
            if (item == null) {
                continue;
            }
            String itemId = normalizeItemId(item.itemId);
            int quantity = normalizeQuantity(item.quantity);
            if (itemId.isEmpty() || quantity <= 0) {
                continue;
            }
            result.add(factory.create(itemId, quantity));
        }
        @SuppressWarnings("unchecked")
        R[] array = (R[]) Array.newInstance(type, result.size());
        return result.toArray(array);
    }

    /** Converts bonus drops, retrying defaults when a supplied list has no valid entries. */
    private static <D> D[] buildBonusDropArray(
            List<MacSettings.BonusDrop> value,
            List<MacSettings.BonusDrop> defaults,
            BonusDropFactory<D> factory,
            Class<D> type) {
        List<MacSettings.BonusDrop> source = value != null ? value : defaults;
        D[] result = toBonusDropArray(source, factory, type);
        if (result.length == 0 && value != null && defaults != null) {
            result = toBonusDropArray(defaults, factory, type);
        }
        return result;
    }

    /** Converts one bonus drop, retrying the default if the supplied value is invalid. */
    private static <D> D buildSingleBonusDrop(
            MacSettings.BonusDrop value,
            MacSettings.BonusDrop defaults,
            BonusDropFactory<D> factory) {
        MacSettings.BonusDrop source = value != null ? value : defaults;
        D result = toBonusDrop(source, factory);
        if (result == null && value != null && defaults != null) {
            result = toBonusDrop(defaults, factory);
        }
        return result;
    }

    /** Converts valid bonus-drop rules to the machine-specific array type. */
    private static <D> D[] toBonusDropArray(
            List<MacSettings.BonusDrop> items,
            BonusDropFactory<D> factory,
            Class<D> type) {
        if (items == null || items.isEmpty()) {
            @SuppressWarnings("unchecked")
            D[] empty = (D[]) Array.newInstance(type, 0);
            return empty;
        }
        List<D> result = new ArrayList<>(items.size());
        for (MacSettings.BonusDrop drop : items) {
            D built = toBonusDrop(drop, factory);
            if (built != null) {
                result.add(built);
            }
        }
        @SuppressWarnings("unchecked")
        D[] array = (D[]) Array.newInstance(type, result.size());
        return result.toArray(array);
    }

    /** Normalizes a drop rule and rejects an empty item ID or nonpositive quantity. */
    private static <D> D toBonusDrop(MacSettings.BonusDrop drop, BonusDropFactory<D> factory) {
        if (drop == null) {
            return null;
        }
        String itemId = normalizeItemId(drop.itemId);
        int quantity = normalizeQuantity(drop.quantity);
        double baseChance = normalizeChance(drop.baseChance);
        double perTierChance = normalizeChance(drop.perTierChance);
        if (itemId.isEmpty() || quantity <= 0) {
            return null;
        }
        return factory.create(itemId, quantity, baseChance, perTierChance);
    }

    /** Fills a fixed-size string array from defaults, overriding with nonempty supplied values. */
    private static String[] mergeStringList(List<String> value, List<String> defaults, int length) {
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            String fallback = normalizeItemId(getListItem(defaults, i));
            result[i] = fallback;
        }
        if (value != null) {
            int limit = Math.min(value.size(), length);
            for (int i = 0; i < limit; i++) {
                String item = normalizeItemId(value.get(i));
                if (!item.isEmpty()) {
                    result[i] = item;
                }
            }
        }
        return result;
    }

    /** Fills a fixed-size integer array and clamps supplied and default values to a minimum. */
    private static int[] mergeIntList(List<Integer> value, List<Integer> defaults, int length, int minValue) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            Integer fallbackValue = getListItem(defaults, i);
            int fallback = fallbackValue == null ? minValue : Math.max(minValue, fallbackValue);
            result[i] = fallback;
        }
        if (value != null) {
            int limit = Math.min(value.size(), length);
            for (int i = 0; i < limit; i++) {
                Integer item = value.get(i);
                if (item == null) {
                    continue;
                }
                int safe = Math.max(minValue, item);
                result[i] = safe;
            }
        }
        return result;
    }

    /** Fills a fixed-size floating-point array using normalized supplied and default values. */
    private static double[] mergeDoubleList(List<Double> value, List<Double> defaults, int length, double minValue) {
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            Double fallbackValue = getListItem(defaults, i);
            double fallback = normalizeDouble(fallbackValue, minValue);
            result[i] = fallback;
        }
        if (value != null) {
            int limit = Math.min(value.size(), length);
            for (int i = 0; i < limit; i++) {
                Double item = value.get(i);
                double safe = normalizeDouble(item, minValue);
                result[i] = safe;
            }
        }
        return result;
    }

    /** Returns null for a missing list or an index outside its range. */
    private static <T> T getListItem(List<T> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** Fills missing sections and fields in loaded MAC settings from independent defaults. */
    private static MacSettings mergeSettings(MacSettings settings, MacSettings defaults) {
        if (settings == null) {
            return copySettings(defaults);
        }
        MacSettings base = defaults == null ? new MacSettings() : defaults;

        // Fill missing battery fields and per-tier upgrade groups from native defaults.
        if (settings.battery == null) {
            settings.battery = new MacSettings.Battery();
        }
        MacSettings.Battery baseBattery = base.battery == null ? new MacSettings.Battery() : base.battery;
        settings.battery.tierNames = mergeList(settings.battery.tierNames, baseBattery.tierNames);
        settings.battery.capacity = mergeList(settings.battery.capacity, baseBattery.capacity);
        settings.battery.maxTransfer = mergeList(settings.battery.maxTransfer, baseBattery.maxTransfer);
        settings.battery.upgradeRequirements = mergeRequirementGroups(
                settings.battery.upgradeRequirements,
                baseBattery.upgradeRequirements);

        // Merge solar tier names, storage, generation and upgrade requirements.
        if (settings.solar == null) {
            settings.solar = new MacSettings.Solar();
        }
        MacSettings.Solar baseSolar = base.solar == null ? new MacSettings.Solar() : base.solar;
        settings.solar.tierNames = mergeList(settings.solar.tierNames, baseSolar.tierNames);
        settings.solar.capacity = mergeList(settings.solar.capacity, baseSolar.capacity);
        settings.solar.generation = mergeList(settings.solar.generation, baseSolar.generation);
        settings.solar.upgradeRequirements = mergeRequirementGroups(
                settings.solar.upgradeRequirements,
                baseSolar.upgradeRequirements);

        // Merge wind-generator settings using the same per-tier fallback rules.
        if (settings.wind == null) {
            settings.wind = new MacSettings.Wind();
        }
        MacSettings.Wind baseWind = base.wind == null ? new MacSettings.Wind() : base.wind;
        settings.wind.tierNames = mergeList(settings.wind.tierNames, baseWind.tierNames);
        settings.wind.capacity = mergeList(settings.wind.capacity, baseWind.capacity);
        settings.wind.generation = mergeList(settings.wind.generation, baseWind.generation);
        settings.wind.upgradeRequirements = mergeRequirementGroups(
                settings.wind.upgradeRequirements,
                baseWind.upgradeRequirements);

        // Merge energy and item cable limits separately for each tier.
        if (settings.cable == null) {
            settings.cable = new MacSettings.Cable();
        }
        MacSettings.Cable baseCable = base.cable == null ? new MacSettings.Cable() : base.cable;
        settings.cable.tierNames = mergeList(settings.cable.tierNames, baseCable.tierNames);
        settings.cable.energyCapacity = mergeList(settings.cable.energyCapacity, baseCable.energyCapacity);
        settings.cable.energyMaxTransfer = mergeList(settings.cable.energyMaxTransfer, baseCable.energyMaxTransfer);
        settings.cable.itemMaxTransfer = mergeList(settings.cable.itemMaxTransfer, baseCable.itemMaxTransfer);
        settings.cable.upgradeRequirements = mergeRequirementGroups(
                settings.cable.upgradeRequirements,
                baseCable.upgradeRequirements);

        // Preserve the default furnace multiplier when the MAC value is absent.
        if (settings.furnace == null) {
            settings.furnace = new MacSettings.Furnace();
        }
        MacSettings.Furnace baseFurnace = base.furnace == null ? new MacSettings.Furnace() : base.furnace;
        if (settings.furnace.consumptionMultiplier == null) {
            settings.furnace.consumptionMultiplier = baseFurnace.consumptionMultiplier;
        }

        // Fill crusher processing values, tier requirements and bonus-drop settings.
        if (settings.oreCrusher == null) {
            settings.oreCrusher = new MacSettings.OreCrusher();
        }
        MacSettings.OreCrusher baseOreCrusher = base.oreCrusher == null ? new MacSettings.OreCrusher() : base.oreCrusher;
        settings.oreCrusher.tierNames = mergeList(settings.oreCrusher.tierNames, baseOreCrusher.tierNames);
        settings.oreCrusher.capacity = mergeList(settings.oreCrusher.capacity, baseOreCrusher.capacity);
        settings.oreCrusher.consumptionPerSecond = mergeList(
                settings.oreCrusher.consumptionPerSecond,
                baseOreCrusher.consumptionPerSecond);
        settings.oreCrusher.outputMultiplier = mergeList(
                settings.oreCrusher.outputMultiplier,
                baseOreCrusher.outputMultiplier);
        settings.oreCrusher.processingSeconds = mergeList(
                settings.oreCrusher.processingSeconds,
                baseOreCrusher.processingSeconds);
        settings.oreCrusher.upgradeRequirements = mergeRequirementGroups(
                settings.oreCrusher.upgradeRequirements,
                baseOreCrusher.upgradeRequirements);
        settings.oreCrusher.bonusDrops = mergeBonusDrops(
                settings.oreCrusher.bonusDrops,
                baseOreCrusher.bonusDrops);
        settings.oreCrusher.powderBonusDrop = mergeBonusDrop(
                settings.oreCrusher.powderBonusDrop,
                baseOreCrusher.powderBonusDrop);

        // Fill alloy-smelter processing values, tier requirements and bonus drops.
        if (settings.alloySmelter == null) {
            settings.alloySmelter = new MacSettings.AlloySmelter();
        }
        MacSettings.AlloySmelter baseAlloy = base.alloySmelter == null
                ? new MacSettings.AlloySmelter()
                : base.alloySmelter;
        settings.alloySmelter.tierNames = mergeList(settings.alloySmelter.tierNames, baseAlloy.tierNames);
        settings.alloySmelter.capacity = mergeList(settings.alloySmelter.capacity, baseAlloy.capacity);
        settings.alloySmelter.consumptionPerSecond = mergeList(
                settings.alloySmelter.consumptionPerSecond,
                baseAlloy.consumptionPerSecond);
        settings.alloySmelter.outputMultiplier = mergeList(
                settings.alloySmelter.outputMultiplier,
                baseAlloy.outputMultiplier);
        settings.alloySmelter.processingSeconds = mergeList(
                settings.alloySmelter.processingSeconds,
                baseAlloy.processingSeconds);
        settings.alloySmelter.upgradeRequirements = mergeRequirementGroups(
                settings.alloySmelter.upgradeRequirements,
                baseAlloy.upgradeRequirements);
        settings.alloySmelter.bonusDrops = mergeBonusDrops(
                settings.alloySmelter.bonusDrops,
                baseAlloy.bonusDrops);

        // Complete quarry area, speed, energy, backfill and upgrade settings.
        if (settings.quarry == null) {
            settings.quarry = new MacSettings.Quarry();
        }
        MacSettings.Quarry baseQuarry = base.quarry == null ? new MacSettings.Quarry() : base.quarry;
        if (settings.quarry.baseArea == null) {
            settings.quarry.baseArea = baseQuarry.baseArea;
        }
        if (settings.quarry.minArea == null) {
            settings.quarry.minArea = baseQuarry.minArea;
        }
        if (settings.quarry.basicSpeedSeconds == null) {
            settings.quarry.basicSpeedSeconds = baseQuarry.basicSpeedSeconds;
        }
        if (settings.quarry.quantumSpeedSeconds == null) {
            settings.quarry.quantumSpeedSeconds = baseQuarry.quantumSpeedSeconds;
        }
        if (settings.quarry.backfillPlaceDelayTicks == null) {
            settings.quarry.backfillPlaceDelayTicks = baseQuarry.backfillPlaceDelayTicks;
        }
        if (settings.quarry.backfillPostDelayTicks == null) {
            settings.quarry.backfillPostDelayTicks = baseQuarry.backfillPostDelayTicks;
        }
        settings.quarry.tierNames = mergeList(settings.quarry.tierNames, baseQuarry.tierNames);
        settings.quarry.capacity = mergeList(settings.quarry.capacity, baseQuarry.capacity);
        settings.quarry.consumptionPerSecond = mergeList(
                settings.quarry.consumptionPerSecond,
                baseQuarry.consumptionPerSecond);
        settings.quarry.maxArea = mergeList(settings.quarry.maxArea, baseQuarry.maxArea);
        settings.quarry.upgradeRequirements = mergeRequirementGroups(
                settings.quarry.upgradeRequirements,
                baseQuarry.upgradeRequirements);
        if (settings.quarry.forceReplaceBlocks == null) {
            settings.quarry.forceReplaceBlocks = baseQuarry.forceReplaceBlocks;
        }

        return settings;
    }

    /** Copies all settings sections and their nested lists so defaults are not shared. */
    private static MacSettings copySettings(MacSettings defaults) {
        MacSettings copy = new MacSettings();
        if (defaults == null) {
            return copy;
        }
        if (defaults.battery != null) {
            copy.battery.tierNames = copyList(defaults.battery.tierNames);
            copy.battery.capacity = copyList(defaults.battery.capacity);
            copy.battery.maxTransfer = copyList(defaults.battery.maxTransfer);
            copy.battery.upgradeRequirements = copyRequirementGroups(defaults.battery.upgradeRequirements);
        }
        if (defaults.solar != null) {
            copy.solar.tierNames = copyList(defaults.solar.tierNames);
            copy.solar.capacity = copyList(defaults.solar.capacity);
            copy.solar.generation = copyList(defaults.solar.generation);
            copy.solar.upgradeRequirements = copyRequirementGroups(defaults.solar.upgradeRequirements);
        }
        if (defaults.wind != null) {
            copy.wind.tierNames = copyList(defaults.wind.tierNames);
            copy.wind.capacity = copyList(defaults.wind.capacity);
            copy.wind.generation = copyList(defaults.wind.generation);
            copy.wind.upgradeRequirements = copyRequirementGroups(defaults.wind.upgradeRequirements);
        }
        if (defaults.cable != null) {
            copy.cable.tierNames = copyList(defaults.cable.tierNames);
            copy.cable.energyCapacity = copyList(defaults.cable.energyCapacity);
            copy.cable.energyMaxTransfer = copyList(defaults.cable.energyMaxTransfer);
            copy.cable.itemMaxTransfer = copyList(defaults.cable.itemMaxTransfer);
            copy.cable.upgradeRequirements = copyRequirementGroups(defaults.cable.upgradeRequirements);
        }
        if (defaults.furnace != null) {
            copy.furnace.consumptionMultiplier = defaults.furnace.consumptionMultiplier;
        }
        if (defaults.oreCrusher != null) {
            copy.oreCrusher.tierNames = copyList(defaults.oreCrusher.tierNames);
            copy.oreCrusher.capacity = copyList(defaults.oreCrusher.capacity);
            copy.oreCrusher.consumptionPerSecond = copyList(defaults.oreCrusher.consumptionPerSecond);
            copy.oreCrusher.outputMultiplier = copyList(defaults.oreCrusher.outputMultiplier);
            copy.oreCrusher.processingSeconds = copyList(defaults.oreCrusher.processingSeconds);
            copy.oreCrusher.upgradeRequirements = copyRequirementGroups(defaults.oreCrusher.upgradeRequirements);
            copy.oreCrusher.bonusDrops = copyBonusDrops(defaults.oreCrusher.bonusDrops);
            copy.oreCrusher.powderBonusDrop = copyBonusDrop(defaults.oreCrusher.powderBonusDrop);
        }
        if (defaults.alloySmelter != null) {
            copy.alloySmelter.tierNames = copyList(defaults.alloySmelter.tierNames);
            copy.alloySmelter.capacity = copyList(defaults.alloySmelter.capacity);
            copy.alloySmelter.consumptionPerSecond = copyList(defaults.alloySmelter.consumptionPerSecond);
            copy.alloySmelter.outputMultiplier = copyList(defaults.alloySmelter.outputMultiplier);
            copy.alloySmelter.processingSeconds = copyList(defaults.alloySmelter.processingSeconds);
            copy.alloySmelter.upgradeRequirements = copyRequirementGroups(defaults.alloySmelter.upgradeRequirements);
            copy.alloySmelter.bonusDrops = copyBonusDrops(defaults.alloySmelter.bonusDrops);
        }
        if (defaults.quarry != null) {
            copy.quarry.baseArea = defaults.quarry.baseArea;
            copy.quarry.minArea = defaults.quarry.minArea;
            copy.quarry.basicSpeedSeconds = defaults.quarry.basicSpeedSeconds;
            copy.quarry.quantumSpeedSeconds = defaults.quarry.quantumSpeedSeconds;
            copy.quarry.backfillPlaceDelayTicks = defaults.quarry.backfillPlaceDelayTicks;
            copy.quarry.backfillPostDelayTicks = defaults.quarry.backfillPostDelayTicks;
            copy.quarry.tierNames = copyList(defaults.quarry.tierNames);
            copy.quarry.capacity = copyList(defaults.quarry.capacity);
            copy.quarry.consumptionPerSecond = copyList(defaults.quarry.consumptionPerSecond);
            copy.quarry.maxArea = copyList(defaults.quarry.maxArea);
            copy.quarry.upgradeRequirements = copyRequirementGroups(defaults.quarry.upgradeRequirements);
            copy.quarry.forceReplaceBlocks = defaults.quarry.forceReplaceBlocks;
        }
        return copy;
    }

    /** Keeps supplied list entries and fills missing or null positions from defaults. */
    private static <T> List<T> mergeList(List<T> value, List<T> defaults) {
        if (value == null || value.isEmpty()) {
            return copyList(defaults);
        }
        List<T> merged = new ArrayList<>(value);
        if (defaults == null || defaults.isEmpty()) {
            return merged;
        }
        int limit = defaults.size();
        for (int i = 0; i < limit; i++) {
            T fallback = defaults.get(i);
            if (i >= merged.size()) {
                merged.add(fallback);
            } else if (merged.get(i) == null) {
                merged.set(i, fallback);
            }
        }
        return merged;
    }

    /** Creates a mutable list copy, or an empty list for null input. */
    private static <T> List<T> copyList(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    /** Merges requirement groups by tier, copying nested default groups as needed. */
    private static List<MacSettings.RequirementGroup> mergeRequirementGroups(
            List<MacSettings.RequirementGroup> value,
            List<MacSettings.RequirementGroup> defaults) {
        if (value == null || value.isEmpty()) {
            return copyRequirementGroups(defaults);
        }
        List<MacSettings.RequirementGroup> merged = new ArrayList<>();
        int max = Math.max(value.size(), defaults == null ? 0 : defaults.size());
        for (int i = 0; i < max; i++) {
            MacSettings.RequirementGroup current = getListItem(value, i);
            MacSettings.RequirementGroup fallback = getListItem(defaults, i);
            if (current == null) {
                merged.add(copyRequirementGroup(fallback));
                continue;
            }
            MacSettings.RequirementGroup group = new MacSettings.RequirementGroup();
            group.items = mergeRequirements(
                    current.items,
                    fallback == null ? null : fallback.items);
            merged.add(group);
        }
        return merged;
    }

    /** Merges each item requirement with its corresponding default entry. */
    private static List<MacSettings.Requirement> mergeRequirements(
            List<MacSettings.Requirement> value,
            List<MacSettings.Requirement> defaults) {
        if (value == null || value.isEmpty()) {
            return copyRequirements(defaults);
        }
        List<MacSettings.Requirement> merged = new ArrayList<>();
        int max = Math.max(value.size(), defaults == null ? 0 : defaults.size());
        for (int i = 0; i < max; i++) {
            MacSettings.Requirement current = getListItem(value, i);
            MacSettings.Requirement fallback = getListItem(defaults, i);
            MacSettings.Requirement mergedReq = mergeRequirement(current, fallback);
            if (mergedReq != null) {
                merged.add(mergedReq);
            }
        }
        return merged;
    }

    /** Fills a requirement's missing item ID and quantity from its default. */
    private static MacSettings.Requirement mergeRequirement(
            MacSettings.Requirement value,
            MacSettings.Requirement defaults) {
        if (value == null) {
            return copyRequirement(defaults);
        }
        MacSettings.Requirement merged = new MacSettings.Requirement();
        if (value.itemId != null && !value.itemId.isEmpty()) {
            merged.itemId = value.itemId;
        } else if (defaults != null) {
            merged.itemId = defaults.itemId;
        }
        if (value.quantity != null) {
            merged.quantity = value.quantity;
        } else if (defaults != null) {
            merged.quantity = defaults.quantity;
        }
        return merged;
    }

    /** Deep-copies the list of tier requirement groups. */
    private static List<MacSettings.RequirementGroup> copyRequirementGroups(
            List<MacSettings.RequirementGroup> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<MacSettings.RequirementGroup> copy = new ArrayList<>(source.size());
        for (MacSettings.RequirementGroup group : source) {
            copy.add(copyRequirementGroup(group));
        }
        return copy;
    }

    /** Copies one group and its nested requirements. */
    private static MacSettings.RequirementGroup copyRequirementGroup(
            MacSettings.RequirementGroup source) {
        MacSettings.RequirementGroup group = new MacSettings.RequirementGroup();
        if (source == null) {
            group.items = new ArrayList<>();
            return group;
        }
        group.items = copyRequirements(source.items);
        return group;
    }

    /** Copies non-null requirement entries into a mutable list. */
    private static List<MacSettings.Requirement> copyRequirements(
            List<MacSettings.Requirement> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<MacSettings.Requirement> copy = new ArrayList<>(source.size());
        for (MacSettings.Requirement requirement : source) {
            MacSettings.Requirement item = copyRequirement(requirement);
            if (item != null) {
                copy.add(item);
            }
        }
        return copy;
    }

    /** Copies an item ID and quantity into a new requirement object. */
    private static MacSettings.Requirement copyRequirement(MacSettings.Requirement source) {
        if (source == null) {
            return null;
        }
        MacSettings.Requirement copy = new MacSettings.Requirement();
        copy.itemId = source.itemId;
        copy.quantity = source.quantity;
        return copy;
    }

    /** Merges bonus-drop rules by index, filling missing entries from defaults. */
    private static List<MacSettings.BonusDrop> mergeBonusDrops(
            List<MacSettings.BonusDrop> value,
            List<MacSettings.BonusDrop> defaults) {
        if (value == null || value.isEmpty()) {
            return copyBonusDrops(defaults);
        }
        List<MacSettings.BonusDrop> merged = new ArrayList<>();
        int max = Math.max(value.size(), defaults == null ? 0 : defaults.size());
        for (int i = 0; i < max; i++) {
            MacSettings.BonusDrop current = getListItem(value, i);
            MacSettings.BonusDrop fallback = getListItem(defaults, i);
            MacSettings.BonusDrop mergedDrop = mergeBonusDrop(current, fallback);
            if (mergedDrop != null) {
                merged.add(mergedDrop);
            }
        }
        return merged;
    }

    /** Fills missing fields of one bonus-drop rule from its default. */
    private static MacSettings.BonusDrop mergeBonusDrop(
            MacSettings.BonusDrop value,
            MacSettings.BonusDrop defaults) {
        if (value == null) {
            return copyBonusDrop(defaults);
        }
        MacSettings.BonusDrop merged = new MacSettings.BonusDrop();
        if (value.itemId != null && !value.itemId.isEmpty()) {
            merged.itemId = value.itemId;
        } else if (defaults != null) {
            merged.itemId = defaults.itemId;
        }
        if (value.quantity != null) {
            merged.quantity = value.quantity;
        } else if (defaults != null) {
            merged.quantity = defaults.quantity;
        }
        if (value.baseChance != null) {
            merged.baseChance = value.baseChance;
        } else if (defaults != null) {
            merged.baseChance = defaults.baseChance;
        }
        if (value.perTierChance != null) {
            merged.perTierChance = value.perTierChance;
        } else if (defaults != null) {
            merged.perTierChance = defaults.perTierChance;
        }
        return merged;
    }

    /** Copies non-null bonus-drop rules into a mutable list. */
    private static List<MacSettings.BonusDrop> copyBonusDrops(List<MacSettings.BonusDrop> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<MacSettings.BonusDrop> copy = new ArrayList<>(source.size());
        for (MacSettings.BonusDrop drop : source) {
            MacSettings.BonusDrop item = copyBonusDrop(drop);
            if (item != null) {
                copy.add(item);
            }
        }
        return copy;
    }

    /** Copies the item, quantity and chance fields of a bonus-drop rule. */
    private static MacSettings.BonusDrop copyBonusDrop(MacSettings.BonusDrop source) {
        if (source == null) {
            return null;
        }
        MacSettings.BonusDrop copy = new MacSettings.BonusDrop();
        copy.itemId = source.itemId;
        copy.quantity = source.quantity;
        copy.baseChance = source.baseChance;
        copy.perTierChance = source.perTierChance;
        return copy;
    }

    /** Reads the currently active native settings into MAC's editable default structure. */
    private static MacSettings buildDefaults() {
        MacSettings defaults = new MacSettings();

        // Capture active battery settings for each supported tier.
        defaults.battery.tierNames = buildStringList(
                BatteryUpgradeConfig.MIN_TIER,
                BatteryUpgradeConfig.MAX_TIER,
                BatteryUpgradeConfig::getTierName);
        defaults.battery.capacity = buildIntList(
                BatteryUpgradeConfig.MIN_TIER,
                BatteryUpgradeConfig.MAX_TIER,
                BatteryUpgradeConfig::getCapacityForTier);
        defaults.battery.maxTransfer = buildIntList(
                BatteryUpgradeConfig.MIN_TIER,
                BatteryUpgradeConfig.MAX_TIER,
                BatteryUpgradeConfig::getMaxTransferForTier);
        defaults.battery.upgradeRequirements = buildRequirementGroups(
                BatteryUpgradeConfig.MIN_TIER,
                BatteryUpgradeConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        BatteryUpgradeConfig.getUpgradeRequirements(tier),
                        BatteryUpgradeConfig.Requirement::getItemId,
                        BatteryUpgradeConfig.Requirement::getQuantity));

        // Capture solar generation and upgrade settings for the editable defaults.
        defaults.solar.tierNames = buildStringList(
                SolarUpgradeConfig.MIN_TIER,
                SolarUpgradeConfig.MAX_TIER,
                SolarUpgradeConfig::getTierName);
        defaults.solar.capacity = buildIntList(
                SolarUpgradeConfig.MIN_TIER,
                SolarUpgradeConfig.MAX_TIER,
                SolarUpgradeConfig::getCapacityForTier);
        defaults.solar.generation = buildIntList(
                SolarUpgradeConfig.MIN_TIER,
                SolarUpgradeConfig.MAX_TIER,
                SolarUpgradeConfig::getGenerationForTier);
        defaults.solar.upgradeRequirements = buildRequirementGroups(
                SolarUpgradeConfig.MIN_TIER,
                SolarUpgradeConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        SolarUpgradeConfig.getUpgradeRequirements(tier),
                        SolarUpgradeConfig.Requirement::getItemId,
                        SolarUpgradeConfig.Requirement::getQuantity));

        // Capture wind generation and upgrade settings for the editable defaults.
        defaults.wind.tierNames = buildStringList(
                WindUpgradeConfig.MIN_TIER,
                WindUpgradeConfig.MAX_TIER,
                WindUpgradeConfig::getTierName);
        defaults.wind.capacity = buildIntList(
                WindUpgradeConfig.MIN_TIER,
                WindUpgradeConfig.MAX_TIER,
                WindUpgradeConfig::getCapacityForTier);
        defaults.wind.generation = buildIntList(
                WindUpgradeConfig.MIN_TIER,
                WindUpgradeConfig.MAX_TIER,
                WindUpgradeConfig::getGenerationForTier);
        defaults.wind.upgradeRequirements = buildRequirementGroups(
                WindUpgradeConfig.MIN_TIER,
                WindUpgradeConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        WindUpgradeConfig.getUpgradeRequirements(tier),
                        WindUpgradeConfig.Requirement::getItemId,
                        WindUpgradeConfig.Requirement::getQuantity));

        // Capture cable capacity, energy and item transfer limits, and costs.
        defaults.cable.tierNames = buildStringList(
                CableUpgradeConfig.MIN_TIER,
                CableUpgradeConfig.MAX_TIER,
                CableUpgradeConfig::getTierName);
        defaults.cable.energyCapacity = buildIntList(
                CableUpgradeConfig.MIN_TIER,
                CableUpgradeConfig.MAX_TIER,
                CableUpgradeConfig::getEnergyCapacityForTier);
        defaults.cable.energyMaxTransfer = buildIntList(
                CableUpgradeConfig.MIN_TIER,
                CableUpgradeConfig.MAX_TIER,
                CableUpgradeConfig::getEnergyMaxTransferForTier);
        defaults.cable.itemMaxTransfer = buildIntList(
                CableUpgradeConfig.MIN_TIER,
                CableUpgradeConfig.MAX_TIER,
                CableUpgradeConfig::getItemMaxTransferForTier);
        defaults.cable.upgradeRequirements = buildRequirementGroups(
                CableUpgradeConfig.MIN_TIER,
                CableUpgradeConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        CableUpgradeConfig.getUpgradeRequirements(tier, 1),
                        CableUpgradeConfig.Requirement::getItemId,
                        CableUpgradeConfig.Requirement::getQuantity));

        Integer furnaceMultiplier = readFurnaceMultiplier();
        defaults.furnace.consumptionMultiplier = furnaceMultiplier == null ? 3 : furnaceMultiplier;

        // Capture crusher processing, per-tier requirements and bonus-drop defaults.
        defaults.oreCrusher.tierNames = buildStringList(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                OreCrusherConfig::getTierName);
        defaults.oreCrusher.capacity = buildIntList(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                OreCrusherConfig::getCapacityForTier);
        defaults.oreCrusher.consumptionPerSecond = buildIntList(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                OreCrusherConfig::getConsumptionPerSecond);
        defaults.oreCrusher.outputMultiplier = buildIntList(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                OreCrusherConfig::getOutputMultiplierForTier);
        defaults.oreCrusher.processingSeconds = buildDoubleList(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                OreCrusherConfig::getProcessingSecondsForTier);
        defaults.oreCrusher.upgradeRequirements = buildRequirementGroups(
                OreCrusherConfig.MIN_TIER,
                OreCrusherConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        OreCrusherConfig.getUpgradeRequirements(tier),
                        OreCrusherConfig.Requirement::getItemId,
                        OreCrusherConfig.Requirement::getQuantity));
        defaults.oreCrusher.bonusDrops = toSettingBonusDrops(
                OreCrusherConfig.getBonusDropConfig(),
                OreCrusherConfig.BonusDrop::getItemId,
                OreCrusherConfig.BonusDrop::getQuantity,
                OreCrusherConfig.BonusDrop::getBaseChance,
                OreCrusherConfig.BonusDrop::getPerTierChance);
        defaults.oreCrusher.powderBonusDrop = toSettingBonusDrop(
                OreCrusherConfig.getPowderBonusDropConfig(),
                OreCrusherConfig.BonusDrop::getItemId,
                OreCrusherConfig.BonusDrop::getQuantity,
                OreCrusherConfig.BonusDrop::getBaseChance,
                OreCrusherConfig.BonusDrop::getPerTierChance);

        // Capture alloy-smelter processing, per-tier requirements and bonus-drop defaults.
        defaults.alloySmelter.tierNames = buildStringList(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                AlloySmelterConfig::getTierName);
        defaults.alloySmelter.capacity = buildIntList(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                AlloySmelterConfig::getCapacityForTier);
        defaults.alloySmelter.consumptionPerSecond = buildIntList(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                AlloySmelterConfig::getConsumptionPerSecond);
        defaults.alloySmelter.outputMultiplier = buildIntList(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                AlloySmelterConfig::getOutputMultiplierForTier);
        defaults.alloySmelter.processingSeconds = buildDoubleList(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                AlloySmelterConfig::getProcessingSecondsForTier);
        defaults.alloySmelter.upgradeRequirements = buildRequirementGroups(
                AlloySmelterConfig.MIN_TIER,
                AlloySmelterConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        AlloySmelterConfig.getUpgradeRequirements(tier),
                        AlloySmelterConfig.Requirement::getItemId,
                        AlloySmelterConfig.Requirement::getQuantity));
        defaults.alloySmelter.bonusDrops = toSettingBonusDrops(
                AlloySmelterConfig.getBonusDropConfig(),
                AlloySmelterConfig.BonusDrop::getItemId,
                AlloySmelterConfig.BonusDrop::getQuantity,
                AlloySmelterConfig.BonusDrop::getBaseChance,
                AlloySmelterConfig.BonusDrop::getPerTierChance);

        defaults.quarry.baseArea = QuarryConfig.BASE_AREA;
        defaults.quarry.minArea = QuarryConfig.MIN_AREA;
        defaults.quarry.basicSpeedSeconds = QuarryConfig.getMiningSecondsForTier(QuarryConfig.MIN_TIER);
        defaults.quarry.quantumSpeedSeconds = QuarryConfig.getMiningSecondsForTier(QuarryConfig.MAX_TIER);
        defaults.quarry.backfillPlaceDelayTicks = QuarryConfig.getBackfillPlaceDelayTicks();
        defaults.quarry.backfillPostDelayTicks = QuarryConfig.getBackfillPostDelayTicks();
        // Capture the quarry's per-tier energy, area and upgrade values.
        defaults.quarry.tierNames = buildStringList(
                QuarryConfig.MIN_TIER,
                QuarryConfig.MAX_TIER,
                QuarryConfig::getTierName);
        defaults.quarry.capacity = buildIntList(
                QuarryConfig.MIN_TIER,
                QuarryConfig.MAX_TIER,
                QuarryConfig::getCapacityForTier);
        defaults.quarry.consumptionPerSecond = buildIntList(
                QuarryConfig.MIN_TIER,
                QuarryConfig.MAX_TIER,
                QuarryConfig::getConsumptionPerSecond);
        defaults.quarry.maxArea = buildIntList(
                QuarryConfig.MIN_TIER,
                QuarryConfig.MAX_TIER,
                QuarryConfig::getMaxAreaForTier);
        defaults.quarry.upgradeRequirements = buildRequirementGroups(
                QuarryConfig.MIN_TIER,
                QuarryConfig.MAX_TIER,
                tier -> toSettingRequirements(
                        QuarryConfig.getUpgradeRequirements(tier),
                        QuarryConfig.Requirement::getItemId,
                        QuarryConfig.Requirement::getQuantity));
        defaults.quarry.forceReplaceBlocks = QuarryConfig.isForceReplaceBlocks();

        return defaults;
    }

    /** Reads and normalizes one string for every tier in the inclusive range. */
    private static List<String> buildStringList(
            int minTier,
            int maxTier,
            IntFunction<String> getter) {
        List<String> list = new ArrayList<>();
        for (int tier = minTier; tier <= maxTier; tier++) {
            list.add(normalizeItemId(getter.apply(tier)));
        }
        return list;
    }

    /** Reads one integer per tier, substituting zero for missing values. */
    private static List<Integer> buildIntList(
            int minTier,
            int maxTier,
            IntFunction<Integer> getter) {
        List<Integer> list = new ArrayList<>();
        for (int tier = minTier; tier <= maxTier; tier++) {
            Integer value = getter.apply(tier);
            list.add(value == null ? 0 : value);
        }
        return list;
    }

    /** Reads one floating-point value for each tier in the inclusive range. */
    private static List<Double> buildDoubleList(
            int minTier,
            int maxTier,
            IntToDoubleFunction getter) {
        List<Double> list = new ArrayList<>();
        for (int tier = minTier; tier <= maxTier; tier++) {
            list.add(getter.applyAsDouble(tier));
        }
        return list;
    }

    /** Converts each tier's requirement list into a MAC requirement group. */
    private static List<MacSettings.RequirementGroup> buildRequirementGroups(
            int minTier,
            int maxTier,
            IntFunction<List<MacSettings.Requirement>> provider) {
        List<MacSettings.RequirementGroup> groups = new ArrayList<>();
        for (int tier = minTier; tier <= maxTier; tier++) {
            MacSettings.RequirementGroup group = new MacSettings.RequirementGroup();
            List<MacSettings.Requirement> items = provider.apply(tier);
            group.items = items == null ? Collections.emptyList() : items;
            groups.add(group);
        }
        return groups;
    }

    /** Converts valid native requirements into MAC item-and-quantity entries. */
    private static <R> List<MacSettings.Requirement> toSettingRequirements(
            R[] requirements,
            java.util.function.Function<R, String> itemIdGetter,
            java.util.function.ToIntFunction<R> quantityGetter) {
        if (requirements == null || requirements.length == 0) {
            return Collections.emptyList();
        }
        List<MacSettings.Requirement> list = new ArrayList<>(requirements.length);
        for (R requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            String itemId = normalizeItemId(itemIdGetter.apply(requirement));
            int quantity = normalizeQuantity(quantityGetter.applyAsInt(requirement));
            if (itemId.isEmpty() || quantity <= 0) {
                continue;
            }
            MacSettings.Requirement item = new MacSettings.Requirement();
            item.itemId = itemId;
            item.quantity = quantity;
            list.add(item);
        }
        return list;
    }

    /** Converts a list of native drop rules to editable MAC settings. */
    private static <D> List<MacSettings.BonusDrop> toSettingBonusDrops(
            List<D> drops,
            java.util.function.Function<D, String> itemIdGetter,
            java.util.function.ToIntFunction<D> quantityGetter,
            java.util.function.ToDoubleFunction<D> baseChanceGetter,
            java.util.function.ToDoubleFunction<D> perTierChanceGetter) {
        if (drops == null || drops.isEmpty()) {
            return Collections.emptyList();
        }
        List<MacSettings.BonusDrop> list = new ArrayList<>(drops.size());
        for (D drop : drops) {
            MacSettings.BonusDrop built = toSettingBonusDrop(
                    drop,
                    itemIdGetter,
                    quantityGetter,
                    baseChanceGetter,
                    perTierChanceGetter);
            if (built != null) {
                list.add(built);
            }
        }
        return list;
    }

    /** Converts a valid native bonus-drop rule into a MAC settings object. */
    private static <D> MacSettings.BonusDrop toSettingBonusDrop(
            D drop,
            java.util.function.Function<D, String> itemIdGetter,
            java.util.function.ToIntFunction<D> quantityGetter,
            java.util.function.ToDoubleFunction<D> baseChanceGetter,
            java.util.function.ToDoubleFunction<D> perTierChanceGetter) {
        if (drop == null) {
            return null;
        }
        String itemId = normalizeItemId(itemIdGetter.apply(drop));
        int quantity = normalizeQuantity(quantityGetter.applyAsInt(drop));
        double baseChance = normalizeChance(baseChanceGetter.applyAsDouble(drop));
        double perTierChance = normalizeChance(perTierChanceGetter.applyAsDouble(drop));
        if (itemId.isEmpty() || quantity <= 0) {
            return null;
        }
        MacSettings.BonusDrop built = new MacSettings.BonusDrop();
        built.itemId = itemId;
        built.quantity = quantity;
        built.baseChance = baseChance;
        built.perTierChance = perTierChance;
        return built;
    }

    /** Reads the current furnace multiplier reflectively, if available. */
    private static Integer readFurnaceMultiplier() {
        try {
            Field field = FurnaceConfig.class.getDeclaredField("consumptionMultiplier");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Trims an item ID and represents null or whitespace-only values as empty strings. */
    private static String normalizeItemId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    /** Treats an absent quantity as zero. */
    private static int normalizeQuantity(Integer value) {
        if (value == null) {
            return 0;
        }
        return normalizeQuantity(value.intValue());
    }

    /** Clamps a quantity to zero or greater. */
    private static int normalizeQuantity(int value) {
        return Math.max(0, value);
    }

    /** Treats an absent chance as zero. */
    private static double normalizeChance(Double value) {
        if (value == null) {
            return 0.0;
        }
        return normalizeChance(value.doubleValue());
    }

    /** Replaces non-finite chance values with zero; finite values are retained. */
    private static double normalizeChance(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return value;
    }

    /** Replaces absent, non-finite or too-small numbers with the supplied minimum. */
    private static double normalizeDouble(Double value, double minValue) {
        if (value == null) {
            return minValue;
        }
        double v = value.doubleValue();
        if (!Double.isFinite(v) || v < minValue) {
            return minValue;
        }
        return v;
    }
}
