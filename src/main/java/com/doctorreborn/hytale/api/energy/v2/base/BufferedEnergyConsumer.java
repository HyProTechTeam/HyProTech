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

import com.doctorreborn.hytale.api.energy.v2.Energy;
import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;

/** Energy consumer backed by a storage buffer and a transaction per operation. */
public class BufferedEnergyConsumer<T extends Storage<Energy>> extends BaseEnergyConsumer {
    private long lastConsumed = 0;
    private T storage;

    /** Stores the buffer and configured limit. */
    public BufferedEnergyConsumer(T storage, long energyConsumption) {
        super(energyConsumption);
        this.storage = storage;
    }

    /** Returns the amount from the previous operation. */
    @Override
    public long getLastConsumed() {
        return lastConsumed;
    }

    /** Opens an outer transaction, transfers the configured amount, and commits the result. */
    @Override
    public long consume() {
        try (Transaction transaction = Transaction.openOuter()) {
            lastConsumed = storage.extract(Energy.INSTANCE, getEnergyConsumption(), transaction);
            transaction.commit();
        }
        return lastConsumed;
    }
}
