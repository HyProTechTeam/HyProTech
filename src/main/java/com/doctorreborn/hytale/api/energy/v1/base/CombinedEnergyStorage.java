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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.shailist.hytale.api.transfer.v1.storage.StoragePreconditions;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/**
 * A {@link EnergyStorage} wrapping multiple storages.
 *
 * <p>
 * The storages passed to
 * {@linkplain CombinedEnergyStorage#CombinedEnergyStorage the
 * constructor} will be iterated in order.
 *
 * @param <S> The class of every part. {@code ? extends EnergyStorage} can be
 *            used if the parts are of different types.
 */
public class CombinedEnergyStorage<S extends EnergyStorage> implements EnergyStorage {
    /**
     * The list of backing parts that make up this combined storage. Iterated in
     * order.
     */
    public List<S> parts;

    /**
     * Create a combined storage delegating to the provided parts.
     *
     * @param parts The list of parts backing this combined storage.
     */
    public CombinedEnergyStorage(List<S> parts) {
        this.parts = parts;
    }

    /** Reports insertion support when at least one wrapped part supports it. */
    @Override
    public boolean supportsInsertion() {
        for (S part : parts) {
            if (part.supportsInsertion()) {
                return true;
            }
        }

        return false;
    }

    /** Offers each part the remaining amount until the request is satisfied. */
    @Override
    public long insert(long maxAmount, @NonNull TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        long amount = 0;

        for (S part : parts) {
            amount += part.insert(maxAmount - amount, transaction);
            if (amount == maxAmount)
                break;
        }

        return amount;
    }

    /** Reports extraction support when at least one wrapped part supports it. */
    @Override
    public boolean supportsExtraction() {
        for (S part : parts) {
            if (part.supportsExtraction()) {
                return true;
            }
        }

        return false;
    }

    /** Requests the remaining amount from each part in order. */
    @Override
    public long extract(long maxAmount, @NonNull TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        long amount = 0;

        for (S part : parts) {
            amount += part.extract(maxAmount - amount, transaction);
            if (amount == maxAmount)
                break;
        }

        return amount;
    }

    /** Iterates over the views of each part in order. */
    @Override
    public @NonNull Iterator<EnergyStorageView> iterator() {
        return new CombinedIterator();
    }

    /** Displays the wrapped parts for diagnostics. */
    @Override
    public String toString() {
        StringJoiner partNames = new StringJoiner(", ");

        for (S part : parts) {
            partNames.add(part.toString());
        }

        return "CombinedEnergyStorage[" + partNames + "]";
    }

    /**
     * The combined iterator for multiple storages.
     */
    private class CombinedIterator implements Iterator<EnergyStorageView> {
        final Iterator<S> partIterator = parts.iterator();
        // Always holds the next EnergyStorageView, except during next() while the
        // iterator is being advanced.
        Iterator<? extends EnergyStorageView> currentPartIterator = null;

        CombinedIterator() {
            advanceCurrentPartIterator();
        }

        /** Reports whether the current backing iterator has another view. */
        @Override
        public boolean hasNext() {
            return currentPartIterator != null && currentPartIterator.hasNext();
        }

        /** Returns the next view and advances to the next nonempty part when necessary. */
        @Override
        public EnergyStorageView next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            EnergyStorageView returned = currentPartIterator.next();

            // Advance the current part iterator
            if (!currentPartIterator.hasNext()) {
                advanceCurrentPartIterator();
            }

            return returned;
        }

        /** Skips empty part iterators until a view is found or the parts are exhausted. */
        private void advanceCurrentPartIterator() {
            while (partIterator.hasNext()) {
                this.currentPartIterator = partIterator.next().iterator();

                if (this.currentPartIterator.hasNext()) {
                    break;
                }
            }
        }
    }
}
