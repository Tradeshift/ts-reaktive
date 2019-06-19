package com.tradeshift.reaktive;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import io.vavr.Value;
import io.vavr.collection.Vector;

/**
 * Various functions relating to CompletableFuture
 */
public class CompletableFutures {
    /**
     * Returns a future that has failed with the given exception.
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable x) {
        CompletableFuture<T> promise = new CompletableFuture<>();
        promise.completeExceptionally(x);
        return promise;
    }
    
    /**
     * Returns a future that completes when all the futures in the given collection have completed.
     */
    public static <T,U extends T> CompletableFuture<List<T>> sequence(Collection<? extends CompletionStage<U>> futures) {
        return sequence(futures.stream());
    }
    
    /**
     * Returns a future that completes when all the futures in the given stream have completed.
     */
    public static <T,U extends T> CompletableFuture<List<T>> sequence(Stream<? extends CompletionStage<U>> futures) {
        return CompletableFutures.<T,U>sequence(futures.collect(Vector.collector())).thenApply(v -> v.toJavaList());
    }
    
    /**
     * Returns a future that completes when all the futures in the given collection have completed.
     */
    public static <T,U extends T> CompletableFuture<Vector<T>> sequence(Value<? extends CompletionStage<U>> completionStages) {
        CompletableFuture<Vector<T>> result = new CompletableFuture<>();
        Value<CompletableFuture<U>> futures = completionStages.map(f -> f.toCompletableFuture());

        // We don't join() the futures until allOf() has completed, since otherwise we'll be blocking
        // a thread on the common thread pool all the time we're waiting
        @SuppressWarnings({ "unchecked", "rawtypes" })
        CompletableFuture<?>[] array = futures.toJavaArray((Class) CompletableFuture.class);
        CompletableFuture.allOf(array).whenComplete((done, exception) -> {
            if (exception != null) {
                result.completeExceptionally(exception);
            } else {
                result.complete(Vector.narrow(futures.map(f -> f.join()).toVector()));
            }
        });
        
        return result;
    }
}
