
description := "Provides reactive CSV streaming and marshalling support"

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
    "junit" % "junit" % "4.11" % "test",
    "org.assertj" % "assertj-core" % "3.2.0" % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test",
    "info.solidsoft.mockito" % "mockito-java8" % "0.3.0" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.1.0" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.1.0" % "test",
    "org.xmlunit" % "xmlunit-core" % "2.2.1" % "test",
    "org.xmlunit" % "xmlunit-matchers" % "2.2.1" % "test",
    "com.github.tomakehurst" % "wiremock" % "1.58" % "test"    
  )
}
