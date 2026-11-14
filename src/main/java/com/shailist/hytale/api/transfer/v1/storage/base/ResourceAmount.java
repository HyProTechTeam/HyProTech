package com.shailist.hytale.api.transfer.v1.storage.base;

/** Pairs a resource with its quantity. */
public record ResourceAmount<T>(T resource, long amount) {
}
