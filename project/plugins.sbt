addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.2")

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")

libraryDependencies += "com.github.os72" % "protoc-jar" % "3.11.4"

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.3")
