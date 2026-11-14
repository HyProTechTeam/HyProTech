/*
 * Copyright (C) 2026 DoctorReborn and contributors
 *
 * This file is part of Energy API for Hytale.
 *
 * Energy API is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Energy API is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Energy API. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2025 Shai List and contributors
 * Licensed under the MIT license. See LICENSE-SHAILIST file in the project root for details.
 *
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 */

package com.doctorreborn.hytale.api.energy.v1.base;

import java.util.Iterator;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.google.common.collect.Iterators;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/**
 * A base {@link EnergyStorage} implementation that delegates every call to
 * another storage, except that it only allows insertion or extraction if
 * {@link #canInsert} or {@link #canExtract} allows it respectively.
 * This can for example be used to wrap the internal storage of some device
 * behind additional insertion or extraction checks.
 * If one of these two functions is overridden to always return false,
 * implementors may also wish to override
 * {@link #supportsInsertion} and/or {@link #supportsExtraction}.
 *
 * <p>
 * The static functions can be used when insertion or/and extraction should be
 * blocked entirely.
 */
public abstract class FilteringEnergyStorage implements EnergyStorage {
    /**
     * Return a wrapper over the passed storage that prevents extraction.
     * 
     * @param backingStorage The storage to wrap.
     * @return A storage that allows insertion only.
     */
    public static EnergyStorage insertOnlyOf(EnergyStorage backingStorage) {
        return of(backingStorage, true, false);
    }

    /**
     * Return a wrapper over the passed storage that prevents insertion.
     *
     * @param backingStorage The storage to wrap.
     * @return A storage that allows extraction only.
     */
    public static EnergyStorage extractOnlyOf(EnergyStorage backingStorage) {
        return of(backingStorage, false, true);
    }

    /**
     * Return a wrapper over the passed storage that prevents insertion and
     * extraction.
     *
     * @param backingStorage The storage to wrap.
     * @return A read-only wrapper around the backing storage.
     */
    public static EnergyStorage readOnlyOf(EnergyStorage backingStorage) {
        return of(backingStorage, false, false);
    }

    /**
     * Return a wrapper over the passed storage that may prevent insertion or
     * extraction, depending on the boolean parameters.
     * For more fine-grained control, a custom subclass of {@link FilteringStorage}
     * should be used.
     *
     * @param backingStorage Storage to wrap.
     * @param allowInsert    True to allow insertion, false to block insertion.
     * @param allowExtract   True to allow extraction, false to block extraction.
     * @return A filtering wrapper over {@code backingStorage} implementing the
     *         requested allowances.
     */
    public static EnergyStorage of(EnergyStorage backingStorage, boolean allowInsert, boolean allowExtract) {
        if (allowInsert && allowExtract) {
            return backingStorage;
        }

        // Preserve restrictions already applied by an existing filtering wrapper.
        if (backingStorage instanceof FilteringEnergyStorage backingFilteringStorage) {
            return new FilteringEnergyStorage(backingFilteringStorage) {
                /** Requires both this wrapper and the existing wrapper to allow insertion. */
                @Override
                protected boolean canInsert(long amount) {
                    return allowInsert && backingFilteringStorage.canInsert(amount);
                }

                /** Requires both wrappers to allow extraction. */
                @Override
                protected boolean canExtract(long amount) {
                    return allowExtract && backingFilteringStorage.canExtract(amount);
                }

                /** Reports insertion support only if all restrictions and the backing storage permit it. */
                @Override
                public boolean supportsInsertion() {
                    return allowInsert && backingFilteringStorage.supportsInsertion() && super.supportsInsertion();
                }

                /** Reports extraction support only if all restrictions and the backing storage permit it. */
                @Override
                public boolean supportsExtraction() {
                    return allowExtract && backingFilteringStorage.supportsExtraction() && super.supportsExtraction();
                }
            };
        } else {
            return new FilteringEnergyStorage(backingStorage) {
                /** Uses the requested fixed insertion policy. */
                @Override
                protected boolean canInsert(long amount) {
                    return allowInsert;
                }

                /** Uses the requested fixed extraction policy. */
                @Override
                protected boolean canExtract(long amount) {
                    return allowExtract;
                }

                /** Combines the fixed insertion policy with backing-storage support. */
                @Override
                public boolean supportsInsertion() {
                    return allowInsert && super.supportsInsertion();
                }

                /** Combines the fixed extraction policy with backing-storage support. */
                @Override
                public boolean supportsExtraction() {
                    return allowExtract && super.supportsExtraction();
                }
            };
        }
    }

    /**
     * Supplier that provides the backing storage used by this filtering wrapper.
     */
    protected final Supplier<EnergyStorage> backingStorage;

    /**
     * Create a new filtering storage, with a fixed backing storage.
     *
     * @param backingStorage The backing storage used by this filtering wrapper.
     */
    public FilteringEnergyStorage(EnergyStorage backingStorage) {
        this(() -> backingStorage);
    }

    /**
     * Create a new filtering storage, with a supplier for the backing storage.
     * This allows the backing storage to change without having to create a new
     * filtering storage.
     * If that is unnecessary, the other overload can be used for convenience.
     *
     * @param backingStorage Supplier that provides the backing storage used by this
     *                       filtering wrapper.
     */
    public FilteringEnergyStorage(Supplier<EnergyStorage> backingStorage) {
        this.backingStorage = backingStorage;
    }

    /**
     * Return true if insertion should be forwarded to the backing storage, or
     * false if it should fail.
     *
     * @return True if insertion should be forwarded to the backing storage,
     *         false otherwise.
     */
    protected boolean canInsert(long amount) {
        return true;
    }

    /**
     * Return true if extraction should be forwarded to the backing storage, or
     * false if it should fail.
     *
     * @return True if extraction should be forwarded to the backing storage,
     *         false otherwise.
     */
    protected boolean canExtract(long amount) {
        return true;
    }

    /** Delegates the insertion capability to the current backing storage. */
    @Override
    public boolean supportsInsertion() {
        return backingStorage.get().supportsInsertion();
    }

    /** Forwards insertion only when the predicate accepts the requested amount. */
    @Override
    public long insert(long maxAmount, @NonNull TransactionContext transaction) {
        if (canInsert(maxAmount)) {
            return backingStorage.get().insert(maxAmount, transaction);
        } else {
            return 0;
        }
    }

    /** Delegates the extraction capability to the current backing storage. */
    @Override
    public boolean supportsExtraction() {
        return backingStorage.get().supportsExtraction();
    }

    /** Forwards extraction only when the predicate accepts the requested amount. */
    @Override
    public long extract(long maxAmount, @NonNull TransactionContext transaction) {
        if (canExtract(maxAmount)) {
            return backingStorage.get().extract(maxAmount, transaction);
        } else {
            return 0;
        }
    }

    /** Wraps each backing view so extraction through it also passes the filter. */
    @Override
    public @NonNull Iterator<EnergyStorageView> iterator() {
        return Iterators.transform(backingStorage.get().iterator(), FilteringEnergyStorageView::new);
    }

    /** Returns the backing storage's version. */
    @Override
    public long getVersion() {
        return backingStorage.get().getVersion();
    }

    /** Displays the backing storage and supplier for diagnostics. */
    @Override
    public String toString() {
        return "FilteringEnergyStorage[" + backingStorage.get() + "/" + backingStorage + "]";
    }

    /**
     * This is used to ensure extractions through storage views of the backing
     * stored also get checked by {@link #canExtract}.
     */
    private class FilteringEnergyStorageView implements EnergyStorageView {
        private final EnergyStorageView backingView;

        private FilteringEnergyStorageView(EnergyStorageView backingView) {
            this.backingView = backingView;
        }

        /** Applies the outer extraction predicate before forwarding to the backing view. */
        @Override
        public long extract(long maxAmount, @NonNull TransactionContext transaction) {
            if (canExtract(maxAmount)) {
                return backingView.extract(maxAmount, transaction);
            } else {
                return 0;
            }
        }

        /** Returns the backing view's current energy amount. */
        @Override
        public long getAmount() {
            return backingView.getAmount();
        }

        /** Returns the backing view's capacity. */
        @Override
        public long getCapacity() {
            return backingView.getCapacity();
        }

        /** Exposes the underlying view reported by the backing view. */
        @Override
        public EnergyStorageView getUnderlyingView() {
            return backingView.getUnderlyingView();
        }
    }
}
