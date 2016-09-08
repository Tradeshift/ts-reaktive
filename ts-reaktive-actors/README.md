About this module
=================

This module contains the following functionality:
- A convenient base class `AbstractStatefulPersistentActor` that serves as a good starting point for writing clustered, event-sourced applications
- An akka http route `EventRoute` which can expose an HTTP API for querying an akka persistence event journal
 

Using the UUID protobuf type
============================

This module defines a convenient protobuf UUID type, which serializes to a fixed 128-bit value. It also contains converters to and from `java.util.UUID`,
see [UUIDs.java](src/main/java/com/tradeshift/reaktive/protobuf/UUIDs.java).

If you are using SBT and its [protobuf plugin](https://github.com/sbt/sbt-protobuf) to build your project, just add the following dependency:

     libraryDependencies ++= Seq(
       "com.tradeshift" % "ts-reaktive-actors" % reaktiveVersion,
       "com.tradeshift" % "ts-reaktive-actors" % reaktiveVersion % PB.protobufConfig.name
     )
     
And then in any `.proto` file you can just say

    import Types.proto
    
    message MyMessage {  
        optional Types.UUID userId = 1;
    }
