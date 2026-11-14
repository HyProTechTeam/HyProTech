package com.shailist.hytale.api.transfer.v1.storage;

/** Validation shared by transfer storage implementations. */
public final class StoragePreconditions {
    private StoragePreconditions() {
    }

    /** Rejects negative transfer amounts with an IllegalArgumentException. */
    public static void notNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative.");
        }
    }
}
