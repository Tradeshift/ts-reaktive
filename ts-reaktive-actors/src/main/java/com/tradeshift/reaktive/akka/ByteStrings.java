package com.tradeshift.reaktive.akka;

/**
 * Various functions relating to byte strings
 */
public class ByteStrings {

    /**
     * Converts an akka ByteString into google protobuf ByteString
     */
    public static com.google.protobuf.ByteString toProtobuf(akka.util.ByteString data) {
        //TODO do this by writing a wrapper around akka.util.ByteString, so no copying is needed
        return com.google.protobuf.ByteString.copyFrom(data.asByteBuffer());
    }

}
