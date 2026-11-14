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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Simple registry for looking up {@link EnergyStorage} instances at world
 * positions.
 *
 * <p>
 * Mods can register providers to expose their storages to other mods without
 * requiring direct dependencies.
 */
public final class EnergyStorageLookup {
    /** Provider for a storage at a world position, returning null when none is available. */
    @FunctionalInterface
    public interface Provider {
        @Nullable EnergyStorage find(@Nullable World world, int x, int y, int z);
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private EnergyStorageLookup() {
    }

    /** Registers a non-null provider at the end of the lookup order. */
    public static void register(Provider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    /** Removes a non-null provider from the registry. */
    public static void unregister(Provider provider) {
        if (provider != null) {
            PROVIDERS.remove(provider);
        }
    }

    /** Returns the first storage supplied by a provider, or null if none matches. */
    public static @Nullable EnergyStorage find(@Nullable World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        for (Provider provider : PROVIDERS) {
            EnergyStorage storage = provider.find(world, x, y, z);
            if (storage != null) {
                return storage;
            }
        }
        return null;
    }
}
