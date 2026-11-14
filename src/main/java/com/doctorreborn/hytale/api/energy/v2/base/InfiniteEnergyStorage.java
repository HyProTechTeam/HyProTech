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

import java.util.Collections;
import java.util.Iterator;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v2.Energy;

import com.shailist.hytale.api.transfer.v1.storage.StorageView;
import com.shailist.hytale.api.transfer.v1.storage.base.ExtractionOnlyStorage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Extraction-only source that represents an effectively unlimited energy amount. */
public final class InfiniteEnergyStorage implements ExtractionOnlyStorage<Energy> {
    /** Returns Long.MAX_VALUE regardless of the requested amount or transaction. */
    @Override
    public long extract(Energy resource, long maxAmount, @NonNull TransactionContext transaction) {
        return Long.MAX_VALUE;
    }

    /** Invokes extraction for the singleton energy resource. */
    public long extract(long maxAmount, @NonNull TransactionContext transaction) {
        return extract(Energy.INSTANCE, maxAmount, transaction);
    }

    /** Exposes no individual storage views. */
    @Override
    public @NonNull Iterator<StorageView<Energy>> iterator() {
        return Collections.emptyIterator();
    }

    /** Reports Long.MAX_VALUE as the available amount. */
    public long getAmount() {
        return Long.MAX_VALUE;
    }

    /** Returns a diagnostic name for this source. */
    @Override
    public String toString() {
        return "InfiniteEnergyStorage[]";
    }
}
