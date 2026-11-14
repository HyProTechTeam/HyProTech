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
 */

package com.doctorreborn.hytale.api.energy.v2.base;

import com.doctorreborn.hytale.api.energy.v2.EnergyTransferer;
import com.doctorreborn.hytale.api.energy.v2.EnergyPreconditions;

/** Stores the configured transfer rate. */
public class BaseEnergyTransferer implements EnergyTransferer {
    private long transferRate;

    /** Validates and stores the transfer rate. */
    public BaseEnergyTransferer(long transferRate) {
        EnergyPreconditions.notNegative(transferRate);

        this.transferRate = transferRate;
    }

    /** Returns the configured transfer rate. */
    @Override
    public long getTransferRate() {
        return transferRate;
    }
}
