package com.tradeshift.reaktive.marshal.stream;

import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import akka.NotUsed;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.javadsl.Flow;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import javaslang.collection.Seq;

/**
 * Transforms a stream of T into a stream of events E, by applying a {@link WriteProtocol} to each element T.
 */
public class ProtocolWriter<T,E> extends GraphStage<FlowShape<T, E>> {
    
    public static <T,E> Flow<T,E,NotUsed> flow(WriteProtocol<E,T> protocol) {
        return Flow.fromGraph(new ProtocolWriter<>(protocol));
    }

    private final Inlet<T> in = Inlet.create("in");
    private final Outlet<E> out = Outlet.create("out");
    private final FlowShape<T, E> shape = FlowShape.of(in, out);
    
    private final WriteProtocol<E,T> protocol;
    
    private ProtocolWriter(WriteProtocol<E, T> protocol) {
        this.protocol = protocol;
    }

    @Override
    public FlowShape<T, E> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes attr) throws Exception {
        Writer<E, T> writer = protocol.writer();
        return new GraphStageLogic(shape) {{
            setHandler(out, new AbstractOutHandler() {
                @Override
                public void onPull() throws Exception {
                    pull(in);
                }
            });
            
            setHandler(in, new AbstractInHandler() {
                @Override
                public void onPush() throws Exception {
                    T t = grab(in);
                    Seq<E> events = writer.apply(t);
                    emitMultiple(out, events.iterator());
                }
                
                @Override
                public void onUpstreamFinish() throws Exception {
                    Seq<E> events = writer.reset();
                    emitMultiple(out, events.iterator(), () -> complete(out));
                }
            });
        }};
    }
}
