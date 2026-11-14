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

import com.shailist.hytale.api.transfer.v1.transaction.Transaction;

/** Energy producer backed by a storage buffer and a transaction per operation. */
public class SingleBufferEnergyProducer extends BaseEnergyProducer {
    private long lastProduced = 0;
    private SingleBufferEnergyStorage storage;

    /** Stores the buffer and configured limit. */
    public SingleBufferEnergyProducer(SingleBufferEnergyStorage storage, long energyProduction) {
        super(energyProduction);
        this.storage = storage;
    }

    /** Returns the amount from the previous operation. */
    @Override
    public long getLastProduced() {
        return lastProduced;
    }

    /** Opens an outer transaction, transfers the configured amount, and commits the result. */
    @Override
    public long produce() {
        try (Transaction transaction = Transaction.openOuter()) {
            lastProduced = storage.insert(getEnergyProduction(), transaction);
            transaction.commit();
        }
        return lastProduced;
    }
}
