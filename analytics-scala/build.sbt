ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "0.1.0"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:imports")

lazy val analytics = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "analytics-scala",
    Compile / mainClass := Some("gamestats.analytics.Main"),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % "0.10.2",
      "com.lihaoyi" %% "upickle" % "4.1.0",
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    Test / fork := true,
    Test / baseDirectory := baseDirectory.value
  )
