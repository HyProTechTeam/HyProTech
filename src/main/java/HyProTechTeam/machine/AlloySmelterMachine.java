package HyProTechTeam.machine;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import java.util.List;

/** Implements the alloy smelter's tier settings, energy use, and processing cycle. */
public final class AlloySmelterMachine implements MachineDefinition {
    public static final String ID = "HyProTech:alloy_smelter";
    private static final short INPUT_SLOT_START = 0;
    private static final int INPUT_SLOT_COUNT = AlloySmelterConfig.INPUT_SLOT_COUNT;
    private static final short OUTPUT_SLOT_START = (short) INPUT_SLOT_COUNT;
    private static final int OUTPUT_SLOT_COUNT = AlloySmelterConfig.OUTPUT_SLOT_COUNT;

    /** Returns the registry ID for this machine definition. */
    @Override
    public String id() {
        return ID;
    }

    /** Accepts the machine's base, tiered, and state block IDs. */
    @Override
    public boolean matchesBlockId(String blockId) {
        return TieredIdUtil.isTieredId(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER)
                || isIdOrState(blockId, HyProTechIds.BLOCK_ALLOY_SMELTER);
    }

    /** Creates machine state at the minimum tier with its initial processing delay. */
    @Override
    public MachineComponent createMachineComponent() {
        MachineComponent component = new MachineComponent();
        component.setMachineId(ID);
        component.setTier(AlloySmelterConfig.MIN_TIER);
        component.setProgressMax(AlloySmelterConfig.getProcessingDelayTicks(AlloySmelterConfig.MIN_TIER));
        return component;
    }

    /** Creates the initial machine energy node using tier-specific configuration. */
    @Override
    public EnergyNodeComponent createEnergyNode() {
        EnergyNodeComponent node = new EnergyNodeComponent();
        node.setNodeType(EnergyNodeComponent.NodeType.MACHINE);
        node.setCapacity(AlloySmelterConfig.getCapacityForTier(AlloySmelterConfig.MIN_TIER));
        node.setMaxTransfer(AlloySmelterConfig.getMaxTransferForTier(AlloySmelterConfig.MIN_TIER));
        node.setConsumption(AlloySmelterConfig.getConsumptionPerSecond(AlloySmelterConfig.MIN_TIER));
        node.setInputMask(EnergySide.ALL_MASK);
        node.setOutputMask(EnergySide.ALL_MASK);
        return node;
    }

    /** Reconciles the machine and energy node with the current tier configuration. */
    @Override
    public void configureDefaults(MachineComponent machine, EnergyNodeComponent energy) {
        if (machine == null || energy == null) {
            return;
        }
        if (energy.getNodeType() != EnergyNodeComponent.NodeType.MACHINE) {
            energy.setNodeType(EnergyNodeComponent.NodeType.MACHINE);
        }
        if (energy.getInputMask() != EnergySide.ALL_MASK) {
            energy.setInputMask(EnergySide.ALL_MASK);
        }
        if (energy.getOutputMask() != EnergySide.ALL_MASK) {
            energy.setOutputMask(EnergySide.ALL_MASK);
        }
        // Apply tier settings and clamp stored energy if the configured capacity shrinks.
        int tier = AlloySmelterConfig.clampTier(machine.getTier());
        if (machine.getTier() != tier) {
            machine.setTier(tier);
        }
        int capacity = AlloySmelterConfig.getCapacityForTier(tier);
        if (energy.getCapacity() != capacity) {
            energy.setCapacity(capacity);
            if (energy.getEnergy() > capacity) {
                energy.setEnergy(capacity);
            }
        }
        int consumption = AlloySmelterConfig.getConsumptionPerSecond(tier);
        if (energy.getConsumption() != consumption) {
            energy.setConsumption(consumption);
        }
        int maxTransfer = AlloySmelterConfig.getMaxTransferForTier(tier);
        if (energy.getMaxTransfer() != maxTransfer) {
            energy.setMaxTransfer(maxTransfer);
        }
        // Keep the machine and energy progress fields synchronized for consumers such as UI.
        int desiredProgressMax = AlloySmelterConfig.getProcessingDelayTicks(tier);
        if (machine.getProgressMax() != desiredProgressMax) {
            machine.setProgressMax(desiredProgressMax);
            if (machine.getProgress() > desiredProgressMax) {
                machine.setProgress(0);
            }
        }
        if (energy.getProgressMax() != desiredProgressMax) {
            energy.setProgressMax(desiredProgressMax);
        }
        if (energy.getProgress() != machine.getProgress()) {
            energy.setProgress(machine.getProgress());
        }
    }

    /** Validates input and output space, consumes energy, and advances one processing tick. */
    @Override
    public boolean tick(MachineContext context, float deltaSeconds) {
        if (context == null) {
            return false;
        }
        MachineComponent machine = context.getMachine();
        EnergyNodeComponent energy = context.getEnergyNode();
        if (machine == null || energy == null) {
            return false;
        }

        // Record relevant values before configuration so changed settings can be persisted.
        int beforeProgress = machine.getProgress();
        int beforeProgressMax = machine.getProgressMax();
        int beforeTier = machine.getTier();
        int beforeCapacity = energy.getCapacity();
        int beforeConsumption = energy.getConsumption();
        int beforeMaxTransfer = energy.getMaxTransfer();

        configureDefaults(machine, energy);

        boolean changed = beforeProgress != machine.getProgress()
                || beforeProgressMax != machine.getProgressMax()
                || beforeTier != machine.getTier()
                || beforeCapacity != energy.getCapacity()
                || beforeConsumption != energy.getConsumption()
                || beforeMaxTransfer != energy.getMaxTransfer();

        // A disabled machine resets progress and does no processing.
        if (!machine.isEnabled()) {
            if (machine.getProgress() != 0) {
                machine.setProgress(0);
                changed = true;
            }
            if (changed) {
                context.markDirty();
            }
            return changed;
        }

        // Without the required inventory and matching inputs, no recipe can advance.
        ItemContainer container = context.getItemContainer();
        if (container == null || container.getCapacity() < INPUT_SLOT_COUNT) {
            if (changed) {
                context.markDirty();
            }
            return changed;
        }

        // Find the first recipe for which the input slots contain enough items.
        AlloySmelterRecipes.RecipeEntry recipe = findMatchingRecipe(container);
        if (recipe == null) {
            if (machine.getProgress() != 0) {
                machine.setProgress(0);
                changed = true;
            }
            if (changed) {
                context.markDirty();
            }
            return changed;
        }

        int tier = AlloySmelterConfig.clampTier(machine.getTier());
        String primaryInputId = resolvePrimaryInputId(recipe);
        if (!canFitOutputs(container, recipe, tier, primaryInputId)) {
            if (changed) {
                context.markDirty();
            }
            return changed;
        }
        if (!hasInputs(container, recipe)) {
            if (machine.getProgress() != 0) {
                machine.setProgress(0);
                changed = true;
            }
            if (changed) {
                context.markDirty();
            }
            return changed;
        }

        // Convert per-second consumption to this tick's cost, rounding positive costs up to one.
        int consumptionPerSecond = Math.max(0, energy.getConsumption());
        int cost = 0;
        if (consumptionPerSecond > 0 && deltaSeconds > 0f) {
            cost = (int) Math.round(consumptionPerSecond * deltaSeconds);
            if (cost < 1) {
                cost = 1;
            }
        }
        // Try the transactional adapter first; fall back to the node's stored amount.
        if (cost > 0 && !consumeEnergy(context.getEnergyStorage(), cost)) {
            int available = energy.getEnergy();
            if (available >= cost) {
                energy.setEnergy(available - cost);
            } else {
                if (changed) {
                    context.markDirty();
                }
                return changed;
            }
        }

        // Advance by one processing step; produce output when the threshold is reached.
        int progressMax = Math.max(1, machine.getProgressMax());
        int progress = machine.getProgress() + 1;
        if (progress < progressMax) {
            machine.setProgress(progress);
            changed = true;
            context.markDirty();
            return true;
        }

        // Consume input only when applying the outputs reports success.
        machine.setProgress(0);
        boolean outputAdded = applyOutputs(container, recipe, tier, primaryInputId);
        if (outputAdded) {
            consumeInputs(container, recipe);
            MachineItemAccess.markContainerDirty(context.getWorld(), context.getX(), context.getY(), context.getZ());
            changed = true;
        }
        context.markDirty();
        return changed;
    }

    /** Finds the first recipe whose required input quantities are present. */
    private AlloySmelterRecipes.RecipeEntry findMatchingRecipe(ItemContainer container) {
        if (container == null) {
            return null;
        }
        List<AlloySmelterRecipes.RecipeEntry> recipes = AlloySmelterRecipes.getRecipes();
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        for (AlloySmelterRecipes.RecipeEntry recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            if (hasInputs(container, recipe)) {
                return recipe;
            }
        }
        return null;
    }

    /** Checks all valid ingredient entries against the aggregate input-slot counts. */
    private boolean hasInputs(ItemContainer container, AlloySmelterRecipes.RecipeEntry recipe) {
        if (container == null || recipe == null) {
            return false;
        }
        MaterialQuantity[] inputs = recipe.inputs == null ? MaterialQuantity.EMPTY_ARRAY : recipe.inputs;
        boolean foundAny = false;
        for (MaterialQuantity input : inputs) {
            if (input == null || input.getItemId() == null || input.getItemId().isEmpty()) {
                continue;
            }
            foundAny = true;
            int required = Math.max(1, input.getQuantity());
            int available = countInputItem(container, input.getItemId());
            if (available < required) {
                return false;
            }
        }
        return foundAny;
    }

    /** Removes each recipe ingredient across matching input slots. */
    private void consumeInputs(ItemContainer container, AlloySmelterRecipes.RecipeEntry recipe) {
        if (container == null || recipe == null) {
            return;
        }
        MaterialQuantity[] inputs = recipe.inputs == null ? MaterialQuantity.EMPTY_ARRAY : recipe.inputs;
        for (MaterialQuantity input : inputs) {
            if (input == null || input.getItemId() == null || input.getItemId().isEmpty()) {
                continue;
            }
            int remaining = Math.max(1, input.getQuantity());
            for (short slot = 0; slot < INPUT_SLOT_COUNT && remaining > 0; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || ItemStack.isEmpty(stack)) {
                    continue;
                }
                if (!stack.getItemId().equalsIgnoreCase(input.getItemId())) {
                    continue;
                }
                int remove = Math.min(remaining, stack.getQuantity());
                container.removeItemStackFromSlot(slot, remove);
                remaining -= remove;
            }
        }
    }

    /** Sums matching item quantities across the input slots. */
    private int countInputItem(ItemContainer container, String itemId) {
        if (container == null || itemId == null || itemId.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (short slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            if (itemId.equalsIgnoreCase(stack.getItemId())) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    /** Returns the first valid recipe input ID, falling back to its legacy input ID. */
    private String resolvePrimaryInputId(AlloySmelterRecipes.RecipeEntry recipe) {
        if (recipe == null) {
            return null;
        }
        MaterialQuantity[] inputs = recipe.inputs == null ? MaterialQuantity.EMPTY_ARRAY : recipe.inputs;
        for (MaterialQuantity input : inputs) {
            if (input != null && input.getItemId() != null && !input.getItemId().isEmpty()) {
                return input.getItemId();
            }
        }
        return recipe.inputItemId;
    }

    /** Checks each possible output against the output slot range before processing. */
    private boolean canFitOutputs(
            ItemContainer container,
            AlloySmelterRecipes.RecipeEntry recipe,
            int tier,
            String inputItemId) {
        List<MaterialQuantity> outputs = buildOutputList(recipe, tier, inputItemId, false);
        if (outputs.isEmpty()) {
            return false;
        }
        for (MaterialQuantity output : outputs) {
            if (output == null || output.getItemId() == null || output.getItemId().isEmpty()) {
                continue;
            }
            if (!canFitOutput(container, output.getItemId(), Math.max(1, output.getQuantity()))) {
                return false;
            }
        }
        return true;
    }

    /** Delegates a single output capacity check to the shared inventory helper. */
    private boolean canFitOutput(ItemContainer container, String itemId, int quantity) {
        return MachineCommonUtil.canFitOutput(
                container,
                itemId,
                quantity,
                OUTPUT_SLOT_START,
                OUTPUT_SLOT_COUNT);
    }

    /** Rolls bonus outputs and attempts to add each output to the inventory. */
    private boolean applyOutputs(
            ItemContainer container,
            AlloySmelterRecipes.RecipeEntry recipe,
            int tier,
            String inputItemId) {
        boolean addedAny = false;
        List<MaterialQuantity> outputs = buildOutputList(recipe, tier, inputItemId, true);
        for (MaterialQuantity output : outputs) {
            if (output == null || output.getItemId() == null || output.getItemId().isEmpty()) {
                continue;
            }
            int qty = Math.max(1, output.getQuantity());
            ItemStack stack = new ItemStack(output.getItemId(), qty);
            boolean placed = false;
            for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
                short slot = (short) (OUTPUT_SLOT_START + i);
                if (slot < 0 || slot >= container.getCapacity()) {
                    continue;
                }
                ItemStack existing = container.getItemStack(slot);
                if (existing != null && !ItemStack.isEmpty(existing)
                        && !existing.getItemId().equalsIgnoreCase(output.getItemId())) {
                    continue;
                }
                if (addToOutputSlots(container, output.getItemId(), qty)) {
                    placed = true;
                    addedAny = true;
                    break;
                }
            }
            if (!placed) {
                return false;
            }
        }
        return addedAny;
    }

    /** Adds an item to the configured output slot range. */
    private boolean addToOutputSlots(ItemContainer container, String itemId, int quantity) {
        return MachineCommonUtil.addToOutputSlots(
                container,
                itemId,
                quantity,
                OUTPUT_SLOT_START,
                OUTPUT_SLOT_COUNT);
    }

    /** Builds multiplied recipe outputs and adds eligible bonus drops, optionally rolling chances. */
    private List<MaterialQuantity> buildOutputList(
            AlloySmelterRecipes.RecipeEntry recipe,
            int tier,
            String inputItemId,
            boolean rollBonuses) {
        List<MaterialQuantity> list = new java.util.ArrayList<>();
        int multiplier = Math.max(1, AlloySmelterConfig.getOutputMultiplierForTier(tier));

        // Support both the legacy single output and the newer output array.
        MaterialQuantity[] outputs = recipe.outputs;
        if (outputs == null || outputs.length == 0) {
            list.add(new MaterialQuantity(
                    recipe.outputItemId,
                    null,
                    null,
                    Math.max(1, recipe.outputQuantity) * multiplier,
                    null));
        } else {
            for (MaterialQuantity output : outputs) {
                if (output == null || output.getItemId() == null || output.getItemId().isEmpty()) {
                    continue;
                }
                int qty = Math.max(1, output.getQuantity()) * multiplier;
                list.add(new MaterialQuantity(output.getItemId(), null, null, qty, null));
            }
        }

        List<AlloySmelterConfig.BonusDrop> bonuses =
                AlloySmelterConfig.getBonusDropsForInput(inputItemId);
        if (bonuses.isEmpty()) {
            return list;
        }

        // Capacity checks include eligible bonuses; actual processing rolls their chances.
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (AlloySmelterConfig.BonusDrop bonus : bonuses) {
            if (bonus == null) {
                continue;
            }
            double chance = bonus.getChance(tier);
            if (chance <= 0.0) {
                continue;
            }
            if (rollBonuses && random.nextDouble() > chance) {
                continue;
            }
            String bonusItemId = bonus.getItemId();
            if (bonusItemId == null || bonusItemId.isEmpty()) {
                continue;
            }
            list.add(new MaterialQuantity(bonusItemId, null, null, Math.max(1, bonus.getQuantity()), null));
        }

        return list;
    }

    /** Delegates all-or-nothing energy extraction to the shared transactional helper. */
    private boolean consumeEnergy(EnergyStorage storage, int amount) {
        return MachineCommonUtil.consumeEnergy(storage, amount);
    }

    /** Matches a base block ID or one of its state variants. */
    private static boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }
}
