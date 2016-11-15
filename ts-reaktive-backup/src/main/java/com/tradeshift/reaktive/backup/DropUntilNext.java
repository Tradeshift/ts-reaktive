package com.tradeshift.reaktive.backup;

import java.util.function.Predicate;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * Stage that drops incoming elements, until it finds one that matches the predicate, but starts
 * output with the element BEFORE the one matching the predicate.
 * 
 * In other words, it drops elements until the NEXT element matches the predicate.
 */
public class DropUntilNext<T> extends GraphStage<FlowShape<T,T>> {
    /**
     * Returns a DropUntilNext.
     * @param includeLastIfNoMatch Whether to emit the last element if the stream is about to end without
     * anything having matched the predicate.
     */
    public static <T> GraphStage<FlowShape<T,T>> dropUntilNext(Predicate<T> predicate, boolean includeLastIfNoMatch) {
        return new DropUntilNext<>(predicate, includeLastIfNoMatch);
    }
    
    private final Inlet<T> in = Inlet.create("in");
    private final Outlet<T> out = Outlet.create("out");
    private final FlowShape<T,T> shape = FlowShape.of(in, out);
    
    private final Predicate<T> predicate;
    private final boolean includeLastIfNoMatch;
    
    public DropUntilNext(Predicate<T> predicate, boolean includeLastIfNoMatch) {
        this.predicate = predicate;
        this.includeLastIfNoMatch = includeLastIfNoMatch;
    }

    @Override
    public FlowShape<T, T> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes attr) throws Exception {
        return new GraphStageLogic(shape) {
            boolean open = false;
            T last = null;
            {
                setHandler(in, new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        T t = grab(in);
                        if (open) {
                            push(out, last);
                        } else if (predicate.test(t)) {
                            open = true;
                            if (last != null) {
                                push(out, last);
                            } else {
                                pull(in);
                            }
                        } else {
                            pull(in);
                        }
                        last = t;
                    }
                    
                    @Override
                    public void onUpstreamFinish() {
                        if ((open || includeLastIfNoMatch) && last != null) {
                            emit(out, last);
                        }
                        completeStage();
                    };
                });
                
                setHandler(out, new AbstractOutHandler() {
                    @Override
                    public void onPull() {
                        pull(in);
                    }
                });
            }
        };
    }

}
