package com.tradeshift.reaktive.akka;

import java.util.concurrent.CompletionStage;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Various functions relating to akka streams
 */
public class AkkaStreams {
    
    /**
     * Materializes the given source and waits for it to successfully emit one element. It then completes the returned
     * CompletionStage with the full stream. It will wait indefinitely for that first element, so timeouts will have to be handled
     * separately on the stream, returned future, or both.
     * 
     * This is useful in cases where you want to "fail early" when handling a stream result. For example, you might want
     * to build an http response based on a stream, but want to set a different status code if the stream fails
     * to emit any element.
     */
    public static <T> CompletionStage<Source<T,NotUsed>> awaitOne(Source<T,?> source, Materializer materializer) {
        return source.prefixAndTail(1).map(pair -> {
            if (pair.first().isEmpty()) {
                return pair.second();
            } else {
                T head = pair.first().get(0);
                Source<T, NotUsed> tail = pair.second();
                return Source.single(head).concat(tail);                
            }
        }).runWith(Sink.head(), materializer);
    }

}
