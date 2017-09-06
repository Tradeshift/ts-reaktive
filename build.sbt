crossScalaVersions := Seq("2.11.11", "2.12.3")

enablePlugins(ProtobufPlugin)

lazy val projectSettings = Seq(
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  organization := "com.tradeshift",
  version := "0.0.29-SNAPSHOT",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  publishMavenStyle := true,
  javacOptions ++= Seq("-source", "1.8"),
  javacOptions in (Compile, Keys.compile) ++= Seq("-target", "1.8", "-Xlint", "-Xlint:-processing", "-Xlint:-serial", "-Werror"),
  javacOptions in doc ++= Seq("-Xdoclint:none"),
  EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
  EclipseKeys.withSource := true,
  fork := true,
  resolvers ++= Seq(
    Resolver.bintrayRepo("readytalk", "maven"),
    Resolver.jcenterRepo),
  dependencyOverrides += "com.google.protobuf" % "protobuf-java" % "2.6.1",
  //unmanagedResourceDirectories in Compile += { sourceDirectory in ProtobufConfig },
  protobufRunProtoc in ProtobufConfig := { args =>
    com.github.os72.protocjar.Protoc.runProtoc("-v261" +: args.toArray)
  },
  libraryDependencies ++= Seq(
    "io.javaslang" % "javaslang" % "2.0.5",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.slf4j" % "slf4j-log4j12" % "1.7.12" % "test"
  )
)

lazy val akkaVersion = "2.4.18"

lazy val commonSettings = projectSettings ++ Seq(
  libraryDependencies ++= {
    val akkaHttpVersion = "10.0.7"
    val kamonVersion = "0.6.6"

    Seq(
      "com.typesafe" % "config" % "1.3.0",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.29",
      "org.slf4j" % "slf4j-log4j12" % "1.7.12"
    )
  }  
)

lazy val kamonSettings = Seq(
  libraryDependencies ++= {
    val kamonVersion = "0.6.6"

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

lazy val `ts-reaktive-java` = project.settings(projectSettings: _*)

lazy val `ts-reaktive-testkit` = project.settings(commonSettings :+ (libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion) : _*)

lazy val `ts-reaktive-testkit-assertj` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-akka` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-marshal` = project.settings(projectSettings: _*).dependsOn(`ts-reaktive-java`)

lazy val `ts-reaktive-marshal-akka` = project.settings(commonSettings: _*).dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-csv` = project.settings(commonSettings: _*).dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-marshal-xerces` = project.settings(commonSettings: _*).dependsOn(`ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-cassandra` = project.settings(commonSettings: _*).dependsOn(`ts-reaktive-akka`, `ts-reaktive-testkit-assertj` % "test")

lazy val `ts-reaktive-actors` = project.enablePlugins(ProtobufPlugin).settings(commonSettings: _*).dependsOn(`ts-reaktive-java`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-replication` = project.enablePlugins(ProtobufPlugin).settings(commonSettings: _*).dependsOn(`ts-reaktive-actors`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-cassandra`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-backup` = project.enablePlugins(ProtobufPlugin).settings(commonSettings: _*).dependsOn(`ts-reaktive-replication`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-ssl` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-kamon-log4j` = project.settings(commonSettings: _*).settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka` = project.settings(commonSettings: _*).settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka-client` = project.settings(commonSettings: _*).settings(kamonSettings: _*)

lazy val `ts-reaktive-kamon-akka-cluster` = project.settings(commonSettings: _*).settings(kamonSettings: _*)

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
