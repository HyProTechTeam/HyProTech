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

import com.shailist.hytale.api.transfer.v1.storage.StoragePreconditions;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import com.shailist.hytale.api.transfer.v1.transaction.base.SnapshotParticipant;

/**
 * A storage that can store a single energy buffer.
 * Implementors should at least override {@link #getCapacity()},
 * and probably {@link #onFinalCommit()} as well for {@code setChanged()} and
 * similar calls.
 */
public class SingleBufferEnergyStorage extends SnapshotParticipant<Long> implements SingleSlotEnergyStorage {
    protected long amount = 0;
    protected long capacity;

    /** Creates an initially empty buffer with the supplied capacity. */
    public SingleBufferEnergyStorage(long capacity) {
        this.capacity = capacity;
    }

    /** Returns the current buffer contents. */
    @Override
    public long getAmount() {
        return amount;
    }

    /** Returns the buffer capacity. */
    @Override
    public long getCapacity() {
        return capacity;
    }

    /** Inserts up to the available capacity and records a rollback snapshot before mutation. */
    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);

        // Never insert more than the requested amount or the remaining capacity.
        long inserted = Math.min(maxAmount, getCapacity() - amount);

        if (inserted > 0) {
            updateSnapshots(transaction);
            amount += inserted;
            return inserted;
        }

        return 0;
    }

    /** Extracts up to the stored amount and records a rollback snapshot before mutation. */
    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);

        long extracted = Math.min(amount, maxAmount);

        if (extracted > 0) {
            this.updateSnapshots(transaction);
            amount -= extracted;
            return extracted;
        }

        return 0;
    }

    /** Captures the current amount for a transaction. */
    @Override
    protected Long createSnapshot() {
        return amount;
    }

    /** Restores the amount when a transaction rolls back. */
    @Override
    protected void readSnapshot(Long snapshot) {
        amount = snapshot;
    }

    /** Displays the current amount and capacity for diagnostics. */
    @Override
    public String toString() {
        return "SingleBufferEnergyStorage[" + amount + "/" + capacity + "]";
    }
}
