package com.tradeshift.reaktive.marshal.stream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;

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
import akka.util.ByteString;
import akka.util.ByteStringBuilder;
import scala.Tuple2;

public abstract class Base64Decoder {
    /**
     * A graph stage that decodes input bytes, which are assumed to be ASCII base64-encoded, into their binary representation.
     * 
     * The implementation is according to RFC4648.
     */
    public static final Flow<ByteString,ByteString,NotUsed> decodeBase64Bytes = Flow.fromGraph(new GraphStage<FlowShape<ByteString,ByteString>>() {
        private final Inlet<ByteString> in = Inlet.create("in");
        private final Outlet<ByteString> out = Outlet.create("out");
        private final FlowShape<ByteString, ByteString> shape = FlowShape.of(in, out);

        @Override
        public FlowShape<ByteString, ByteString> shape() {
            return shape;
        }

        @Override
        public GraphStageLogic createLogic(Attributes attr) throws Exception {
            Decoder decoder = Base64.getDecoder();
            return new GraphStageLogic(shape) {
                private ByteString buf = ByteString.empty();
                {
                    setHandler(in, new AbstractInHandler() {
                        @Override
                        public void onPush() throws IOException {
                            buf = buf.concat(grab(in));
                            // only decode in 4-character groups
                            Tuple2<ByteString, ByteString> split = buf.splitAt(buf.size() - (buf.size() % 4));
                            if (split._1.isEmpty()) {
                                pull(in);
                            } else {
                                push(out, decode(split._1));
                            }
                            buf = split._2;
                        }

                        private ByteString decode(ByteString encoded) throws IOException {
                            ByteStringBuilder decoded = new ByteStringBuilder();
                            decoded.sizeHint((encoded.size() * 6) / 8);
                            InputStream stream = decoder.wrap(encoded.iterator().asInputStream());
                            for (int i = stream.read(); i != -1; i = stream.read()) {
                                decoded.putByte((byte) (i & 0xFF));
                            }
                            return decoded.result();
                        }
                        
                        public void onUpstreamFinish() throws IOException {
                            if (buf.isEmpty()) {
                                complete(out);
                            } else {
                                emit(out, decode(buf), () -> complete(out));
                            }
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
    });
    
    /**
     * A graph stage that decodes input strings, which are assumed to be ASCII base64-encoded, into their binary representation.
     */
    public static final Flow<String,ByteString,NotUsed> decodeBase64Strings =
        Flow.of(String.class)
            .map(s -> s.getBytes(StandardCharsets.ISO_8859_1))
            .map(Base64Decoder::unsafeWrapByteArray)
            .via(decodeBase64Bytes);
        
    /**
     * Wraps a byte array that is known to no longer be externally modified by any thread, ever, in an immutable ByteString.
     * 
     * This uses internal akka API, which is unsupported (but saves an expensive array copy operation).
     */
    private static ByteString unsafeWrapByteArray(byte[] bytes) {
        return new ByteStringBuilder()
            .putByteArrayUnsafe(bytes)
            .result();
    }
}
