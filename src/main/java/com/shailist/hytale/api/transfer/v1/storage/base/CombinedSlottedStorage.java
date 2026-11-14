package com.shailist.hytale.api.transfer.v1.storage.base;

import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.storage.StorageView;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import java.util.Iterator;
import java.util.List;

/** Wraps multiple single-slot storages and processes transfers in list order. */
public class CombinedSlottedStorage<T, S extends SingleSlotStorage<T>> implements Storage<T> {
    protected final List<S> parts;

    /** Retains the supplied list of storage parts. */
    public CombinedSlottedStorage(List<S> parts) {
        this.parts = parts;
    }

    /** Offers each part the remaining insertion amount until the request is satisfied. */
    @Override
    public long insert(T resource, long maxAmount, TransactionContext transaction) {
        long amount = 0;
        for (S part : parts) {
            amount += part.insert(resource, maxAmount - amount, transaction);
            if (amount == maxAmount) {
                return amount;
            }
        }
        return amount;
    }

    /** Requests the remaining extraction amount from each part in order. */
    @Override
    public long extract(T resource, long maxAmount, TransactionContext transaction) {
        long amount = 0;
        for (S part : parts) {
            amount += part.extract(resource, maxAmount - amount, transaction);
            if (amount == maxAmount) {
                return amount;
            }
        }
        return amount;
    }

    /** Iterates over the parts as storage views and delegates removal to the list iterator. */
    @Override
    public Iterator<StorageView<T>> iterator() {
        Iterator<S> iterator = parts.iterator();
        return new Iterator<StorageView<T>>() {
            /** Delegates availability to the parts iterator. */
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            /** Returns the next single-slot storage as a view. */
            @Override
            public StorageView<T> next() {
                return iterator.next();
            }

            /** Delegates removal to the parts iterator. */
            @Override
            public void remove() {
                iterator.remove();
            }
        };
    }

    /** Returns the number of parts. */
    public int getSlotCount() {
        return parts.size();
    }

    /** Returns the part at the given list index. */
    public S getSlot(int slot) {
        return parts.get(slot);
    }
}
