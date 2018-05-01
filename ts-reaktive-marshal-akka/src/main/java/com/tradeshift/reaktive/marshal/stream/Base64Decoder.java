package com.tradeshift.reaktive.marshal.stream;

import akka.NotUsed;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

/**
 * @deprecated replaced by {@link Base64Flows}
 */
@Deprecated
public abstract class Base64Decoder {
    
    /**
     * A graph stage that decodes input bytes, which are assumed to be ASCII base64-encoded, into their binary representation.
     * The implementation is according to RFC4648.
     * @deprecated replaced by {@link Base64Flows#decodeBytes}
     */
    @Deprecated 
    public static final Flow<ByteString, ByteString, NotUsed> decodeBase64Bytes = Base64Flows.decodeBytes;

    /**
     * A graph stage that decodes input strings, which are assumed to be ASCII base64-encoded, into their binary representation.
     * @deprecated replaced by {@link Base64Flows#decodeStrings}
     */
    @Deprecated 
    public static final Flow<String, ByteString, NotUsed> decodeBase64Strings = Base64Flows.decodeStrings;
}
