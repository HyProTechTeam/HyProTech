package HyProTechTeam.machine;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Shared energy and output-inventory operations used by machine implementations. */
final class MachineCommonUtil {
    private MachineCommonUtil() {
    }

    /** Extracts the full requested amount transactionally, rolling back a partial extraction. */
    static boolean consumeEnergy(EnergyStorage storage, int amount) {
        if (storage == null || amount <= 0) {
            return false;
        }
        // Nest in an existing transaction when present; closing without commit rolls back.
        TransactionContext context = TransactionContext.current();
        try (Transaction transaction = Transaction.openNested(context)) {
            long extracted = storage.extract(amount, transaction);
            if (extracted >= amount) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    /** Resolves an item's stack limit, using 100 when the asset or limit is unavailable. */
    static int getMaxStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 100;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null || item == Item.UNKNOWN) {
            return 100;
        }
        int max = item.getMaxStack();
        return max > 0 ? max : 100;
    }

    /** Checks whether the selected slot range can fit the requested item quantity. */
    static boolean canFitOutput(
            ItemContainer container,
            String itemId,
            int quantity,
            short outputSlotStart,
            int outputSlotCount) {
        if (container == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        int remaining = Math.max(1, quantity);
        int maxStack = getMaxStack(itemId);
        // Count space in empty slots and matching stacks without changing the container.
        for (int i = 0; i < outputSlotCount && remaining > 0; i++) {
            short slot = (short) (outputSlotStart + i);
            if (slot < 0 || slot >= container.getCapacity()) {
                continue;
            }
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || ItemStack.isEmpty(existing)) {
                remaining -= maxStack;
                continue;
            }
            if (!existing.getItemId().equalsIgnoreCase(itemId)) {
                continue;
            }
            int space = Math.max(0, maxStack - existing.getQuantity());
            if (space > 0) {
                remaining -= space;
            }
        }
        return remaining <= 0;
    }

    /** Fills matching stacks, then empty slots, and reports whether the whole quantity fit. */
    static boolean addToOutputSlots(
            ItemContainer container,
            String itemId,
            int quantity,
            short outputSlotStart,
            int outputSlotCount) {
        if (container == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        int remaining = Math.max(1, quantity);
        int maxStack = getMaxStack(itemId);

        // Fill existing stacks first, preserving their metadata.
        for (int i = 0; i < outputSlotCount && remaining > 0; i++) {
            short slot = (short) (outputSlotStart + i);
            if (slot < 0 || slot >= container.getCapacity()) {
                continue;
            }
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || ItemStack.isEmpty(existing)) {
                continue;
            }
            if (!existing.getItemId().equalsIgnoreCase(itemId)) {
                continue;
            }
            int space = Math.max(0, maxStack - existing.getQuantity());
            if (space <= 0) {
                continue;
            }
            int add = Math.min(space, remaining);
            ItemStack merged = new ItemStack(itemId, existing.getQuantity() + add, existing.getMetadata());
            container.setItemStackForSlot(slot, merged, true);
            remaining -= add;
        }

        // Put any remaining items into empty slots within the requested range.
        for (int i = 0; i < outputSlotCount && remaining > 0; i++) {
            short slot = (short) (outputSlotStart + i);
            if (slot < 0 || slot >= container.getCapacity()) {
                continue;
            }
            ItemStack existing = container.getItemStack(slot);
            if (existing != null && !ItemStack.isEmpty(existing)) {
                continue;
            }
            int add = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(itemId, add);
            container.setItemStackForSlot(slot, stack, true);
            remaining -= add;
        }

        return remaining <= 0;
    }
}
