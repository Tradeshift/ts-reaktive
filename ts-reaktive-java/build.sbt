
description := "Reactive persistent actor base classes for Akka persistence"

// Do not append Scala versions to the generated artifacts
crossPaths := false

// This forbids including Scala related libraries into the dependency
autoScalaLibrary := false

// Make this a Java-only project in Eclipse
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java

// Because of https://github.com/cuppa-framework/cuppa/pull/113
parallelExecution in Test := false

// library dependencies. (organization name) % (project name) % (version) % (scope)
libraryDependencies ++= {
  Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.4",  
    "com.google.protobuf" % "protobuf-java" % "2.6.1",
    "junit" % "junit" % "4.11" % "test",
    "org.assertj" % "assertj-core" % "3.2.0" % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test",
    "info.solidsoft.mockito" % "mockito-java8" % "0.3.0" % "test",
    "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.2.0" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.1.0" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.1.0" % "test",
    "org.apache.cassandra" % "cassandra-all" % "3.0.3" % "test" exclude("ch.qos.logback", "logback-classic") exclude("org.slf4j","log4j-over-slf4j"),
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test"
  )
}
