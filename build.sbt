name := "codacy-bandit"
ThisBuild / scalaVersion := "2.13.7"

val engineSeed = "com.codacy" %% "codacy-engine-scala-seed" % "5.0.3"

libraryDependencies += engineSeed

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++= Seq(
      engineSeed,
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
      "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
      "com.github.pathikrit" %% "better-files" % "3.8.0"
    ),
    Compile / fork := true
  )

enablePlugins(JavaAppPackaging)
