package com.shailist.hytale.api.transfer.v1.storage;

import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import java.util.Collections;
import java.util.Iterator;

/** Transactional insertion and extraction operations for a resource type. */
public interface Storage<T> extends Iterable<StorageView<T>> {
    /** Requests insertion of up to maxAmount units in the given transaction. */
    long insert(T resource, long maxAmount, TransactionContext transaction);

    /** Requests extraction of up to maxAmount units in the given transaction. */
    long extract(T resource, long maxAmount, TransactionContext transaction);

    /** Returns no views by default; implementations can override this. */
    @Override
    default Iterator<StorageView<T>> iterator() {
        return Collections.emptyIterator();
    }
}
