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

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.doctorreborn.hytale.api.energy.v1.SlottedEnergyStorage;
import com.shailist.hytale.impl.transfer.TransferApiImpl;

/**
 * A storage that is also its only storage view.
 * It can be used in APIs for storages that are wrappers around a single "slot",
 * or for slightly more convenient implementation.
 */
public interface SingleSlotEnergyStorage extends SlottedEnergyStorage, EnergyStorageView {
    /** Iterates over this storage as its sole view. */
    @Override
    default @NonNull Iterator<EnergyStorageView> iterator() {
        return TransferApiImpl.singletonIterator(this);
    }

    /** A single-slot storage exposes exactly one slot. */
    @Override
    default int getSlotCount() {
        return 1;
    }

    /** Returns this storage for slot zero and rejects all other indices. */
    @Override
    default SingleSlotEnergyStorage getSlot(int slot) {
        if (slot != 0) {
            throw new IndexOutOfBoundsException("Slot " + slot + " does not exist in a single-slot storage.");
        }

        return this;
    }
}
