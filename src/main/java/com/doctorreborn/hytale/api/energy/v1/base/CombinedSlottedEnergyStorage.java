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

import java.util.List;
import java.util.StringJoiner;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.SlottedEnergyStorage;

/**
 * A {@link EnergyStorage} wrapping multiple slotted storages.
 * Same as {@link CombinedEnergyStorage}, but for {@link SlottedEnergyStorage}s.
 *
 * @param <S> The class of every part. {@code ? extends SlottedEnergyStorage}
 *            can be used if the parts are of different types.
 */
public class CombinedSlottedEnergyStorage<S extends SlottedEnergyStorage> extends CombinedEnergyStorage<S>
        implements SlottedEnergyStorage {
    /**
     * Create a combined slotted storage that delegates to multiple parts.
     *
     * @param parts The list of parts backing this combined storage.
     */
    public CombinedSlottedEnergyStorage(List<S> parts) {
        super(parts);
    }

    /** Sums the slot counts of all wrapped storages. */
    @Override
    public int getSlotCount() {
        int count = 0;

        for (S part : parts) {
            count += part.getSlotCount();
        }

        return count;
    }

    /** Maps a combined slot index into the corresponding wrapped storage. */
    @Override
    public SingleSlotEnergyStorage getSlot(int slot) {
        int updatedSlot = slot;

        for (SlottedEnergyStorage part : parts) {
            if (updatedSlot < part.getSlotCount()) {
                return part.getSlot(updatedSlot);
            }

            updatedSlot -= part.getSlotCount();
        }

        throw new IndexOutOfBoundsException(
                "Slot " + slot + " is out of bounds. This storage has size " + getSlotCount());
    }

    /** Displays the wrapped parts for diagnostics. */
    @Override
    public String toString() {
        StringJoiner partNames = new StringJoiner(", ");

        for (S part : parts) {
            partNames.add(part.toString());
        }

        return "CombinedSlottedEnergyStorage[" + partNames + "]";
    }
}
