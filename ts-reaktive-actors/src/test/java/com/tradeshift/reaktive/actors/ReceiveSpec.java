package com.tradeshift.reaktive.actors;

import static com.tradeshift.reaktive.actors.Receive.asReceive;
import static com.tradeshift.reaktive.actors.Receive.run;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import javaslang.Function1;
import javaslang.control.Option;
import scala.runtime.BoxedUnit;
import static javaslang.API.*;
import static javaslang.Predicates.*;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

@RunWith(CuppaRunner.class)
public class ReceiveSpec extends SharedActorSystemSpec {
    public static class ActorUsingJavaSlangReceive extends AbstractActor {
        private String lastString;
        
        {
            Function1<Object,Option<BoxedUnit>> r1 = msg -> { System.out.println("matching " + msg); return Match(msg).option(
                Case(instanceOf(String.class).and(is("?")), s -> run(() -> sender().tell(lastString, self()))),
                Case(instanceOf(String.class), s -> run(() -> lastString = s))
            ); };
            Function1<Object,Option<BoxedUnit>> r2 = msg -> Match(msg).option(
                Case(instanceOf(Integer.class), i -> run(() -> lastString = String.valueOf(i)))
            );
            
            receive(asReceive(r1).orElse(asReceive(r2)));
        }
    }

    {
        describe("Receive", () -> {
            it("should forward calls to custom receive functions as needed", () -> {
                ActorRef actor = system.actorOf(Props.create(ActorUsingJavaSlangReceive.class));
                JavaTestKit probe = new JavaTestKit(system);
                
                probe.send(actor, "hello");
                probe.send(actor, "?");
                probe.expectMsgEquals("hello");
                
                probe.send(actor, 123);
                probe.send(actor, "?");
                probe.expectMsgEquals("123");
            });
        });
    }
}
