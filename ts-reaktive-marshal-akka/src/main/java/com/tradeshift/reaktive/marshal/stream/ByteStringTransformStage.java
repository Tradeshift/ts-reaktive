package com.tradeshift.reaktive.marshal.stream;

import java.util.regex.Pattern;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import scala.Tuple2;


/**
 * Base class to provide graph stages that transform ByteString streams. 
 * Overriding {@link #getInputChunkSizeMultiple()} guaranties chunking of the input to sizes that are multiple of a desired number before 
 * feeding it to {@link #transform(ByteString)} method 
 */
public abstract class ByteStringTransformStage extends GraphStage<FlowShape<ByteString, ByteString>> {
    private static final boolean[] NOT_WHITESPACE = new boolean[256];
    static {
        Pattern whitespace = Pattern.compile("\\s");
        for (int b = 0; b < 256; b++) {
            NOT_WHITESPACE[b] = !whitespace.matcher(Character.toString((char) (b & 0xFF))).matches();
        }
    }
    
    private final Inlet<ByteString> in = Inlet.create("in");
    private final Outlet<ByteString> out = Outlet.create("out");
    private final FlowShape<ByteString, ByteString> shape = FlowShape.of(in, out);

    @Override
    public FlowShape<ByteString, ByteString> shape() {
        return shape;
    }

    /**
     * Returns the transformed input (which is guaranteed to be a multiple of {@link #getInputChunkSizeMultiple()})
     *
     * @throws IllegalArgumentException if the input is invalid, which will fail the stage.
     */
    protected abstract byte[] transform(ByteString input);

    /**
     * @return the number that transformation should guaranty the input be multiple of.
     * Returning 1 disables input chunking.
     */
    protected abstract int getInputChunkSizeMultiple();

    /**
     * Removes whitespace (or any other ignored characters) from an incoming bytestring buffer
     * and returns the result.
     */
    protected ByteString removeWhitespace(ByteString in) {
        ByteStringBuilder b = new ByteStringBuilder();
        b.sizeHint(in.size());
        for (int i = 0; i < in.size(); i++) {
            byte ch = in.apply(i);
            if (NOT_WHITESPACE[ch]) {
                b.putByte(ch);
            }
        }
        return b.result();
    }
    
    @Override
    public GraphStageLogic createLogic(Attributes attr) {
        return new GraphStageLogic(shape) {
            private ByteString buf = ByteString.empty();

            {
                setHandler(in, new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        buf = buf.concat(removeWhitespace(grab(in)));
                        Tuple2<ByteString, ByteString> split = buf.splitAt(buf.size() - (buf.size() % getInputChunkSizeMultiple()));
                        if (split._1.isEmpty()) {
                            pull(in);
                        } else {
                            try {
                                push(out, unsafeWrapByteArray(transform(split._1)));
                            } catch (IllegalArgumentException x) {
                                failStage(x);
                            }
                        }
                        buf = split._2;
                    }

                    public void onUpstreamFinish() {
                        if (buf.isEmpty()) {
                            complete(out);
                        } else {
                            try {
                                emit(out, unsafeWrapByteArray(transform(buf)), () -> complete(out));
                            } catch (IllegalArgumentException x) {
                                failStage(x);
                            }
                        }
                    }
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
    
    /**
     * Wraps a byte array that is known to no longer be externally modified by any thread, ever, in an immutable ByteString.
     *
     * This uses internal akka API, which is unsupported (but saves an array copy operation).
     */
    protected static ByteString unsafeWrapByteArray(byte[] bytes) {
        return new ByteStringBuilder()
            .putByteArrayUnsafe(bytes)
            .result();
    }
}
