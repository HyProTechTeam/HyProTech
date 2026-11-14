package com.shailist.hytale.api.transfer.v1.transaction.base;

import com.shailist.hytale.api.transfer.v1.transaction.Transaction;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;

/** Base for mutable state that can be restored when a transaction is aborted. */
public abstract class SnapshotParticipant<T> {
    /** Captures the participant's current mutable state. */
    protected abstract T createSnapshot();

    /** Restores a previously captured state. */
    protected abstract void readSnapshot(T snapshot);

    /** Optional callback after the outer transaction commits. */
    protected void onFinalCommit() {
    }

    /** Optional callback after the outer transaction aborts. */
    protected void onFinalAbort() {
    }

    /** Exposes snapshot creation to the transaction implementation. */
    public final Object createSnapshotInternal() {
        return createSnapshot();
    }

    /** Restores the snapshot captured by this participant. */
    @SuppressWarnings("unchecked")
    public final void readSnapshotInternal(Object snapshot) {
        readSnapshot((T) snapshot);
    }

    /** Dispatches the transaction's final-commit notification. */
    public final void onFinalCommitInternal() {
        onFinalCommit();
    }

    /** Dispatches the transaction's final-abort notification. */
    public final void onFinalAbortInternal() {
        onFinalAbort();
    }

    /** Registers the current state before a transaction mutates this participant. */
    protected final void updateSnapshots(TransactionContext transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("TransactionContext may not be null.");
        }
        if (!(transaction instanceof Transaction tx)) {
            throw new IllegalArgumentException("Unsupported TransactionContext implementation.");
        }
        tx.trackParticipant(this);
    }
}
