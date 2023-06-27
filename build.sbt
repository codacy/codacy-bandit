name := "codacy-bandit"
ThisBuild / scalaVersion := "2.13.11"

val engineSeed = "com.codacy" %% "codacy-engine-scala-seed" % "6.1.0"
val betterFiles = "com.github.pathikrit" %% "better-files" % "3.9.2"

libraryDependencies ++= Seq(engineSeed, betterFiles)

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++= Seq(
      engineSeed,
      betterFiles,
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
      "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
    ),
    Compile / fork := true
  )

enablePlugins(JavaAppPackaging)
