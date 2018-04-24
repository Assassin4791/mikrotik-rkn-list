import sbt._
import sbt.Keys._

val projectName = "mikrotik-rkn-list"
val scalaVersionString = "2.12.5"
val versionString = "0.1-SNAPSHOT"
name := projectName
version := versionString
scalaVersion := scalaVersionString

val jarFileName = s"$projectName-$versionString.jar"

assemblyJarName in assembly := jarFileName

lazy val typesafeConfigModule = "com.typesafe" % "config" % "1.3.1"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies ++= Seq(typesafeConfigModule, logback)