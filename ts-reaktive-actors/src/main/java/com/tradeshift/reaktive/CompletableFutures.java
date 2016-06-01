package com.tradeshift.reaktive;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Various functions relating to CompletableFuture
 */
public class CompletableFutures {
    /**
     * Returns a future that has failed with the given exception.
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable x) {
        CompletableFuture<T> promise = new CompletableFuture<T>();
        promise.completeExceptionally(x);
        return promise;
    }
    
    /**
     * Returns a future that completes when all the futures in the given collection have completed.
     */
    public static <T,U extends T> CompletableFuture<List<T>> sequence(Collection<CompletableFuture<U>> futures) {
        return sequence(futures.stream());
    }
    
    /**
     * Returns a future that completes when all the futures in the given stream have completed.
     */
    public static <T,U extends T> CompletableFuture<List<T>> sequence(Stream<CompletableFuture<U>> futures) {
        return CompletableFuture.supplyAsync(() -> futures.map(f -> f.join()).collect(Collectors.toList()));
    }
    
    /**
     * Turns a google ListenableFuture into a Java 7 CompletableFuture
     */
    public static <T> CompletableFuture<T> toJava(ListenableFuture<T> l) {
        CompletableFuture<T> f = new CompletableFuture<>();
        l.addListener(() -> {
            try {
                f.complete(l.get());
            } catch (Throwable x) {
                f.completeExceptionally(x);
            }
        }, Runnable::run);
        return f;
    }
}
