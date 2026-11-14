package com.shailist.hytale.api.transfer.v1.transaction;

/** Identifies a transaction context for transfer operations. */
public interface TransactionContext {
    /** Returns the transaction active on the current thread, or null. */
    static TransactionContext current() {
        return Transaction.current();
    }
}
