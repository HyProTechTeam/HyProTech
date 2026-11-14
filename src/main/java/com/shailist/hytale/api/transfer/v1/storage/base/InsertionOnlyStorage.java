package com.shailist.hytale.api.transfer.v1.storage.base;

import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Storage that declines extraction by default. */
public interface InsertionOnlyStorage<T> extends Storage<T> {
    /** Returns zero because this storage does not extract resources. */
    @Override
    default long extract(T resource, long maxAmount, TransactionContext transaction) {
        return 0;
    }
}
