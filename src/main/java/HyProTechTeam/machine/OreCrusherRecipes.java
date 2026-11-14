package HyProTechTeam.machine;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds and caches ore-crusher recipes from processing assets and known ore fallbacks. */
public final class OreCrusherRecipes {
    private static volatile List<RecipeEntry> RECIPE_CACHE;

    private OreCrusherRecipes() {
    }

    /** Builds the recipe list on first access and reuses it afterward. */
    public static List<RecipeEntry> getRecipes() {
        if (RECIPE_CACHE != null) {
            return RECIPE_CACHE;
        }
        RECIPE_CACHE = buildRecipeEntries();
        return RECIPE_CACHE;
    }

    /** Finds the first recipe with a matching primary input ID, ignoring case. */
    public static RecipeEntry findByInput(String inputItemId) {
        if (inputItemId == null || inputItemId.isEmpty()) {
            return null;
        }
        for (RecipeEntry entry : getRecipes()) {
            if (entry != null && inputItemId.equalsIgnoreCase(entry.inputItemId)) {
                return entry;
            }
        }
        return null;
    }

    /** Filters processing recipes to base ores, adds fallbacks, and sorts by input ID. */
    private static List<RecipeEntry> buildRecipeEntries() {
        Map<String, RecipeEntry> byInput = new LinkedHashMap<>();
        Map<String, CraftingRecipe> map = CraftingRecipe.getAssetMap().getAssetMap();
        if (map != null && !map.isEmpty()) {
            for (CraftingRecipe recipe : map.values()) {
                if (recipe == null || !isProcessingRecipe(recipe)) {
                    continue;
                }
                MaterialQuantity input = resolvePrimaryInput(recipe);
                if (input == null || input.getItemId() == null || input.getItemId().isEmpty()) {
                    continue;
                }
                String inputId = input.getItemId();
                if (!isBaseOreId(inputId)) {
                    continue;
                }
                MaterialQuantity primaryOutput = resolvePrimaryOutput(recipe);
                if (primaryOutput == null
                        || primaryOutput.getItemId() == null
                        || primaryOutput.getItemId().isEmpty()) {
                    continue;
                }
                MaterialQuantity[] outputs = normalizeOutputs(
                        recipe.getOutputs(),
                        inputId,
                        primaryOutput);
                MaterialQuantity output = resolvePrimaryOutputFromOutputs(primaryOutput, outputs);
                if (output == null || output.getItemId() == null || output.getItemId().isEmpty()) {
                    continue;
                }
                if (!byInput.containsKey(inputId)) {
                    RecipeEntry entry = new RecipeEntry(
                            recipe.getId(),
                            inputId,
                            Math.max(1, input.getQuantity()),
                            output.getItemId(),
                            Math.max(1, output.getQuantity()),
                            recipe.getInput(),
                            outputs);
                    byInput.put(inputId, entry);
                }
            }
        }

        // Add known vanilla ores only when no earlier recipe was registered for the input.
        addVanillaOreEntries(byInput);
        List<RecipeEntry> entries = new ArrayList<>(byInput.values());
        entries.sort(Comparator.comparing(o -> o.inputItemId.toLowerCase(Locale.ROOT)));
        return entries;
    }

    /** Adds one-to-one powder recipes for known ore and powder assets missing from the map. */
    private static void addVanillaOreEntries(Map<String, RecipeEntry> byInput) {
        String[][] vanilla = {
                {"Ore_Copper", "HyProTech_Powder_Copper"},
                {"Ore_Iron", "HyProTech_Powder_Iron"},
                {"Ore_Gold", "HyProTech_Powder_Gold"},
                {"Ore_Silver", "HyProTech_Powder_Silver"},
                {"Ore_Cobalt", "HyProTech_Powder_Cobalt"},
                {"Ore_Thorium", "HyProTech_Powder_Thorium"},
                {"Ore_Adamantite", "HyProTech_Powder_Adamantite"}
        };
        for (String[] pair : vanilla) {
            String inputId = pair[0];
            String outputId = pair[1];
            if (byInput.containsKey(inputId)) {
                continue;
            }
            if (!isKnownItemId(inputId) || !isKnownItemId(outputId)) {
                continue;
            }
            RecipeEntry entry = new RecipeEntry(
                    "vanilla:" + inputId.toLowerCase(Locale.ROOT),
                    inputId,
                    1,
                    outputId,
                    1,
                    new MaterialQuantity[] { new MaterialQuantity(inputId, null, null, 1, null) },
                    new MaterialQuantity[] { new MaterialQuantity(outputId, null, null, 1, null) });
            byInput.put(inputId, entry);
        }
    }

    /** Accepts processing recipes registered for the OreCrusher or Furnace bench. */
    private static boolean isProcessingRecipe(CraftingRecipe recipe) {
        BenchRequirement[] requirements = recipe.getBenchRequirement();
        if (requirements == null || requirements.length == 0) {
            return false;
        }
        for (BenchRequirement requirement : requirements) {
            if (requirement == null || requirement.type != BenchType.Processing) {
                continue;
            }
            if ("OreCrusher".equalsIgnoreCase(requirement.id)
                    || "Furnace".equalsIgnoreCase(requirement.id)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the first input with a nonempty item ID. */
    private static MaterialQuantity resolvePrimaryInput(CraftingRecipe recipe) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) {
            return null;
        }
        for (MaterialQuantity input : inputs) {
            if (input != null && input.getItemId() != null && !input.getItemId().isEmpty()) {
                return input;
            }
        }
        return null;
    }

    /** Prefers the declared primary output, then the first valid output array entry. */
    private static MaterialQuantity resolvePrimaryOutput(CraftingRecipe recipe) {
        MaterialQuantity primary = recipe.getPrimaryOutput();
        if (primary != null && primary.getItemId() != null && !primary.getItemId().isEmpty()) {
            return primary;
        }
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs == null || outputs.length == 0) {
            return null;
        }
        for (MaterialQuantity output : outputs) {
            if (output != null && output.getItemId() != null && !output.getItemId().isEmpty()) {
                return output;
            }
        }
        return null;
    }

    /** Returns the first valid normalized output, or the original primary output as fallback. */
    private static MaterialQuantity resolvePrimaryOutputFromOutputs(
            MaterialQuantity fallback,
            MaterialQuantity[] outputs) {
        if (outputs != null) {
            for (MaterialQuantity output : outputs) {
                if (output == null || output.getItemId() == null || output.getItemId().isEmpty()) {
                    continue;
                }
                return output;
            }
        }
        return fallback;
    }

    /** Replaces resolved ore outputs with powder, ore chips, and slag when a powder ID is known. */
    private static MaterialQuantity[] normalizeOutputs(
            MaterialQuantity[] outputs,
            String inputId,
            MaterialQuantity primaryOutput) {
        String powderId = OreCrusherConfig.resolvePowderId(
                inputId,
                primaryOutput == null ? null : primaryOutput.getItemId());
        if (powderId == null || powderId.isEmpty()) {
            return outputs == null ? MaterialQuantity.EMPTY_ARRAY : outputs;
        }
        int powderQty = primaryOutput == null ? 1 : Math.max(1, primaryOutput.getQuantity());
        List<MaterialQuantity> resolved = new ArrayList<>(3);
        resolved.add(new MaterialQuantity(powderId, null, null, powderQty, null));
        resolved.add(new MaterialQuantity("HyProTech_Ore_Chips", null, null, 1, null));
        resolved.add(new MaterialQuantity("HyProTech_Slag", null, null, 1, null));
        return resolved.toArray(new MaterialQuantity[0]);
    }

    /** Recognizes base ore IDs while excluding certain variant or block naming forms. */
    private static boolean isBaseOreId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        if (itemId.startsWith("Ore_")) {
            return itemId.indexOf('_', 4) < 0;
        }
        if (itemId.endsWith("_Ore")) {
            return !itemId.contains("Block") && !itemId.contains("block");
        }
        return false;
    }

    /** Accepts an existing item asset that is neither UNKNOWN nor a state asset. */
    private static boolean isKnownItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null && item != Item.UNKNOWN && !item.isState();
    }

    /** Snapshot of recipe IDs, primary quantities, and full input/output arrays. */
    public static final class RecipeEntry {
        public final String recipeId;
        public final String inputItemId;
        public final int inputQuantity;
        public final String outputItemId;
        public final int outputQuantity;
        public final MaterialQuantity[] inputs;
        public final MaterialQuantity[] outputs;

        private RecipeEntry(
                String recipeId,
                String inputItemId,
                int inputQuantity,
                String outputItemId,
                int outputQuantity,
                MaterialQuantity[] inputs,
                MaterialQuantity[] outputs) {
            this.recipeId = recipeId == null ? "" : recipeId;
            this.inputItemId = inputItemId;
            this.inputQuantity = inputQuantity;
            this.outputItemId = outputItemId;
            this.outputQuantity = outputQuantity;
            this.inputs = inputs == null ? MaterialQuantity.EMPTY_ARRAY : inputs;
            this.outputs = outputs == null ? MaterialQuantity.EMPTY_ARRAY : outputs;
        }
    }
}
