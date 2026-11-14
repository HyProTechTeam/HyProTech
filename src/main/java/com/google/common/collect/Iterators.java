package com.google.common.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

/** Iterator adapters used by the bundled transfer implementation. */
public final class Iterators {
    private Iterators() {
    }

    /** Lazily yields only items accepted by the predicate; removal is unsupported. */
    public static <T> Iterator<T> filter(Iterator<T> unfiltered, Predicate<? super T> predicate) {
        return new Iterator<>() {
            private T nextItem;
            private boolean nextItemSet;

            /** Finds and caches the next element accepted by the predicate. */
            @Override
            public boolean hasNext() {
                if (nextItemSet) {
                    return true;
                }
                // Cache the next matching item so repeated hasNext calls do not advance again.
                while (unfiltered.hasNext()) {
                    T candidate = unfiltered.next();
                    if (predicate.test(candidate)) {
                        nextItem = candidate;
                        nextItemSet = true;
                        return true;
                    }
                }
                return false;
            }

            /** Returns the cached match and clears the cache for the next search. */
            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                T result = nextItem;
                nextItem = null;
                nextItemSet = false;
                return result;
            }

            /** Filtered iterator removal is not supported. */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** Lazily maps each source item and delegates removal to the source iterator. */
    public static <F, T> Iterator<T> transform(Iterator<F> fromIterator, Function<? super F, ? extends T> function) {
        return new Iterator<>() {
            /** Delegates availability to the source iterator. */
            @Override
            public boolean hasNext() {
                return fromIterator.hasNext();
            }

            /** Applies the mapping function to the next source element. */
            @Override
            public T next() {
                return function.apply(fromIterator.next());
            }

            /** Delegates removal to the source iterator. */
            @Override
            public void remove() {
                fromIterator.remove();
            }
        };
    }
}
