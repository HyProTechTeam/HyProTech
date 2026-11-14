package com.shailist.hytale.api.transfer.v1.storage.base;

import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.storage.StorageView;
import java.util.Collections;
import java.util.Iterator;

/** Storage whose only view is the storage itself. */
public interface SingleSlotStorage<T> extends Storage<T>, StorageView<T> {
    /** Iterates once over this storage view. */
    @Override
    default Iterator<StorageView<T>> iterator() {
        return Collections.singleton((StorageView<T>) this).iterator();
    }
}
