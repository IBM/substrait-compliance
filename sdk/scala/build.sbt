name := "substrait-compliance"

version := "1.0.0"

scalaVersion := "2.13.12"

organization := "io.substrait"

description := "Scala SDK for implementing and testing Substrait compliance in query engines"

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

homepage := Some(url("https://github.com/substrait-io/substrait-compliance"))

// Dependencies
libraryDependencies ++= Seq(
  // YAML parsing
  "org.yaml" % "snakeyaml" % "2.2",
  
  // JSON support
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "io.circe" %% "circe-yaml" % "0.15.1",
  
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
  
  // Async support
  "org.scala-lang.modules" %% "scala-async" % "1.0.1",
  
  // Cats for functional programming
  "org.typelevel" %% "cats-core" % "2.10.0",
  "org.typelevel" %% "cats-effect" % "3.5.2"
)

// Compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

// Test options
Test / parallelExecution := true
Test / testOptions += Tests.Argument("-oD")

// Publishing settings
publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}