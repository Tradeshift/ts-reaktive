package com.tradeshift.reaktive.materialize;

import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.stream.Attributes;
import akka.stream.BufferOverflowException;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.TimerGraphStageLogic;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import scala.concurrent.duration.FiniteDuration;

/**
 * A graph stage that groups elements together that, in groups, satisfy a condition.
 *
 * Emitted groups will always have at least one element in them.
 */
public class GroupWhile<T> extends GraphStage<FlowShape<T,Seq<T>>> {
    private static final Logger log = LoggerFactory.getLogger(GroupWhile.class);

    private final Inlet<T> in = Inlet.create("in");
    private final Outlet<Seq<T>> out = Outlet.create("out");
    private final FlowShape<T, Seq<T>> shape = FlowShape.of(in , out);

    private final BiFunction<T, T, Boolean> test;
    private final int maxGroupSize;
    private final FiniteDuration idleEmitTimeout;

    /**
     * Creates a new GroupWhile stage.
     * @param test The predicate which, as long as it holds, groups elements together in one group.
     * @param maxGroupSize Maximum size of emitted groups. If more elements are encountered that still match, the stage fails.
     * @param idleEmitTimeout Duration of no elements after which any current non-empty group is emitted anyways.
     */
    public static <T> GroupWhile<T> apply(BiFunction<T,T,Boolean> test, int maxGroupSize, FiniteDuration idleEmitTimeout) {
        return new GroupWhile<>(test, maxGroupSize, idleEmitTimeout);
    }

    public GroupWhile(BiFunction<T, T, Boolean> test, int maxGroupSize, FiniteDuration idleEmitTimeout) {
        this.test = test;
        this.maxGroupSize = maxGroupSize;
        this.idleEmitTimeout = idleEmitTimeout;
    }

    @Override
    public FlowShape<T, Seq<T>> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
        return new TimerGraphStageLogic(shape) {
            Seq<T> buffer = Vector.empty();

            {
                setHandler(in, new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        T t = grab(in);

                        if (buffer.isEmpty() || test.apply(buffer.get(buffer.size() - 1), t)) {
                            buffer = buffer.append(t);
                            scheduleOnce("idle", idleEmitTimeout);
                            pull(in);
                        } else {
                            emitBuffer();
                            scheduleOnce("idle", idleEmitTimeout);
                            buffer = buffer.append(t);
                        }
                        if (buffer.size() > maxGroupSize) {
                            failStage(new BufferOverflowException("Exceeded configured GroupWhile buffer size of " + maxGroupSize));
                            return;
                        }
                    }

                    @Override
                    public void onUpstreamFinish() {
                        emitBuffer();
                        completeStage();
                    }
                });

                setHandler(out, new AbstractOutHandler() {
                    @Override
                    public void onPull() {
                        if (!hasBeenPulled(in)) {
                            pull(in);
                        }
                    }
                });
            }

            private void emitBuffer() {
                if (!buffer.isEmpty() && isAvailable(out)) {
                    emit(out, buffer);
                    buffer = Vector.empty();
                    cancelTimer("idle");
                }
            }

            @Override
            public void onTimer(Object timerKey) {
                log.debug("Idle timeout reached with {} elements waiting", buffer.size());
                emitBuffer();
            }
        };
    }
}
