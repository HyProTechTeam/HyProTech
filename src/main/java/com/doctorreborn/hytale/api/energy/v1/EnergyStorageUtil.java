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
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.doctorreborn.hytale.api.energy.v1.base.SingleSlotEnergyStorage;
import com.shailist.hytale.api.transfer.v1.storage.StoragePreconditions;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/**
 * Helper functions to work with {@link EnergyStorage}s.
 *
 * <p>
 * Note that the functions that take a predicate iterate over the entire
 * inventory in the worst case.
 */
@SuppressWarnings("CaughtExceptionImmediatelyRethrown") // TODO: Remove when implementing Crash Reporting
public final class EnergyStorageUtil {
    private EnergyStorageUtil() {
    }

    /**
     * Move energy between two storages, matching the passed filter, and return
     * the amount that was successfully transferred.
     *
     * <p>
     * Here is a usage example:
     * 
     * <pre>{@code
     * // Source
     * EnergyStorage source;
     * // Target
     * EnergyStorage target;
     *
     * // Move up to one unit in total from source to target, outside of a
     * // transaction:
     * long amountMoved = EnergyStorageUtil.move(source, target, () -> true, 1, null);
     * // Move exactly one unit in total:
     * try (Transaction transaction = Transaction.openOuter()) {
     *     long energyMoved = EnergyStorageUtil.move(source, target, () -> true, 1, transaction);
     *     if (energyMoved == 1) {
     *         // Only commit if exactly one unit was moved (no less!).
     *         transaction.commit();
     *     }
     * }
     * }</pre>
     *
     * @param from        The source storage. May be null.
     * @param to          The target storage. May be null.
     * @param filter      The filter for transferred energy.
     *                    Only energy for which this filter returns {@code true}
     *                    will be transferred.
     * @param maxAmount   The maximum amount that will be transferred.
     * @param transaction The transaction this transfer is part of, or {@code null}
     *                    if a transaction should be opened just for this transfer.
     * @return The total amount of energy that was successfully transferred.
     * @throws IllegalStateException If no transaction is passed and a transaction
     *                               is already active on the current thread.
     */
    public static long move(@Nullable EnergyStorage from, @Nullable EnergyStorage to, BooleanSupplier filter,
            long maxAmount,
            @Nullable TransactionContext transaction) {
        Objects.requireNonNull(filter, "Filter may not be null");
        if (from == null || to == null)
            return 0;

        long totalMoved = 0;

        // Keep the whole iteration in one transaction so its successful transfers commit together.
        try (Transaction iterationTransaction = Transaction.openNested(transaction)) {
            for (EnergyStorageView view : from.nonEmptyViews()) {
                if (!filter.getAsBoolean())
                    continue;

                // Simulate extraction first; the simulation is rolled back on close.
                long maxExtracted = simulateExtract(view, maxAmount - totalMoved, iterationTransaction);

                try (Transaction transferTransaction = iterationTransaction.openNested()) {
                    // Insert only the amount that the source can supply.
                    long accepted = to.insert(maxExtracted, transferTransaction);

                    // Commit this nested transfer only when extraction matches insertion.
                    if (view.extract(accepted, transferTransaction) == accepted) {
                        totalMoved += accepted;
                        transferTransaction.commit();
                    }
                }

                if (maxAmount == totalMoved) {
                    // early return if nothing can be moved anymore
                    iterationTransaction.commit();
                    return totalMoved;
                }
            }

            iterationTransaction.commit();
        } catch (Exception e) {
            // TODO: Crash Reporting
            // CrashReport report = CrashReport.forThrowable(e, "Moving energy between
            // storages");
            // report.addCategory("Move details")
            // .setDetail("Input storage", from::toString)
            // .setDetail("Output storage", to::toString)
            // .setDetail("Filter", filter::toString)
            // .setDetail("Max amount", maxAmount)
            // .setDetail("Transaction", transaction);
            // throw new ReportedException(report);
            throw e;
        }

        return totalMoved;
    }

    /**
     * Convenient helper to simulate an insertion, i.e. get the result of
     * {@link EnergyStorage#insert} without modifying any state.
     * The passed transaction may be null if a new transaction should be opened for
     * the simulation.
     *
     * @param storage     The storage to query. May be null.
     * @param maxAmount   The maximum amount to simulate insertion for.
     * @param transaction The transaction to use for the simulation, or {@code null}
     *                    to open a nested transaction.
     * @return The amount that would be inserted without modifying state.
     * @see EnergyStorage#insert
     */
    public static long simulateInsert(EnergyStorage storage, long maxAmount,
            @Nullable TransactionContext transaction) {
        try (Transaction simulateTransaction = Transaction.openNested(transaction)) {
            return storage.insert(maxAmount, simulateTransaction);
        }
    }

    /**
     * Convenient helper to simulate an extraction, i.e. get the result of
     * {@link EnergyStorage#extract} without modifying any state.
     * The passed transaction may be null if a new transaction should be opened for
     * the simulation.
     *
     * @param storage     The storage to query. May be null.
     * @param maxAmount   The maximum amount to simulate extraction for.
     * @param transaction The transaction to use for the simulation, or {@code null}
     *                    to open a nested transaction.
     * @return The amount that would be extracted without modifying state.
     * @see EnergyStorage#extract
     */
    public static long simulateExtract(EnergyStorage storage, long maxAmount,
            @Nullable TransactionContext transaction) {
        try (Transaction simulateTransaction = Transaction.openNested(transaction)) {
            return storage.extract(maxAmount, simulateTransaction);
        }
    }

    /**
     * Convenient helper to simulate an extraction from a {@link EnergyStorageView},
     * i.e.
     * get the result of extract without modifying any state.
     * The passed transaction may be null if a new transaction should be opened for
     * the simulation.
     *
     * @param storageView The storage view to query. May not be null.
     * @param maxAmount   The maximum amount to simulate extraction for.
     * @param transaction The transaction to use for the simulation, or {@code null}
     *                    to open a nested transaction.
     * @return The amount that would be extracted without modifying state.
     * @see EnergyStorageView#extract
     */
    public static long simulateExtract(EnergyStorageView storageView, long maxAmount,
            @Nullable TransactionContext transaction) {
        try (Transaction simulateTransaction = Transaction.openNested(transaction)) {
            return storageView.extract(maxAmount, simulateTransaction);
        }
    }

    /**
     * Variant of
     * {@link #simulateExtract(EnergyStorage, long, TransactionContext)} that
     * handles objects implementing both
     * {@link EnergyStorage} and {@link EnergyStorageView} to disambiguate
     * overloads.
     *
     * @param storage     The object implementing both {@link EnergyStorage} and
     *                    {@link EnergyStorageView}.
     * @param maxAmount   The maximum amount to simulate extraction for.
     * @param transaction The transaction to use for the simulation, or {@code null}
     *                    to open a nested transaction.
     * @param <S>         The storage/view type.
     * @return The amount that would be extracted without modifying state.
     */
    // Object & is used to have a different erasure than the other overloads.
    public static <S extends Object & EnergyStorage & EnergyStorageView> long simulateExtract(S storage,
            long maxAmount,
            @Nullable TransactionContext transaction) {
        try (Transaction simulateTransaction = Transaction.openNested(transaction)) {
            return storage.extract(maxAmount, simulateTransaction);
        }
    }

    /**
     * Try to extract energy from a storage, up to a maximum amount.
     *
     * @param storage     The storage, may be null.
     * @param maxAmount   The maximum to extract.
     * @param transaction The transaction this operation is part of.
     * @return The strictly positive amount of energy that was
     *         extracted from the storage,
     *         or {@code null} if none could be found.
     */
    public static long extractAny(@Nullable EnergyStorage storage, long maxAmount,
            @NonNull TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);

        if (storage == null)
            return 0;

        try {
            for (EnergyStorageView view : storage.nonEmptyViews()) {
                long amount = view.extract(maxAmount, transaction);
                if (amount > 0)
                    return amount;
            }
        } catch (Exception e) {
            // TODO: Crash Reporting
            // CrashReport report = CrashReport.forThrowable(e, "Extracting energy from
            // storage");
            // report.addCategory("Extraction details")
            // .setDetail("Storage", storage::toString)
            // .setDetail("Max amount", maxAmount)
            // .setDetail("Transaction", transaction);
            // throw new ReportedException(report);
            throw e;
        }

        return 0;
    }

    /**
     * Try to insert up to some amount of energy into a list of storage slots,
     * trying to "stack" first,
     * i.e. prioritizing slots that already contain some energy.
     *
     * @param slots       The list of slots to try inserting into.
     * @param maxAmount   The maximum amount to insert.
     * @param transaction The transaction this operation is part of.
     * @return How much was inserted.
     * @see EnergyStorage#insert
     */
    public static long insertStacking(List<? extends SingleSlotEnergyStorage> slots, long maxAmount,
            @NonNull TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        long amount = 0;

        try {
            // Prefer already occupied slots before trying every slot for any remaining amount.
            for (SingleSlotEnergyStorage slot : slots) {
                if (slot.getAmount() > 0) {
                    amount += slot.insert(maxAmount - amount, transaction);
                    if (amount == maxAmount)
                        return amount;
                }
            }

            for (SingleSlotEnergyStorage slot : slots) {
                amount += slot.insert(maxAmount - amount, transaction);
                if (amount == maxAmount)
                    return amount;
            }
        } catch (Exception e) {
            // TODO: Crash Reporting
            // CrashReport report = CrashReport.forThrowable(e, "Inserting energy into
            // slots");
            // report.addCategory("Slotted insertion details")
            // .setDetail("Slots", () -> Objects.toString(slots, null))
            // .setDetail("Max amount", maxAmount)
            // .setDetail("Transaction", transaction);
            // throw new ReportedException(report);
            throw e;
        }

        return amount;
    }

    /**
     * Insert energy in a storage, attempting to stack it with existing
     * energy first if possible.
     *
     * @param storage     The storage, may be null.
     * @param maxAmount   The maximum amount of energy to insert. May not be
     *                    negative.
     * @param transaction The transaction this operation is part of.
     * @return A nonnegative integer not greater than maxAmount: the amount that was
     *         inserted.
     */
    public static long tryInsertStacking(@Nullable EnergyStorage storage, long maxAmount,
            @NonNull TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);

        try {
            if (storage instanceof SlottedEnergyStorage slottedStorage) {
                return insertStacking(slottedStorage.getSlots(), maxAmount, transaction);
            } else if (storage != null) {
                return storage.insert(maxAmount, transaction);
            } else {
                return 0;
            }
        } catch (Exception e) {
            // TODO: Crash Reporting
            // CrashReport report = CrashReport.forThrowable(e, "Inserting energy into a
            // storage");
            // report.addCategory("Insertion details")
            // .setDetail("Storage", () -> Objects.toString(storage, null))
            // .setDetail("Max amount", maxAmount)
            // .setDetail("Transaction", transaction);
            // throw new ReportedException(report);
            throw e;
        }
    }

    /**
     * Attempt to find energy stored in the passed storage that can be
     * extracted.
     *
     * @param storage     The storage to inspect, may be null.
     * @param transaction The current transaction, or {@code null} if a transaction
     *                    should be opened for this query.
     * @return {@code true} if energy stored in the storage can be extracted,
     *         {@code false} otherwise.
     */
    public static boolean findExtractableEnergy(@Nullable EnergyStorage storage,
            @Nullable TransactionContext transaction) {
        return findExtractableEnergy(storage, () -> true, transaction);
    }

    /**
     * Attempt to find energy stored in the passed storage that matches the
     * passed filter and can be extracted.
     *
     * @param storage     The storage to inspect, may be null.
     * @param filter      The filter. Only a resource for which this filter returns
     *                    {@code true} will be returned.
     * @param transaction The current transaction, or {@code null} if a transaction
     *                    should be opened for this query.
     * @return {@code true} if energy stored in the storage that matches the filter
     *         can be extracted, {@code false} otherwise.
     */
    public static boolean findExtractableEnergy(@Nullable EnergyStorage storage, BooleanSupplier filter,
            @Nullable TransactionContext transaction) {
        Objects.requireNonNull(filter, "Filter may not be null");
        if (storage == null)
            return false;

        try (Transaction nested = Transaction.openNested(transaction)) {
            for (EnergyStorageView view : storage.nonEmptyViews()) {
                if (filter.getAsBoolean() && view.extract(Long.MAX_VALUE, nested) > 0) {
                    // Will abort the extraction.
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Attempt to find energy stored in the passed storage that can be
     * extracted, and how much of it can be extracted.
     *
     * @param storage     The storage to inspect, may be null.
     * @param transaction The current transaction, or {@code null} if a transaction
     *                    should be opened for this query.
     * @return The strictly positive amount of energy that can be extracted,
     *         or {@code null} if none could be found.
     */
    public static long findExtractableAmount(@Nullable EnergyStorage storage,
            @Nullable TransactionContext transaction) {
        return findExtractableAmount(storage, () -> true, transaction);
    }

    /**
     * Attempt to find energy stored in the passed storage that can be extracted
     * and matches the filter, and how much of it can be extracted.
     *
     * @param storage     The storage to inspect, may be null.
     * @param filter      The filter. Only a resource for which this filter returns
     *                    {@code true} will be returned.
     * @param transaction The current transaction, or {@code null} if a transaction
     *                    should be opened for this query.
     * @return The strictly positive amount of energy that can be extracted,
     *         or {@code null} if none could be found.
     */
    public static long findExtractableAmount(@Nullable EnergyStorage storage, BooleanSupplier filter,
            @Nullable TransactionContext transaction) {
        boolean extractable = findExtractableEnergy(storage, filter, transaction);

        if (extractable) {
            assert storage != null; // Should be enforced by findExtractableResource

            long extractableAmount = simulateExtract(storage, Long.MAX_VALUE, transaction);

            if (extractableAmount > 0) {
                return extractableAmount;
            }
        }

        return 0;
    }
}
