package com.tradeshift.reaktive.actors;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class AbstractStatefulPersistentActorSpec extends SharedActorSystemSpec {
    public static class MyEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String msg;

        public MyEvent(String msg) {
            this.msg = msg;
        }
    }
    
    public static class MyState extends AbstractState<MyEvent, MyState> {
        private final String msg;
        
        @Override
        public MyState apply(MyEvent event) {
            return new MyState(msg + "," + event.msg);
        }

        public MyState(String msg) {
            this.msg = msg;
        }
    }

    public static class MyActor extends AbstractStatefulPersistentActor<String, MyEvent, MyState> {
        public static abstract class Handler implements SynchronousCommandHandler<String, MyEvent, MyState> {
            @Override
            public Results<MyEvent> handleSynchronously(MyState state, String cmd) {
                return new Results<MyEvent>() {
                    @Override
                    public Seq<MyEvent> getEventsToEmit() {
                        return Vector.of(new MyEvent(cmd));
                    }

                    @Override
                    public Object getReply(Seq<MyEvent> emittedEvents, long lastSequenceNr) {
                        return Done.getInstance();
                    }                    
                };
            }
        }
        
        public static abstract class AsyncHandler implements CommandHandler<String, MyEvent, MyState> {
            @Override
            public CompletionStage<Results<MyEvent>> handle(MyState state, String cmd) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }                    
                    return new Results<MyEvent>() {
                        @Override
                        public Seq<MyEvent> getEventsToEmit() {
                            return Vector.of(new MyEvent(cmd));
                        }

                        @Override
                        public Object getReply(Seq<MyEvent> emittedEvents, long lastSequenceNr) {
                            return Done.getInstance();
                        }                    
                    };
                });
            }
        }
        
        public static class FailingHandler implements CommandHandler<String, MyEvent, MyState> {
            public boolean canHandle(String cmd) {
                return cmd.equals("fail");
            }

            @Override
            public CompletionStage<Results<MyEvent>> handle(MyState state, String cmd) {
                return CompletableFuture.supplyAsync(() -> { throw new RuntimeException("Simulated failure"); });
            }
        }

        public MyActor() {
            super(String.class, MyEvent.class, new Handler1().orElse(new Handler2()).orElse(new HandlerA()).orElse(new HandlerB()).orElse(new FailingHandler()));
        }
        
        @Override
        protected MyState initialState() {
            return new MyState("");
        }
        
    }
    
    public static class Handler1 extends MyActor.Handler {
        @Override
        public boolean canHandle(String cmd) {
            return cmd.startsWith("1:");
        }
    }
    
    public static class Handler2 extends MyActor.Handler {
        @Override
        public boolean canHandle(String cmd) {
            return cmd.startsWith("2:");
        }
    }
    
    public static class HandlerA extends MyActor.AsyncHandler {
        @Override
        public boolean canHandle(String cmd) {
            return cmd.startsWith("a:");
        }
    }
    
    public static class HandlerB extends MyActor.AsyncHandler {
        @Override
        public boolean canHandle(String cmd) {
            return cmd.startsWith("b:");
        }
    }
    {
        describe("AbstractStatefulPersistentActor.applyCommandAsync", () -> {
            it("should process several async commands in sequence, waiting for their handlers to be resolved", () -> {
                ActorRef actor = system.actorOf(Props.create(MyActor.class));
                TestKit probe = new TestKit(system);
                // Send a synchronous message first, to ensure persistence has initialized successfully and doesn't affect timing.
                probe.send(actor, "1:sync");
                probe.expectMsgEquals(Done.getInstance());
                
                // Each async reply will take 2000ms, because of the Thread.sleep above.
                probe.send(actor, "a:test1");
                probe.send(actor, "b:test2");
                
                // Receive the first reply after about 2000 ms
                probe.expectMsgEquals(Duration.ofMillis(3000), Done.getInstance());
                // Even after 3000ms, the second reply shouldn't have come in (it's also sleeping its own 2 seconds)
                probe.expectNoMessage(Duration.ofMillis(100));
                // But eventually, the reply should make it.
                probe.expectMsgEquals(Done.getInstance());
            });

            it("should by default fail when one of its handlers fails asynchronously", () -> {
                ActorRef actor = system.actorOf(Props.create(MyActor.class));
                TestKit probe = new TestKit(system);
                probe.send(actor, "fail");
                probe.watch(actor);
                probe.expectTerminated(actor);
            });
        });
    }
}
