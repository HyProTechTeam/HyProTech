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

package com.doctorreborn.hytale.impl.energy;

import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.doctorreborn.hytale.api.energy.v1.SlottedEnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.base.SingleSlotEnergyStorage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Internal empty storage and adapters used by the version 1 energy API. */
public class EnergyApiImpl {
    /** Monotonic version counter used by internal storages. */
    public static final AtomicLong version = new AtomicLong();

    /**
     * Internal empty storage instance used as a placeholder where no storage is
     * available.
     */
    public static final EnergyStorage EMPTY_STORAGE = new EnergyStorage() {
        /** Empty storage does not accept energy. */
        @Override
        public boolean supportsInsertion() {
            return false;
        }

        /** Rejects every insertion and reports zero accepted energy. */
        @Override
        public long insert(long maxAmount, @NonNull TransactionContext transaction) {
            return 0;
        }

        /** Empty storage does not provide extractable energy. */
        @Override
        public boolean supportsExtraction() {
            return false;
        }

        /** Returns zero for every extraction request. */
        @Override
        public long extract(long maxAmount, @NonNull TransactionContext transaction) {
            return 0;
        }

        /** Exposes no storage views. */
        @Override
        public @NonNull Iterator<EnergyStorageView> iterator() {
            return Collections.emptyIterator();
        }

        /** The immutable empty storage always reports version zero. */
        @Override
        public long getVersion() {
            return 0;
        }

        /** Identifies the empty storage in diagnostics. */
        @Override
        public String toString() {
            return "EmptyEnergyStorage";
        }
    };

    /**
     * Create a view over a {@link SlottedEnergyStorage} as a {@link List} of
     * {@link SingleSlotEnergyStorage}.
     *
     * @param storage the slotted storage to wrap
     * @return a list view of the storage slots
     */
    public static List<SingleSlotEnergyStorage> makeListView(SlottedEnergyStorage storage) {
        return new AbstractList<>() {
            /** Reads the requested slot from the backing slotted storage. */
            @Override
            public SingleSlotEnergyStorage get(int index) {
                return storage.getSlot(index);
            }

            /** Keeps the list size synchronized with the backing slot count. */
            @Override
            public int size() {
                return storage.getSlotCount();
            }
        };
    }

    /**
     * Private constructor to avoid instantiation of this utility class.
     */
    private EnergyApiImpl() {
    }
}
