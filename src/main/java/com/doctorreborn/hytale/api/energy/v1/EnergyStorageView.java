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

import org.jspecify.annotations.NonNull;

import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/**
 * A view of stored energy in a {@link EnergyStorage}, for use with
 * {@link EnergyStorage#iterator}.
 */
public interface EnergyStorageView {
    /**
     * Try to extract energy from this view.
     *
     * @param maxAmount   The maximum amount to extract. May not be negative.
     * @param transaction The transaction this operation is part of.
     * @return The amount that was extracted.
     */
    long extract(long maxAmount, @NonNull TransactionContext transaction);

    /**
     * Return the amount of energy stored in this view.
     *
     * @return The amount of energy stored in this view.
     */
    long getAmount();

    /**
     * Return the total capacity of this view for the stored energy.
     *
     * @return The total amount of energy that could be stored in this view.
     */
    long getCapacity();

    /**
     * Return the underlying delegated view if this view wraps another view.
     * This can be used to check if two views refer to the same inventory "slot".
     * <b>Do not try to extract from the underlying view, or you risk bypassing some
     * checks.</b>
     *
     * <p>
     * It is expected that two storage views with the same underlying view
     * ({@code a.getUnderlyingView() == b.getUnderlyingView()})
     * share the same content, and mutating one should mutate the other. However,
     * one of them may allow extraction, and the other may not.
     *
     * @return The underlying {@link EnergyStorageView} or {@code this} if none.
     */
    default EnergyStorageView getUnderlyingView() {
        // An unwrapped view is its own underlying identity.
        return this;
    }
}
