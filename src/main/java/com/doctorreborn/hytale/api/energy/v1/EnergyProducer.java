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

package com.doctorreborn.hytale.api.energy.v1;

/** Contract for a producer that reports its configured and most recent production. */
public interface EnergyProducer {
    /**
     * @return The maximum amount of energy this producer can produce.
     */
    long getEnergyProduction();

    /**
     * @return The amount of energy produced in the last iteration.
     */
    long getLastProduced();

    /**
     * Try to produce some amount of energy.
     * 
     * @return The amount of energy produced.
     */
    long produce();
}
