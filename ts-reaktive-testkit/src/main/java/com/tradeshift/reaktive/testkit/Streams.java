package com.tradeshift.reaktive.testkit;

import java.util.concurrent.CompletionStage;

import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

/**
 * Various akka stream operators
 */
public class Streams {
    /**
     * Returns a {@link Sink} that folds all emitted elements into a {@link Seq}.
     */
    public static <T> Sink<T, CompletionStage<Seq<T>>> toSeq() {
        return Sink.<Seq<T>,T>fold(Vector.empty(), (seq, elmt) -> seq.append(elmt));
    }
    
    /**
     * Returns a {@link Sink} that appends all emitted ByteString elements to each other.
     */
    public static Sink<ByteString, CompletionStage<ByteString>> toByteString() {
        return Sink.fold(ByteString.emptyByteString(), (bs, elmt) -> bs.concat(elmt));
    }
}
