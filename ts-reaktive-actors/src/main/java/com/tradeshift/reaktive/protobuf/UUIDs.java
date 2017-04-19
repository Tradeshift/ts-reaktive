package com.tradeshift.reaktive.protobuf;

import java.util.Arrays;
import java.util.UUID;

import javaslang.collection.SortedSet;
import javaslang.collection.TreeSet;

public class UUIDs {

    public static Types.UUID toProtobuf(UUID javaUUID) {
        return Types.UUID.newBuilder()
            .setLeastSignificantBits(javaUUID.getLeastSignificantBits())
            .setMostSignificantBits(javaUUID.getMostSignificantBits())
            .build();
    }
    
    public static UUID toJava(Types.UUID protobufUUID) {
        return new UUID(protobufUUID.getMostSignificantBits(), protobufUUID.getLeastSignificantBits());
    }

    public static SortedSet<UUID> splitIntoUUIDs(String commaSeparatedUuids) {
        return Arrays.stream(commaSeparatedUuids.split(","))
            .map(UUID::fromString)
            .collect(TreeSet.collector());
    }
}
