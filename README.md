ts-reaktive
===========

This repository shows how to build reactive applications in Java 8 using the Akka framework, on Akka Persistence,
with cassandra as backend, and exposing an event sourced stream using HTTP.

In addition, it contains several extensions for [kamon](http://kamon.io) that provide insight to a running, clustered akka application.

The repository consists of the following modules:
  - [ts-reaktive-actor](ts-reaktive-actor) contains the base Java classes with which a reactive application can be built
  - [ts-reaktive-testkit](ts-reaktive-testkit)  provides a test framework for testing akka routes with real HTTP
  - [ts-reaktive-kamon-akka](ts-reaktive-kamon-akka)  provides dead letter monitoring for an actor system
  - [ts-reaktive-kamon-akka-http](ts-reaktive-kamon-akka-http) provides http server monitoring for akka streams http
  - [ts-reaktive-kamon-akka-cluster](ts-reaktive-kamon-akka-cluster) provides cluster membership monitoring for akka cluster
  - [ts-reaktive-kamon-log4j](ts-reaktive-kamon-log4j) provides log4j error and warning monitoring

How to use from SBT
===================

If you use SBT, you can use this library by adding the following:

    resolvers += Resolver.bintrayRepo("jypma", "maven")
    
    libraryDependencies ++= {
      val version = "0.0.1"
      Seq(
        "com.tradeshift" % "ts-reaktive-actor" % version,
        "com.tradeshift" % "ts-reaktive-testkit" % version % "test",
        "com.tradeshift" %% "ts-reaktive-kamon-akka" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-akka-http" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-akka-cluster" % version,
        "com.tradeshift" %% "ts-reaktive-kamon-log4j" % version
      )
    }
    
How to use from Maven
=====================

If you use Maven, you can add the following to your `settings.xml` file or your `pom.xml`:

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
