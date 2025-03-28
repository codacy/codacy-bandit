name := "codacy-bandit"
ThisBuild / scalaVersion := "2.13.12"

val engineSeed = "com.codacy" %% "codacy-engine-scala-seed" % "6.1.3"

libraryDependencies ++= Seq(
  engineSeed,
  "com.github.pathikrit" %% "better-files" % "3.9.2"
)

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++= Seq(
      engineSeed,
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
      "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
      "com.github.pathikrit" %% "better-files" % "3.9.2"
    ),
    Compile / fork := true
  )

enablePlugins(JavaAppPackaging)
