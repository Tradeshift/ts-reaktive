ts-reaktive
===========

This repository shows how to build reactive applications in Java 8 using the Akka framework, on Akka Persistence,
with cassandra as backend, and exposing an event sourced stream using HTTP.

In addition, it contains several extensions for [kamon](http://kamon.io) that provide insight to a running, clustered akka application.

The repository consists of the following modules:
  - [ts-reaktive-actors](ts-reaktive-actors) contains the base Java classes with which a reactive application can be built
  - [ts-reaktive-akka](ts-reaktive-akka) contains Akka additions that haven't been merged in to akka main yet
  - [ts-reaktive-cassandra](ts-reaktive-cassandra) contains classes that help using Cassandra in an async way
  - [ts-reaktive-java](ts-reaktive-cassandra) contains various utility classes for Java 8
  - [ts-reaktive-kamon-akka](ts-reaktive-kamon-akka)  provides dead letter monitoring for an actor system. Just have it on your classpath to enable.
  - [ts-reaktive-kamon-akka-http](ts-reaktive-kamon-akka-http) provides http server monitoring for akka streams http.  Just have it on your classpath to enable.
  - [ts-reaktive-kamon-akka-cluster](ts-reaktive-kamon-akka-cluster) provides cluster membership monitoring for akka clustering. Just have it on your classpath to enable.
  - [ts-reaktive-kamon-log4j](ts-reaktive-kamon-log4j) provides log4j error and warning monitoring. Just have it on your classpath to enable.
  - [ts-reaktive-marshal](ts-reaktive-marshal) provides a non-blocking marshalling DSL for XML and JSON
  - [ts-reaktive-marshal-akka](ts-reaktive-marshal-akka) uses the above marshalling inside akka reactive streams
  - [ts-reaktive-ssl](ts-reaktive-ssl) provides utility classes to read SSL keys and certs in PEM format
  - [ts-reaktive-testkit](ts-reaktive-testkit)  provides a test framework for testing akka routes with real HTTP
  - [ts-reaktive-testkit-assertj](ts-reaktive-testkit-assertj)  provides [AssertJ](http://joel-costigliola.github.io/assertj/)-style 
    assertions for Java 8 [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) 
    (with wait support) and Jackson's [JsonNode](https://fasterxml.github.io/jackson-databind/javadoc/2.2.0/com/fasterxml/jackson/databind/JsonNode.html).

How to use from SBT
===================

If you use SBT, you can use this library by adding the following:

    resolvers += Resolver.bintrayRepo("jypma", "maven")
    
    libraryDependencies ++= {
      val version = "0.0.15"
      Seq(
        "com.tradeshift" % "ts-reaktive-actors" % version,
        "com.tradeshift" %% "ts-reaktive-akka" % version,
        "com.tradeshift" % "ts-reaktive-cassandra" % version,
        "com.tradeshift" % "ts-reaktive-marshal" % version,
        "com.tradeshift" % "ts-reaktive-marshal-akka" % version,
        "com.tradeshift" % "ts-reaktive-replication" % version,
        "com.tradeshift" % "ts-reaktive-ssl" % version,
        "com.tradeshift" % "ts-reaktive-testkit" % version % "test",
        "com.tradeshift" % "ts-reaktive-testkit-assertj" % version % "test",
        "com.tradeshift" %% "ts-reaktive-kamon-akka" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-akka-http" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-akka-cluster" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-log4j" % version
      )
    }
    
How to use from Maven
=====================

If you use Maven, you can add the following to your `settings.xml` file or your `pom.xml`, and then add the individual dependencies
shown above:

    <repositories>
      <repository>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
        <id>bintray-jypma-maven</id>
        <name>bintray</name>
        <url>http://dl.bintray.com/jypma/maven</url>
      </repository>
    </repositories>
    <pluginRepositories>
      <pluginRepository>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
        <id>bintray-jypma-maven</id>
        <name>bintray-plugins</name>
        <url>http://dl.bintray.com/jypma/maven</url>
      </pluginRepository>
    </pluginRepositories>
    
Other build systems
===================

Visit [bintray](https://bintray.com/jypma/maven) and click "Set me up".
