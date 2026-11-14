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

import java.util.Iterator;

import org.jspecify.annotations.NonNull;

import com.doctorreborn.hytale.impl.energy.EnergyApiImpl;
import com.google.common.collect.Iterators;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/**
 * An object that can store energy.
 *
 * <p>
 * Most of the documentation that follows is quite technical.
 * For an easier introduction to the API, see the
 * <a href="https://fabricmc.net/wiki/tutorial:transfer-api">wiki page</a>.
 *
 * <ul>
 * <li>{@link #supportsInsertion} and {@link #supportsExtraction} can be used to
 * tell if insertion and extraction
 * functionality are possibly supported by this storage.</li>
 * <li>{@link #insert} and {@link #extract} can be used to insert or extract
 * energy from this storage.</li>
 * <li>{@link #iterator} can be used to inspect the contents of this
 * storage.</li>
 * <li>{@link #getVersion()} can be used to quickly check if a storage has
 * changed, without having to rescan its contents.</li>
 * </ul>
 *
 * <p>
 * Users that wish to implement this interface can use the helpers in the
 * {@code base} package:
 * <ul>
 * <li>{@link CombinedEnergyStorage} can be used to combine multiple instances,
 * for
 * example to combine multiple "slots" in one big storage.</li>
 * <li>{@link ExtractionOnlyEnergyStorage} and
 * {@link InsertionOnlyEnergyStorage} can
 * be
 * used when only extraction or insertion is needed.</li>
 * </ul>
 *
 * <p>
 * <b>Important note:</b> Unless otherwise specified, all transfer functions
 * take a non-negative maximum amount as parameters.
 * Implementations are encouraged to throw an exception if these preconditions
 * are violated.
 * {@link com.shailist.hytale.api.transfer.v1.storage.StoragePreconditions} can
 * be used for these checks.
 *
 * <p>
 * For transfer functions, the returned amount must be non-negative, and smaller
 * than the passed maximum amount.
 * Consumers of these functions are encourage to throw an exception if these
 * postconditions are violated.
 *
 * @see com.shailist.hytale.api.transfer.v1.transaction.Transaction
 */
public interface EnergyStorage extends Iterable<EnergyStorageView> {
    /**
     * Return an empty energy storage.
     *
     * @return An empty storage instance.
     */
    static EnergyStorage empty() {
        return EnergyApiImpl.EMPTY_STORAGE;
    }

    /**
     * Return whether insertion may be supported by this storage.
     *
     * <p>
     * Return {@code false} only when {@link #insert} will always return 0.
     * This can be used by transport code to decide whether to interact with this
     * storage.
     *
     * @return {@code true} when insertion may be supported or in doubt,
     *         {@code false} when insertion will always fail.
     */
    default boolean supportsInsertion() {
        return true;
    }

    /**
     * Try to insert up to some amount of energy into this storage.
     *
     * @param maxAmount   The maximum amount of energy to insert. May not be
     *                    negative.
     * @param transaction The transaction this operation is part of.
     * @return A non-negative integer not greater than maxAmount: the amount that
     *         was inserted.
     */
    long insert(long maxAmount, TransactionContext transaction);

    /**
     * Return whether extraction may be supported by this storage.
     *
     * <p>
     * Return {@code false} only when {@link #extract} will always return 0.
     * This can be used by transport code to decide whether to interact with this
     * storage.
     *
     * @return {@code true} when extraction may be supported or in doubt,
     *         {@code false} when extraction will always fail.
     */
    default boolean supportsExtraction() {
        return true;
    }

    /**
     * Try to extract up to some amount of energy from this storage.
     *
     * @param maxAmount   The maximum amount of energy to extract. May not be
     *                    negative.
     * @param transaction The transaction this operation is part of.
     * @return A non-negative integer not greater than maxAmount: the amount that
     *         was extracted.
     */
    long extract(long maxAmount, TransactionContext transaction);

    /**
     * Iterate through the contents of this storage.
     * Every visited {@link EnergyStorageView} represents a stored energy and an
     * amount.
     * The iterator doesn't guarantee that a single energy only occurs once during
     * an iteration.
     * Calling {@linkplain Iterator#remove remove} on the iterator is not allowed.
     *
     * <p>
     * {@link #insert} and {@link #extract} may be called safely during iteration.
     * Extractions should be visible to an open iterator, but insertions are not
     * required to.
     * In particular, inventories with a fixed amount of slots may wish to make
     * insertions visible to iterators,
     * but inventories with a dynamic or very large amount of slots should not do
     * that to ensure timely termination of
     * the iteration.
     *
     * <p>
     * If a modification is made to the storage during iteration, the iterator might
     * become invalid at the end of the outermost transaction.
     * In particular, if multiple storage views are extracted from, the entire
     * iteration should be wrapped in a transaction.
     *
     * @return An iterator over the contents of this storage. Calling remove on the
     *         iterator is not allowed.
     */
    @Override
    @NonNull
    Iterator<EnergyStorageView> iterator();

    /**
     * Same as {@link #iterator()}, but the iterator is guaranteed to skip over
     * empty views,
     * i.e. views that have a zero {@linkplain EnergyStorageView#getAmount()
     * amount}.
     *
     * <p>
     * This can provide a large performance benefit over {@link #iterator()} if the
     * caller is only interested in non-empty views,
     * for example because it is trying to extract energy from the storage.
     *
     * <p>
     * This function should only be overridden if the storage is able to provide an
     * optimized iterator over non-empty views,
     * for example because it is keeping an index of non-empty views.
     * Otherwise, the default implementation simply calls {@link #iterator()} and
     * filters out empty views.
     *
     * <p>
     * When implementing this function, note that the guarantees of
     * {@link #iterator()} still apply.
     * In particular, {@link #insert} and {@link #extract} may be called safely
     * during iteration.
     *
     * @return An iterator over the non-empty views of this storage. Calling remove
     *         on the iterator is not allowed.
     */
    default Iterator<EnergyStorageView> nonEmptyIterator() {
        return Iterators.filter(iterator(), view -> view != null && view.getAmount() > 0);
    }

    /**
     * Convenient helper to get an {@link Iterable} over the
     * {@linkplain #nonEmptyIterator() non-empty views} of this storage, for use in
     * for-each loops.
     *
     * <pre>{@code
     * for (EnergyStorageView view : storage.nonEmptyViews()) {
     *     // Do something with the view
     * }
     * }</pre>
     *
     * @return An iterable over the non-empty {@link EnergyStorageView} elements of
     *         this storage.
     */
    default Iterable<EnergyStorageView> nonEmptyViews() {
        return this::nonEmptyIterator;
    }

    /**
     * Return an integer representing the current version of this storage instance
     * to allow for fast change detection:
     * if the version hasn't changed since the last time, <b>and the storage
     * instance is the same</b>, the storage has the same contents.
     * This can be used to avoid re-scanning the contents of the storage, which
     * could be an expensive operation.
     * It may be used like that:
     * 
     * <pre>{@code
     * // Store storage and version:
     * EnergyStorage firstStorage = // ...
     * long firstVersion = firstStorage.getVersion();
     *
     * // Later, check if the secondStorage is the unchanged firstStorage:
     * EnergyStorage secondStorage = // ...
     * long secondVersion = secondStorage.getVersion();
     * // We must check firstStorage == secondStorage first, otherwise versions may not be compared.
     * if (firstStorage == secondStorage && firstVersion == secondVersion) {
     *     // storage contents are the same.
     * } else {
     *     // storage contents might have changed.
     * }
     * }</pre>
     *
     * <p>
     * The version <b>must</b> change if the state of the storage has changed,
     * generally after a direct modification, or at the end of a modifying
     * transaction.
     * The version may also change even if the state of the storage hasn't changed.
     *
     * <p>
     * It is not valid to call this during a transaction,
     * and implementations are encouraged to throw an exception if that happens.
     *
     * @return The version identifier; changes whenever the storage may have
     *         changed.
     */
    default long getVersion() {
        if (Transaction.isOpen()) {
            throw new IllegalStateException("getVersion() may not be called during a transaction.");
        }

        // The default conservatively returns a new version on every call.
        return EnergyApiImpl.version.getAndIncrement();
    }

    /**
     * Return a {@code Class} token for {@code EnergyStorage} to simplify API
     * lookups.
     *
     * @return A class object for {@code EnergyStorage} used for API lookups.
     */
    @SuppressWarnings("unchecked")
    static Class<EnergyStorage> asClass() {
        return (Class<EnergyStorage>) (Object) EnergyStorage.class;
    }
}
