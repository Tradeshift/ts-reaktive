
scalaVersion := "2.13.14" // just for the root

val akkaVersion = "2.8.6"
val akkaHttpVersion = "10.5.3"
val akkaInMemory = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"
val assertJ = "org.assertj" % "assertj-core" % "3.26.3"

import sbtrelease._
// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion=>_,_}

def setVersionOnly(selectVersion: Versions => String): ReleaseStep =  { st: State =>
  val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
  val selected = selectVersion(vs)

  st.log.info("Setting version to '%s'." format selected)
  val useGlobal =Project.extract(st).get(releaseUseGlobalVersion)
  val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

  reapply(Seq(
    if (useGlobal) version in ThisBuild := selected
    else version := selected
  ), st)
}

lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

releaseVersionBump := { System.getProperty("BUMP", "default").toLowerCase match {
  case "major" => sbtrelease.Version.Bump.Major
  case "minor" => sbtrelease.Version.Bump.Minor
  case "bugfix" => sbtrelease.Version.Bump.Bugfix
  case "default" => sbtrelease.Version.Bump.default
}}
    
releaseVersion := { ver => Version(ver)
  .map(_.withoutQualifier)
  .map(_.bump(releaseVersionBump.value).string).getOrElse(versionFormatError)
}

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

releaseCrossBuild := true

crossScalaVersions := Seq("2.12.6")

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  runClean,
  runTest, 
  tagRelease,
  publishArtifacts,
  pushChanges
)

lazy val projectSettings = Seq(
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  organization := "com.tradeshift",
  scalaVersion := "2.13.14",
  crossScalaVersions := Seq("2.12.6"),
  publishMavenStyle := true,
  javacOptions ++= Seq("-source", "1.8"),
  javacOptions in (Compile, Keys.compile) ++= Seq("-target", "1.8", "-Xlint", "-Xlint:-processing", "-Xlint:-serial", "-Werror"),
  javacOptions in doc ++= Seq("-Xdoclint:none"),
  scalacOptions ++= Seq("-target:jvm-1.8"),
  EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18),
  EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.ManagedClasses,
  EclipseKeys.withSource := true,
  javaOptions += "-Xmx128M",
  fork := true,
  resolvers ++= Seq(
    Resolver.bintrayRepo("readytalk", "maven"),
    Resolver.jcenterRepo),
  dependencyOverrides += "com.google.protobuf" % "protobuf-java" % "2.6.1",
  protobufRunProtoc in ProtobufConfig := { args =>
    com.github.os72.protocjar.Protoc.runProtoc("-v261" +: args.toArray)
  },
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),
  libraryDependencies ++= Seq(
    "io.vavr" % "vavr" % "0.10.4",
    "org.slf4j" % "slf4j-api" % "1.7.36",
    "org.slf4j" % "slf4j-log4j12" % "1.7.36" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
    "junit" % "junit" % "4.13.2" % "test",
    assertJ % "test",
    "org.mockito" % "mockito-core" % "5.14.2" % "test",
    "info.solidsoft.mockito" % "mockito-java8" % "2.5.0" % "test",
    akkaInMemory % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.6.0" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.7.0" % "test",
    "org.apache.cassandra" % "cassandra-all" % "5.0.1" % "test" exclude("ch.qos.logback", "logback-classic"),
    "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "1.1.1" % "test",
    "com.github.tomakehurst" % "wiremock" % "3.0.1" % "test",
    "org.xmlunit" % "xmlunit-core" % "2.9.1" % "test",
    "org.xmlunit" % "xmlunit-matchers" % "2.10.0" % "test"
  ),
  git.useGitDescribe := true,
  git.baseVersion := "0.1.0",
  git.gitTagToVersionNumber := {
    case VersionRegex(v,"") => Some(v)
    case VersionRegex(v,"SNAPSHOT") => Some(s"$v-SNAPSHOT")  
    case VersionRegex(v,s) => Some(s"$v-$s-SNAPSHOT")
    case s => None
  }  
)

lazy val commonSettings = projectSettings ++ Seq(
  libraryDependencies ++= {
    Seq(
      "com.typesafe" % "config" % "1.4.3",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.107",
      "org.quicktheories" % "quicktheories" % "0.26" % "test",
      "org.slf4j" % "slf4j-log4j12" % "1.7.36"
    )
  }  
)

lazy val kamonSettings = Seq(
  libraryDependencies ++= {
    Seq(
      "io.kamon" %% "kamon-core" % "1.1.6",
      "io.kamon" %% "kamon-system-metrics" % "1.0.1",
      "org.aspectj" % "aspectjweaver" % "1.9.22.1",
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
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    // This project includes Java -> Scala bridge classes, so we do want the scala library.
    autoScalaLibrary := true
  )
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-testkit` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      akkaInMemory
    )
  )
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-testkit-assertj` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      assertJ
    )
  )
  .dependsOn(`ts-reaktive-actors`)
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-akka` = project
  .settings(commonSettings: _*)
  .dependsOn(`ts-reaktive-testkit` % "test")
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-marshal` = project
  .settings(projectSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.18.0"
    )
  )
  .dependsOn(`ts-reaktive-java`)
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-marshal-akka` = project
  .settings(commonSettings: _*)
  .dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml" % "aalto-xml" % "1.3.3",
      "de.undercouch" % "actson" % "1.2.0"
    )
  )
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-csv` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .dependsOn(`ts-reaktive-marshal`, `ts-reaktive-akka`, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-marshal-xerces` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "xerces" % "xercesImpl" % "2.12.2"     
    )
  )
  .dependsOn(`ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test", `ts-reaktive-testkit-assertj` % "test")
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-cassandra` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.readytalk" % "metrics3-statsd" % "4.1.0" // to log cassandra (codahale / dropwizard) metrics into statsd
    )
  )
  .dependsOn(`ts-reaktive-java`, `ts-reaktive-akka`, `ts-reaktive-testkit-assertj` % "test")
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-marshal-scala` = project
  .settings(projectSettings: _*)
  .dependsOn(
    `ts-reaktive-marshal`,
    `ts-reaktive-marshal-akka`,
    `ts-reaktive-testkit` % "test",
    `ts-reaktive-marshal-akka` % "test")
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-xsd` = project
  .settings(projectSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % "test"
  ))
  .dependsOn(
    `ts-reaktive-marshal-scala`,
    `ts-reaktive-testkit` % "test",
    `ts-reaktive-marshal-akka` % "test")

lazy val `ts-reaktive-actors` = project
  .enablePlugins(ProtobufPlugin)
  .enablePlugins(GitVersioning)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(kamonSettings: _*)
  .settings(
    // the .proto files of this project are supposed to be included by others, so they're added to the .jar
    unmanagedResourceDirectories in Compile += (sourceDirectory in ProtobufConfig).value
  )
  .dependsOn(`ts-reaktive-java`, `ts-reaktive-akka`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-replication` = project
  .enablePlugins(ProtobufPlugin)
  .enablePlugins(GitVersioning)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .dependsOn(`ts-reaktive-actors`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-cassandra`, `ts-reaktive-ssl`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-backup` = project
  .enablePlugins(ProtobufPlugin)
  .enablePlugins(GitVersioning)
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.20"
    )
  )
  .dependsOn(`ts-reaktive-replication`, `ts-reaktive-actors` % ProtobufConfig.name, `ts-reaktive-marshal-akka`, `ts-reaktive-testkit` % "test")

lazy val `ts-reaktive-ssl` = project
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70" // for PEMReader, in order to read PEM encoded RSA keys
    )
  )
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-kamon-log4j` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)
  .enablePlugins(GitVersioning)

lazy val `ts-reaktive-kamon-akka-cluster` = project
  .settings(commonSettings: _*)
  .settings(kamonSettings: _*)
  .enablePlugins(GitVersioning)

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
  `ts-reaktive-marshal-scala`,
  `ts-reaktive-marshal-xerces`,
  `ts-reaktive-xsd`,
  `ts-reaktive-testkit`,
  `ts-reaktive-testkit-assertj`,
  `ts-reaktive-kamon-log4j`,
  `ts-reaktive-kamon-akka-cluster`)

// Don't publish the root artifact; only publish sub-projects
publishArtifact := false

publishTo := Some(Resolver.file("unused", file("/tmp/unused")))
