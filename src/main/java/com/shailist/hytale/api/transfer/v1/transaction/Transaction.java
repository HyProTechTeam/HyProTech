package com.shailist.hytale.api.transfer.v1.transaction;

import com.shailist.hytale.api.transfer.v1.transaction.base.SnapshotParticipant;
import java.util.IdentityHashMap;
import java.util.Map;

/** Tracks participant snapshots for nested, thread-local commit and rollback. */
public final class Transaction implements TransactionContext, AutoCloseable {
    private static final ThreadLocal<Transaction> CURRENT = new ThreadLocal<>();

    private final Transaction parent;
    private final IdentityHashMap<SnapshotParticipant<?>, Object> snapshots = new IdentityHashMap<>();
    private boolean committed;
    private boolean closed;

    private Transaction(Transaction parent) {
        this.parent = parent;
    }

    /** Starts the only outer transaction allowed on the current thread. */
    public static Transaction openOuter() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("A transaction is already open on this thread.");
        }
        Transaction transaction = new Transaction(null);
        CURRENT.set(transaction);
        return transaction;
    }

    /** Starts a child of the current transaction, or an outer transaction for a null context. */
    public static Transaction openNested(TransactionContext context) {
        if (context == null) {
            if (CURRENT.get() != null) {
                throw new IllegalStateException("A transaction is already open on this thread.");
            }
            return openOuter();
        }
        if (!(context instanceof Transaction parent)) {
            throw new IllegalArgumentException("Unsupported TransactionContext implementation.");
        }
        if (CURRENT.get() != parent) {
            throw new IllegalStateException("Transaction context is not current for this thread.");
        }
        Transaction transaction = new Transaction(parent);
        CURRENT.set(transaction);
        return transaction;
    }

    /** Opens a child transaction of this current scope. */
    public Transaction openNested() {
        return openNested(this);
    }

    /** Reports whether the current thread has an open transaction. */
    public static boolean isOpen() {
        return CURRENT.get() != null;
    }

    /** Returns the current thread's transaction, or null. */
    public static Transaction current() {
        return CURRENT.get();
    }

    /** Marks this scope committed and passes child snapshots to its parent. */
    public void commit() {
        ensureOpen();
        committed = true;
        if (parent != null) {
            parent.absorbSnapshots(this);
        }
    }

    /** Saves the participant's state once, before its first change in this scope. */
    public void trackParticipant(SnapshotParticipant<?> participant) {
        ensureOpen();
        if (!snapshots.containsKey(participant)) {
            snapshots.put(participant, participant.createSnapshotInternal());
        }
    }

    /** Keeps the earliest parent snapshot so an outer rollback undoes committed children too. */
    private void absorbSnapshots(Transaction child) {
        for (Map.Entry<SnapshotParticipant<?>, Object> entry : child.snapshots.entrySet()) {
            snapshots.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /** Restores uncommitted state; only the outer scope sends final notifications. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (CURRENT.get() == this) {
            CURRENT.set(parent);
        }

        if (!committed) {
            rollback();
            if (parent == null) {
                notifyFinalAbort();
            }
            return;
        }

        if (parent == null) {
            notifyFinalCommit();
        }
    }

    /** Restores each participant to its state when first touched in this scope. */
    private void rollback() {
        for (Map.Entry<SnapshotParticipant<?>, Object> entry : snapshots.entrySet()) {
            SnapshotParticipant<?> participant = entry.getKey();
            participant.readSnapshotInternal(entry.getValue());
        }
    }

    /** Notifies tracked participants after the outer transaction commits. */
    private void notifyFinalCommit() {
        for (SnapshotParticipant<?> participant : snapshots.keySet()) {
            participant.onFinalCommitInternal();
        }
    }

    /** Notifies tracked participants after the outer transaction aborts. */
    private void notifyFinalAbort() {
        for (SnapshotParticipant<?> participant : snapshots.keySet()) {
            participant.onFinalAbortInternal();
        }
    }

    /** Rejects operations on a transaction that has already closed. */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Transaction is already closed.");
        }
    }
}
