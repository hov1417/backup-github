ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
    .enablePlugins(GraalVMNativeImagePlugin)
    .enablePlugins(LinuxPlugin)
    .settings(
        name                                    := "backup-github",
        libraryDependencies += "org.scala-lang" %% "toolkit" % "0.2.0",
        graalVMNativeImageOptions += "--no-fallback",
        graalVMNativeImageGraalVersion := Some("22.1.0")
    )

