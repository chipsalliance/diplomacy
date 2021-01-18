// SPDX-License-Identifier: Apache-2.0

val chiselVersion = "3.4.1"

lazy val commonSettings = Seq(
  organization := "org.chipsalliance",
  version      := "0.1",
  scalaVersion := "2.12.12",
  parallelExecution in Global := false,
  libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.0" % "test"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { x => false },
)

lazy val chisel3Ref = ProjectRef(uri(s"git://github.com/chipsalliance/chisel3#v$chiselVersion"), "chisel")
lazy val chisel3Lib = "edu.berkeley.cs" %% "chisel3" % chiselVersion
lazy val chiselPluginLib = "edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full

lazy val diplomacy = project
  .in(file("diplomacy"))
  .sourceDependency(chisel3Ref, chisel3Lib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(commonSettings)
