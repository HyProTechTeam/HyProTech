package com.doctorreborn.hytale.api.energy.v2;

/**
 * Preconditions that can be used when working with energy.
 *
 * <p>
 * In particular, {@link #notNegative} can be
 * used by implementations of
 * {@link Consumer#consume} and {@link Producer#produce} to fail-fast if the
 * arguments are invalid.
 */
public final class EnergyPreconditions {
    /** Throws an IllegalArgumentException when the supplied amount is negative. */
    public static void notNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
    }
}
