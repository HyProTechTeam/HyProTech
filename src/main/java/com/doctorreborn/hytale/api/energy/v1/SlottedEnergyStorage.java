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

package com.doctorreborn.hytale.api.energy.v1;

import java.util.List;

import com.doctorreborn.hytale.api.energy.v1.base.SingleSlotEnergyStorage;
import com.doctorreborn.hytale.impl.energy.EnergyApiImpl;

/**
 * A {@link EnergyStorage} implementation made of indexed slots.
 *
 * <p>
 * Please note that some storages may not implement this interface.
 * It is up to the storage implementation to decide whether to implement this
 * interface or not.
 * Checking whether a storage is slotted can be done using {@code instanceof}.
 */
public interface SlottedEnergyStorage extends EnergyStorage {
    /**
     * Retrieve the number of slots in this storage.
     *
     * @return The total number of slots in this storage.
     */
    int getSlotCount();

    /**
     * Retrieve a specific slot of this storage.
     *
     * @param slot The slot index to retrieve.
     * @return The corresponding {@link SingleSlotEnergyStorage} for the provided
     *         index.
     * @throws IndexOutOfBoundsException If the slot index is out of bounds.
     */
    SingleSlotEnergyStorage getSlot(int slot);

    /**
     * Retrieve a list containing all the slots of this storage. <b>The list must
     * not be modified.</b>
     *
     * <p>
     * This function can be used to interface with code that requires a slot list,
     * for example {@link EnergyStorageUtil#insertStacking}.
     *
     * <p>
     * It is guaranteed that calling this function is fast.
     * The default implementation returns a view over the storage that delegates to
     * {@link #getSlotCount} and {@link #getSlot}.
     *
     * @return An unmodifiable view over all the slots in this storage.
     */
    default List<SingleSlotEnergyStorage> getSlots() {
        // The list delegates reads to this storage rather than copying its slots.
        return EnergyApiImpl.makeListView(this);
    }
}
