package com.tradeshift.reaktive.protobuf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Message;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.util.ByteString;
import scala.Tuple2;

/**
 * Parses an incoming byte string of "delimited" protobuf messages such, that each ByteString makes
 * up a complete message.
 *
 * The stream is expected to consist of [Varint length] [length bytes] [Varint length] [length bytes] etc.
 * This is the format that multiple calls to {@link Message#writeDelimitedTo(java.io.OutputStream)} would produce.
 *
 * The length delimiters themselves are not emitted downstream, i.e. each downstream ByteString can be decoded
 * using protobuf's "parse" function, not "parseDelimited".
 */
public class DelimitedProtobufFraming extends GraphStage<FlowShape<ByteString,ByteString>> {
    public static final DelimitedProtobufFraming instance = new DelimitedProtobufFraming();

    private static final Logger log = LoggerFactory.getLogger(DelimitedProtobufFraming.class);

    private final Inlet<ByteString> in = Inlet.create("in");
    private final Outlet<ByteString> out = Outlet.create("out");
    private final FlowShape<ByteString, ByteString> shape = FlowShape.of(in, out);

    private DelimitedProtobufFraming() {}

    @Override
    public FlowShape<ByteString, ByteString> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes attr) throws Exception {
        return new GraphStageLogic(shape) {
            ByteString buf = ByteString.empty();
            {
                setHandler(in, new AbstractInHandler() {
                    @Override
                    public void onPush() {
                        ByteString b = grab(in);
                        buf = buf.concat(b);
                        deliverBuf();
                    }

                    @Override
                    public void onUpstreamFinish() {
                        if (buf.size() > 0) {
                            deliverBuf();
                        }
                        completeStage();
                    };

                    private void deliverBuf() {
                        log.debug("Buf now {}", buf);
                        try {
                            List<ByteString> deframed = new ArrayList<>();
                            while (buf.size() > 0) {
                                CodedInputStream i = CodedInputStream.newInstance(buf.iterator().asInputStream());
                                long contentLength = i.readUInt64();
                                int delimiterLength = i.getTotalBytesRead();
                                log.debug("Got content {}, delimiter {}", contentLength, delimiterLength);
                                if (buf.size() >= delimiterLength + contentLength) {
                                    buf = buf.drop(delimiterLength); // cast OK, delimiter will be a few bytes at most
                                    if (contentLength > Integer.MAX_VALUE) {
                                        throw new IOException("Only support protobuf messages up to 2G each. And that's a lot.");
                                    }
                                    Tuple2<ByteString, ByteString> t = buf.splitAt((int)contentLength);
                                    deframed.add(t._1);
                                    buf = t._2;
                                } else {
                                    // not received enough bytes yet
                                    break;
                                }
                            }

                            if (deframed.isEmpty()) {
                                if (!isClosed(in)) {
                                    pull(in);
                                }
                            } else {
                                emitMultiple(out, deframed.iterator());
                            }
                        } catch (IOException x) {
                            log.debug("Protobuf unhappy at length {}", buf.size());
                            if (buf.size() < 10) {
                                // this is possibly because there are not enough bytes to read a full varint. Pull to be safe.
                                pull(in);
                            } else {
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

}
