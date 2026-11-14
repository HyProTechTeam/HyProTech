package com.shailist.hytale.api.transfer.v1.storage.base;

import com.google.common.base.Supplier;
import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.storage.StorageView;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import java.util.Iterator;

/** Delegates to a backing storage while allowing subclasses to filter transfers. */
public class FilteringStorage<T> implements Storage<T> {
    protected final Supplier<Storage<T>> backingStorage;

    /** Wraps a fixed backing storage. */
    public FilteringStorage(Storage<T> backingStorage) {
        this(() -> backingStorage);
    }

    /** Uses a supplier to obtain the backing storage for each operation. */
    public FilteringStorage(Supplier<Storage<T>> backingStorage) {
        this.backingStorage = backingStorage;
    }

    /** Allows insertion by default; subclasses can override this predicate. */
    protected boolean canInsert(T resource, long amount) {
        return true;
    }

    /** Allows extraction by default; subclasses can override this predicate. */
    protected boolean canExtract(T resource, long amount) {
        return true;
    }

    /** Forwards allowed insertions and returns zero when filtered out. */
    @Override
    public long insert(T resource, long maxAmount, TransactionContext transaction) {
        if (canInsert(resource, maxAmount)) {
            return backingStorage.get().insert(resource, maxAmount, transaction);
        }
        return 0;
    }

    /** Forwards allowed extractions and returns zero when filtered out. */
    @Override
    public long extract(T resource, long maxAmount, TransactionContext transaction) {
        if (canExtract(resource, maxAmount)) {
            return backingStorage.get().extract(resource, maxAmount, transaction);
        }
        return 0;
    }

    /** Wraps each backing view so extraction through the view is filtered too. */
    @Override
    public Iterator<StorageView<T>> iterator() {
        Iterator<StorageView<T>> backingIterator = backingStorage.get().iterator();
        return new Iterator<>() {
            /** Delegates availability to the backing iterator. */
            @Override
            public boolean hasNext() {
                return backingIterator.hasNext();
            }

            /** Wraps the next view so extraction continues to honor this filter. */
            @Override
            public StorageView<T> next() {
                return new FilteringStorageView(backingIterator.next());
            }

            /** Delegates removal to the backing iterator. */
            @Override
            public void remove() {
                backingIterator.remove();
            }
        };
    }

    /** Delegates view properties and applies the outer extraction predicate. */
    private class FilteringStorageView implements StorageView<T> {
        private final StorageView<T> backingView;

        private FilteringStorageView(StorageView<T> backingView) {
            this.backingView = backingView;
        }

        /** Delegates the blank-resource check to the backing view. */
        @Override
        public boolean isResourceBlank() {
            return backingView.isResourceBlank();
        }

        /** Returns the backing view's resource. */
        @Override
        public T getResource() {
            return backingView.getResource();
        }

        /** Returns the backing view's current amount. */
        @Override
        public long getAmount() {
            return backingView.getAmount();
        }

        /** Returns the backing view's capacity. */
        @Override
        public long getCapacity() {
            return backingView.getCapacity();
        }

        /** Applies the outer extraction predicate before forwarding to the backing view. */
        @Override
        public long extract(T resource, long maxAmount, TransactionContext transaction) {
            if (canExtract(resource, maxAmount)) {
                return backingView.extract(resource, maxAmount, transaction);
            }
            return 0;
        }

        /** Returns the underlying view exposed by the backing view. */
        @Override
        public StorageView<T> getUnderlyingView() {
            return backingView.getUnderlyingView();
        }
    }
}
