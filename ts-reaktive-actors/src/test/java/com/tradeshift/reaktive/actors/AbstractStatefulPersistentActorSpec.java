package com.tradeshift.reaktive.actors;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import akka.testkit.javadsl.TestKit;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;

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
        public static abstract class Handler extends AbstractCommandHandler<String, MyEvent, MyState> {
            public Handler(MyState state, String cmd) {
                super(state, cmd);
            }

            @Override
            public Seq<MyEvent> getEventsToEmit() {
                return Vector.of(new MyEvent(cmd));
            }

            @Override
            public Object getReply(Seq<MyEvent> emittedEvents, long lastSequenceNr) {
                return Done.getInstance();
            }
        }
        
        public MyActor() {
            super(String.class, MyEvent.class);
        }
        
        @Override
        protected PartialFunction<String, Handler> applyCommand() {
            return new PFBuilder<String, Handler>()
                .match(String.class, c -> c.startsWith("1:"), c -> new Handler1(getState(), c))
                .match(String.class, c -> c.startsWith("2:"), c -> new Handler2(getState(), c))
                .build();
        }
        
        @Override
        protected PartialFunction<String, CompletionStage<? extends AbstractCommandHandler<String, MyEvent, MyState>>> applyCommandAsync() {
            return applyCommandAsyncBuilder()
                .match(String.class, c -> c.startsWith("a:"), c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return new Handler1(getState(), c);
                }))
                .match(String.class, c -> c.startsWith("b:"), c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return new Handler2(getState(), c);
                }))
                .build();
        }

        @Override
        protected MyState initialState() {
            return new MyState("");
        }
        
    }
    
    public static class Handler1 extends MyActor.Handler {
        public Handler1(MyState state, String cmd) {
            super(state, cmd);
        }
    }
    
    public static class Handler2 extends MyActor.Handler {
        public Handler2(MyState state, String cmd) {
            super(state, cmd);
        }
    }
    
    {
        describe("AbstractStatefulPersistentActor.applyCommandAsync", () -> {
            it("should process several async commands concurrently, until their handlers are resolved", () -> {
                ActorRef actor = system.actorOf(Props.create(MyActor.class));
                TestKit probe = new TestKit(system);
                // Send a synchronous message first, to ensure persistence has initialized successfully and doesn't affect timing.
                probe.send(actor, "1:sync");
                probe.expectMsgEquals(Done.getInstance());
                
                // Each async reply will take 2000ms, because of the Thread.sleep above.
                probe.send(actor, "a:test1");
                probe.send(actor, "b:test2");
                
                // After 2000ms, but before 4000ms since they operate concurrently, we should get 2 replies.
                probe.expectMsgEquals(Duration.create(3000, TimeUnit.MILLISECONDS), Done.getInstance());
                probe.expectMsgEquals(Duration.create(500, TimeUnit.MILLISECONDS), Done.getInstance());
            });
        });
    }
}
