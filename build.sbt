scalaVersion := "2.11.8"

lazy val commonSettings = Seq(
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  organization := "com.tradeshift",
  version := "0.0.3",
  scalaVersion := "2.11.8",
  publishMavenStyle := true,
  javacOptions ++= Seq("-source", "1.8"),
  javacOptions in (Compile, Keys.compile) ++= Seq("-target", "1.8", "-Xlint", "-Xlint:-processing", "-Xlint:-serial", "-Werror"),
  javacOptions in doc ++= Seq("-Xdoclint:none"),
  EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
  EclipseKeys.withSource := true,
  libraryDependencies ++= {
    val akkaVersion = "2.4.6"
    val kamonVersion = "0.6.1"

    Seq(
      "io.javaslang" % "javaslang" % "2.0.1",
      "com.typesafe" % "config" % "1.3.0",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-jackson-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % "test",
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.14",
      "com.readytalk" % "metrics3-statsd" % "4.1.0", // to log cassandra (codahale / dropwizard) metrics into statsd
      "io.kamon" %% "kamon-core" % kamonVersion,
      "io.kamon" %% "kamon-akka" % kamonVersion,
      "io.kamon" %% "kamon-statsd" % kamonVersion,
      "io.kamon" %% "kamon-datadog" % kamonVersion,
      "io.kamon" %% "kamon-log-reporter" % kamonVersion,
      "io.kamon" %% "kamon-system-metrics" % kamonVersion,
      "org.aspectj" % "aspectjweaver" % "1.8.5",
      "org.slf4j" % "slf4j-log4j12" % "1.7.12"
    )
  }  
)

lazy val `ts-reaktive-testkit` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-testkit-assertj` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-actors` = project.settings(commonSettings: _*).dependsOn(`ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-kamon-log4j` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-kamon-akka` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-kamon-akka-http` = project.settings(commonSettings: _*)

lazy val `ts-reaktive-kamon-akka-cluster` = project.settings(commonSettings: _*)

lazy val root = (project in file(".")).settings(publish := { }, publishLocal := { }).aggregate(
  `ts-reaktive-actors`,
  `ts-reaktive-testkit`,
  `ts-reaktive-testkit-assertj`,
  `ts-reaktive-kamon-log4j`,
  `ts-reaktive-kamon-akka`,
  `ts-reaktive-kamon-akka-http`,
  `ts-reaktive-kamon-akka-cluster`)

// Don't publish the root artifact; only publish sub-projects
publishArtifact := false

publishTo := Some(Resolver.file("unused", file("/tmp/unused")))
