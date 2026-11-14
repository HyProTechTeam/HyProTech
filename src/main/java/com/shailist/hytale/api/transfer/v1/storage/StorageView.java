package com.shailist.hytale.api.transfer.v1.storage;

import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** View of one resource and amount in a storage, with transactional extraction. */
public interface StorageView<T> {
    /** Reports whether this view has no resource. */
    boolean isResourceBlank();

    /** Returns the resource represented by this view. */
    T getResource();

    /** Returns the stored amount. */
    long getAmount();

    /** Returns the capacity of this view. */
    long getCapacity();

    /** Requests extraction of up to maxAmount units in the given transaction. */
    long extract(T resource, long maxAmount, TransactionContext transaction);

    /** Returns this view unless an implementation exposes a different underlying view. */
    default StorageView<T> getUnderlyingView() {
        return this;
    }
}
