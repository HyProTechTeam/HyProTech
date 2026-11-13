package HyProTechTeam.energy;

import com.doctorreborn.hytale.api.energy.v1.EnergyStorage;
import com.doctorreborn.hytale.api.energy.v1.EnergyStorageView;
import com.doctorreborn.hytale.api.energy.v1.EnergyTransferer;
import com.shailist.hytale.api.transfer.v1.storage.StoragePreconditions;
import com.shailist.hytale.api.transfer.v1.transaction.TransactionContext;
import com.shailist.hytale.api.transfer.v1.transaction.base.SnapshotParticipant;
import java.util.Collections;
import java.util.Iterator;

/** Adapts a node's stored energy to the transactional energy-storage API. */
public final class EnergyNodeStorage extends SnapshotParticipant<Integer>
        implements EnergyStorage, EnergyStorageView, EnergyTransferer {
    private final EnergyNodeComponent node;
    private Runnable onChanged;

    public EnergyNodeStorage(EnergyNodeComponent node, Runnable onChanged) {
        this.node = node;
        this.onChanged = onChanged;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public boolean supportsInsertion() {
        return node.getNodeType() != EnergyNodeComponent.NodeType.SOLAR;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        if (!supportsInsertion()) {
            return 0;
        }

        int capacity = node.getCapacity();
        int energy = node.getEnergy();
        // Limit insertion to the node's remaining capacity.
        long insertable = Math.min(maxAmount, (long) capacity - energy);
        if (insertable <= 0) {
            return 0;
        }

        // Snapshot before mutation so an aborted transaction can restore the old amount.
        updateSnapshots(transaction);
        node.setEnergy(energy + (int) insertable);
        return insertable;
    }

    @Override
    public boolean supportsExtraction() {
        return node.getNodeType() != EnergyNodeComponent.NodeType.FURNACE;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        if (!supportsExtraction()) {
            return 0;
        }

        int energy = node.getEnergy();
        // A transaction cannot extract more energy than the node currently contains.
        long extracted = Math.min(maxAmount, energy);
        if (extracted <= 0) {
            return 0;
        }

        // Record the pre-extraction amount for transaction rollback.
        updateSnapshots(transaction);
        node.setEnergy(energy - (int) extracted);
        return extracted;
    }

    @Override
    public Iterator<EnergyStorageView> iterator() {
        return Collections.singleton((EnergyStorageView) this).iterator();
    }

    @Override
    public long getAmount() {
        return node.getEnergy();
    }

    @Override
    public long getCapacity() {
        return node.getCapacity();
    }

    @Override
    protected Integer createSnapshot() {
        return node.getEnergy();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        node.setEnergy(snapshot == null ? 0 : snapshot);
    }

    @Override
    protected void onFinalCommit() {
        // Notify the owner only after the outermost transaction commits.
        if (onChanged != null) {
            onChanged.run();
        }
    }

    @Override
    public long getTransferRate() {
        return Math.max(0, node.getMaxTransfer());
    }

    @Override
    public String toString() {
        return "EnergyNodeStorage[" + node.getEnergy() + "/" + node.getCapacity() + "]";
    }
}
