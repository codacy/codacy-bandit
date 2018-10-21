name := "codacy-bandit"
version := "1.0.0-SNAPSHOT"
scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "com.github.tkqubo" % "html-to-markdown" % "0.3.0",
  "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
)

val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
