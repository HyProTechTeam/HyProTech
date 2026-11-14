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

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v2.Energy;
import com.shailist.hytale.api.transfer.v1.storage.base.CombinedSlottedStorage;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Combines multiple single energy buffers into one slotted storage. */
public class MultiBufferEnergyStorage extends CombinedSlottedStorage<Energy, SingleBufferEnergyStorage> {

    /** Wraps the supplied buffer list. */
    public MultiBufferEnergyStorage(List<SingleBufferEnergyStorage> parts) {
        super(parts);
    }

    /** Inserts the singleton energy resource through the combined storage. */
    public long insert(long maxAmount, @NonNull TransactionContext transaction) {
        return insert(Energy.INSTANCE, maxAmount, transaction);
    }

    /** Extracts the singleton energy resource through the combined storage. */
    public long extract(long maxAmount, @NonNull TransactionContext transaction) {
        return extract(Energy.INSTANCE, maxAmount, transaction);
    }

    /** Displays the component buffers for diagnostics. */
    @Override
    public String toString() {
        return "MultiBufferEnergyStorage[" + parts + "]";
    }
}
