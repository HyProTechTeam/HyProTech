package HyProTechTeam.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

/** Validates item IDs before they are passed to UI item displays. */
final class UiItemIds {
    private static final String EMPTY_ITEM_ID = ItemStack.EMPTY.getItemId();

    private UiItemIds() {
    }

    /** Treats null, empty strings, and the empty-stack ID as empty. */
    static boolean isEmptyItemId(String itemId) {
        return itemId == null
                || itemId.isEmpty()
                || (EMPTY_ITEM_ID != null && EMPTY_ITEM_ID.equalsIgnoreCase(itemId));
    }

    /** Converts an empty item ID to an empty string. */
    static String safeItemId(String itemId) {
        return isEmptyItemId(itemId) ? "" : itemId;
    }

    /** Accepts known non-state item assets and rejects empty or unknown IDs. */
    static boolean isKnownItemId(String itemId) {
        if (isEmptyItemId(itemId)) {
            return false;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null && item != Item.UNKNOWN && !item.isState();
    }
}
