package com.shailist.hytale.impl.transfer;

import java.util.Collections;
import java.util.Iterator;

/** Internal helpers used by transfer API implementations. */
public final class TransferApiImpl {
    private TransferApiImpl() {
    }

    /** Returns an iterator over exactly one supplied value. */
    public static <T> Iterator<T> singletonIterator(T value) {
        return Collections.singleton(value).iterator();
    }
}
