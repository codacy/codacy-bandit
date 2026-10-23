name := "codacy-bandit"
ThisBuild / scalaVersion := "2.13.12"

val engineSeed = "com.codacy" %% "codacy-engine-scala-seed" % "6.1.3"

libraryDependencies ++= Seq(
  engineSeed,
  "com.github.pathikrit" %% "better-files" % "3.9.2"
)

enablePlugins(JavaAppPackaging)
