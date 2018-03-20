package com.tradeshift.reaktive;

import java.util.concurrent.CompletableFuture;

import com.google.common.util.concurrent.ListenableFuture;

public class ListenableFutures {

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
