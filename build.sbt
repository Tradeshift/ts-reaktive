
scalaVersion := "2.12.3" // just for the root

val akkaVersion = "2.5.4"
val akkaHttpVersion = "10.0.10"
val akkaInMemory = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1"
val kamonVersion = "0.6.6"
val assertJ = "org.assertj" % "assertj-core" % "3.2.0"

lazy val projectSettings = Seq(
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  organization := "com.tradeshift",
  version := "0.0.31-SNAPSHOT",
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  publishMavenStyle := true,
  javacOptions ++= Seq("-source", "1.8"),
  javacOptions in (Compile, Keys.compile) ++= Seq("-target", "1.8", "-Xlint", "-Xlint:-processing", "-Xlint:-serial", "-Werror"),
  javacOptions in doc ++= Seq("-Xdoclint:none"),
  EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
  EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.ManagedClasses,
  EclipseKeys.withSource := true,
  fork := true,
  resolvers ++= Seq(
    Resolver.bintrayRepo("readytalk", "maven"),
    Resolver.jcenterRepo),
  dependencyOverrides += "com.google.protobuf" % "protobuf-java" % "2.6.1",
  protobufRunProtoc in ProtobufConfig := { args =>
    com.github.os72.protocjar.Protoc.runProtoc("-v261" +: args.toArray)
  },
  libraryDependencies ++= Seq(
    "io.vavr" % "vavr" % "0.9.0",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.slf4j" % "slf4j-log4j12" % "1.7.12" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
    "junit" % "junit" % "4.11" % "test",
    assertJ % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test",
    "info.solidsoft.mockito" % "mockito-java8" % "0.3.0" % "test",
    akkaInMemory % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.3.1" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.3.1" % "test",
    "org.apache.cassandra" % "cassandra-all" % "3.9" % "test" exclude("ch.qos.logback", "logback-classic"),
    "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.55" % "test",
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test",
    "org.xmlunit" % "xmlunit-core" % "2.5.0" % "test",
    "org.xmlunit" % "xmlunit-matchers" % "2.5.0" % "test"
  )
)

lazy val commonSettings = projectSettings ++ Seq(
  libraryDependencies ++= {
    Seq(
      "com.typesafe" % "config" % "1.3.0",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
      // Don't upgrade to 0.55 until https://github.com/akka/akka-persistence-cassandra/issues/230 is resolved
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.54",
      "org.slf4j" % "slf4j-log4j12" % "1.7.12"
    )
  }  
)

lazy val kamonSettings = Seq(
  libraryDependencies ++= {
    Seq(
      "io.kamon" %% "kamon-core" % kamonVersion,
      "io.kamon" %% "kamon-statsd" % kamonVersion,
      "io.kamon" %% "kamon-datadog" % kamonVersion,
      "io.kamon" %% "kamon-system-metrics" % kamonVersion,
      "org.aspectj" % "aspectjweaver" % "1.8.8",
      "io.kamon" %% "kamon-autoweave" % "0.6.5", // missing for 0.6.6
      "com.readytalk" % "metrics3-statsd" % "4.1.0" // to log cassandra (codahale / dropwizard) metrics into statsd
    )
  }
)

lazy val javaSettings = Seq(
  // This forbids including Scala related libraries into the dependency
  autoScalaLibrary := false,

  // Make this a Java-only project in Eclipse
  EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
)

lazy val `ts-reaktive-java` = project
  .settings(projectSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    // This project includes Java -> Scala bridge classes, so we do want the scala library.
    autoScalaLibrary := true
  )

lazy val `ts-reaktive-testkit` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      akkaInMemory
    )
  )

lazy val `ts-reaktive-testkit-assertj` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      assertJ
    )
  )  

lazy val `ts-reaktive-akka` = project
  .settings(commonSettings: _*)

lazy val `ts-reaktive-marshal` = project
  .settings(projectSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.7.4"
    )
  )
  .dependsOn(`ts-reaktive-java`)

lazy val `ts-reaktive-marshal-akka` = project
  .settings(commonSettings: _*)
  .dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml" % "aalto-xml" % "1.0.0",
      "de.undercouch" % "actson" % "1.1.0"
    )
  )

lazy val `ts-reaktive-csv` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-marshal-xerces` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "xerces" % "xercesImpl" % "2.11.0"     
    )
  )
  .dependsOn(`ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-cassandra` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.readytalk" % "metrics3-statsd" % "4.1.0" // to log cassandra (codahale / dropwizard) metrics into statsd
    )
  )
  .dependsOn(`ts-reaktive-akka`, `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-actors` = project
  .enablePlugins(ProtobufPlugin)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    // the .proto files of this project are supposed to be included by others, so they're added to the .jar
    unmanagedResourceDirectories in Compile += (sourceDirectory in ProtobufConfig).value
  )
  .dependsOn(`ts-reaktive-java`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-replication` = project
  .enablePlugins(ProtobufPlugin)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .dependsOn(`ts-reaktive-actors`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-cassandra`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-backup` = project
  .enablePlugins(ProtobufPlugin)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.11"
    )
  )
  .dependsOn(`ts-reaktive-replication`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-ssl` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.54" // for PEMReader, in order to read PEM encoded RSA keys
    )
  )

lazy val `ts-reaktive-kamon-log4j` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka-client` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka-cluster` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)

lazy val root = (project in file(".")).settings(publish := { }, publishLocal := { }).aggregate(
  `ts-reaktive-akka`,
  `ts-reaktive-java`,
  `ts-reaktive-actors`,
  `ts-reaktive-cassandra`,
  `ts-reaktive-replication`,
  `ts-reaktive-backup`,
  `ts-reaktive-ssl`,
  `ts-reaktive-marshal`,
  `ts-reaktive-marshal-akka`,
  `ts-reaktive-marshal-xerces`,
  `ts-reaktive-testkit`,
  `ts-reaktive-testkit-assertj`,
  `ts-reaktive-kamon-log4j`,
  `ts-reaktive-kamon-akka`,
  `ts-reaktive-kamon-akka-client`,
  `ts-reaktive-kamon-akka-cluster`)

// Don't publish the root artifact; only publish sub-projects
publishArtifact := false

publishTo := Some(Resolver.file("unused", file("/tmp/unused")))
