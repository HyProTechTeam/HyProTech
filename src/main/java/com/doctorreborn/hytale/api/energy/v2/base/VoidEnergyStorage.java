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
import com.shailist.hytale.api.transfer.v1.storage.base.InsertionOnlyStorage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Insertion-only sink that reports accepting the requested amount without retaining it. */
public class VoidEnergyStorage implements InsertionOnlyStorage<Energy> {

    /** Returns the requested amount without storing it. */
    @Override
    public long insert(Energy resource, long maxAmount, @NonNull TransactionContext transaction) {
        return maxAmount;
    }

    /** Invokes insertion for the singleton energy resource. */
    public long insert(long maxAmount, @NonNull TransactionContext transaction) {
        return insert(Energy.INSTANCE, maxAmount, transaction);
    }

    /** The sink never retains energy. */
    public long getAmount() {
        return 0;
    }

    /** Returns a diagnostic name for this sink. */
    @Override
    public String toString() {
        return "VoidEnergyStorage[]";
    }
}
