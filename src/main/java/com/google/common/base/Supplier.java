package com.google.common.base;

/** Functional interface for obtaining a value. */
@FunctionalInterface
public interface Supplier<T> {
    /** Returns a supplied value. */
    T get();
}
