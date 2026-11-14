package com.doctorreborn.hytale.api.energy.v2.base;

import com.doctorreborn.hytale.api.energy.v2.Energy;
import com.shailist.hytale.api.transfer.v1.storage.Storage;
import com.shailist.hytale.api.transfer.v1.transaction.Transaction;

/** Energy producer backed by a storage buffer and a transaction per operation. */
public class BufferedEnergyProducer<T extends Storage<Energy>> extends BaseEnergyProducer {
    private long lastProduced = 0;
    private T storage;

    /** Stores the buffer and configured limit. */
    public BufferedEnergyProducer(T storage, long energyProduction) {
        super(energyProduction);
        this.storage = storage;
    }

    /** Returns the amount from the previous operation. */
    @Override
    public long getLastProduced() {
        return lastProduced;
    }

    /** Opens an outer transaction, transfers the configured amount, and commits the result. */
    @Override
    public long produce() {
        try (Transaction transaction = Transaction.openOuter()) {
            lastProduced = storage.insert(Energy.INSTANCE, getEnergyProduction(), transaction);
            transaction.commit();
        }
        return lastProduced;
    }
}
