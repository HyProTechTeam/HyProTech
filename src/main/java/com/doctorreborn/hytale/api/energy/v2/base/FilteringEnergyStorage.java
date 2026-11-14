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

package com.doctorreborn.hytale.api.energy.v2.base;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v2.Energy;
import com.google.common.base.Supplier;
import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.storage.base.FilteringStorage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Energy-specific wrapper that forwards transfers permitted by its filter predicates. */
public class FilteringEnergyStorage extends FilteringStorage<Energy> {
    /** Wraps a fixed backing storage. */
    public FilteringEnergyStorage(Storage<Energy> backingStorage) {
        super(backingStorage);
    }

    /** Uses a supplier to resolve the backing storage. */
    public FilteringEnergyStorage(Supplier<Storage<Energy>> backingStorage) {
        super(backingStorage);
    }

    /**
     * Return true if insertion of the passed resource should be forwarded to the
     * backing storage, or false if it should fail.
     *
     * @param resource The resource to test for insertion.
     * @param amount   The amount of the resource to test for insertion.
     * @return True if insertion of the passed resource should be forwarded to the
     *         backing storage, false otherwise.
     */
    protected boolean canInsert(Energy resource, long amount) {
        return true;
    }

    /**
     * Return true if extraction of the passed resource should be forwarded to the
     * backing storage, or false if it should fail.
     *
     * @param resource The resource to test for extraction.
     * @param amount   The amount of the resource to test for extraction.
     * @return True if extraction of the passed resource should be forwarded to the
     *         backing storage, false otherwise.
     */
    protected boolean canExtract(Energy resource, long amount) {
        return true;
    }

    /** Forwards allowed insertions and returns zero when the filter rejects them. */
    @Override
    public long insert(Energy resource, long maxAmount, @NonNull TransactionContext transaction) {
        if (canInsert(resource, maxAmount)) {
            return backingStorage.get().insert(resource, maxAmount, transaction);
        } else {
            return 0;
        }
    }

    /** Inserts the singleton energy resource. */
    public long insert(long maxAmount, @NonNull TransactionContext transaction) {
        return insert(Energy.INSTANCE, maxAmount, transaction);
    }

    /** Forwards allowed extractions and returns zero when the filter rejects them. */
    @Override
    public long extract(Energy resource, long maxAmount, @NonNull TransactionContext transaction) {
        if (canExtract(resource, maxAmount)) {
            return backingStorage.get().extract(resource, maxAmount, transaction);
        } else {
            return 0;
        }
    }

    /** Extracts the singleton energy resource. */
    public long extract(long maxAmount, @NonNull TransactionContext transaction) {
        return extract(Energy.INSTANCE, maxAmount, transaction);
    }

    /** Displays the resolved storage and supplier for diagnostics. */
    @Override
    public String toString() {
        return "FilteringEnergyStorage[" + backingStorage.get() + "/" + backingStorage + "]";
    }
}
