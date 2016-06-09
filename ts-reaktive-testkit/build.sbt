
description := "Test kit for testing akka routes with real http calls"

// Do not append Scala versions to the generated artifacts
crossPaths := false

// This forbids including Scala related libraries into the dependency
autoScalaLibrary := false

// Make this a Java-only project in Eclipse
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java

// Because of https://github.com/cuppa-framework/cuppa/pull/113
parallelExecution in Test := false

libraryDependencies ++= {
  Seq(
    "junit" % "junit" % "4.11" % "test",
    "org.assertj" % "assertj-core" % "3.2.0" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.1.0" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.1.0" % "test"
  )
}
