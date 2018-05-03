package com.tradeshift.reaktive.marshal.stream;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import akka.NotUsed;
import akka.parboiled2.util.Base64;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import akka.util.ByteStringBuilder;

/**
 * Provides flows for encoding and decoding base64 String and ByteString.
 * <pre>
 * Encoders:    
 *                    +----------------+
 *                    |                |
 *        ByteString ~~> encodeBytes  ~~> Base64 ByteString
 *                    |                |
 *                    +----------------+
 *                    +----------------+
 *                    |                |
 *        ByteString ~~> encodeString ~~> Base64 String
 *                    |                |
 *                    +----------------+
 * 
 * Decoders:                   
 *                    +----------------+
 *                    |                |
 * Base64 ByteString ~~> decodeBytes  ~~> decoded ByteString
 *                    |                |
 *                    +----------------+
 *                    +----------------+
 *                    |                |
 *     Base64 String ~~> decodeString ~~> decoded ByteString
 *                    |                |
 *                    +----------------+
 * </pre>
 */
public class Base64Flows {

    private static final boolean[] NOT_WHITESPACE = new boolean[256];
    static {
        Pattern whitespace = Pattern.compile("\\s");
        for (int b = 0; b < 256; b++) {
            NOT_WHITESPACE[b] = !whitespace.matcher(Character.toString((char) (b & 0xFF))).matches();
        }
    }
    
    /**
     * A graph stage that encodes binary input bytes into ByteString representation of Base64 data
     *
     * The implementation is according to RFC4648.
     */
    public static final Flow<ByteString, ByteString, NotUsed> encodeBytes =
        Flow.fromGraph(new ByteStringTransformStage() {
            @Override
            protected int getInputChunkSizeMultiple() {
                return 3;
            }

            @Override
            protected byte[] transform(ByteString input) {
                return Base64.rfc2045().encodeToByte(input.toArray(), false);
            }
        });

    /**
     * A graph stage that encodes input bytes, into base64-encoded string
     */
    public static final Flow<ByteString, String, NotUsed> encodeStrings =
        Flow.of(ByteString.class)
            .via(encodeBytes)
            .map(ByteString::utf8String);

    /**
     * A graph stage that decodes input bytes, which are assumed to be ASCII base64-encoded, into their binary representation.
     *
     * The implementation is according to RFC4648.
     */
    public static final Flow<ByteString, ByteString, NotUsed> decodeBytes =
        Flow.fromGraph(new ByteStringTransformStage() {
            @Override
            protected int getInputChunkSizeMultiple() {
                return 4;
            }

            @Override
            protected ByteString filterBeforeChunk(ByteString in) {
                return removeWhitespace(in);
            }

            @Override
            protected byte[] transform(ByteString input) {
                byte[] a = Base64.rfc2045().decode(input.toArray());
                if (a == null) {
                    throw new IllegalArgumentException("Base64 input is not a valid multiple of 4-char sequences.");
                }
                return a;
            }
        });

    /**
     * A graph stage that decodes input strings, which are assumed to be ASCII base64-encoded, into their binary representation.
     */
    public static final Flow<String, ByteString, NotUsed> decodeStrings =
        Flow.of(String.class)
            .map(s -> s.getBytes(StandardCharsets.ISO_8859_1))
            .map(ByteStringTransformStage::unsafeWrapByteArray)
            .via(decodeBytes);

     /**
     * Removes whitespace (or any other ignored characters) from an incoming ByteString buffer
     * and returns the result.
     */
    private static ByteString removeWhitespace(ByteString in) {
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
}
