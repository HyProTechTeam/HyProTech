package com.shailist.hytale.api.transfer.v1.storage.base;

import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Storage that declines insertion by default. */
public interface ExtractionOnlyStorage<T> extends Storage<T> {
    /** Returns zero because this storage does not insert resources. */
    @Override
    default long insert(T resource, long maxAmount, TransactionContext transaction) {
        return 0;
    }
}
