import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "codacy-bandit"
scalaVersion := "2.13.1"

val engineSeed = "com.codacy" %% "codacy-engine-scala-seed" % "5.0.0"

libraryDependencies += engineSeed

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++=
      engineSeed +: Seq(
        "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
        "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
        "com.github.pathikrit" %% "better-files" % "3.8.0"
      ),
    scalaVersion := "2.13.1",
    Compile / fork := true,
    scalacOptions += "-Xlint:-stars-align"
  )

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

mappings.in(Universal) ++= resourceDirectory
  .in(Compile)
  .map { resourceDir: File =>
    val src = resourceDir / "docs"
    val dest = "/docs"

    for {
      path <- src.allPaths.get if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  }
  .value ++
  baseDirectory
    .in(Compile)
    .map { baseDirectory: File =>
      val toolScriptsDir = baseDirectory / "tool-scripts"
      for {
        path <- toolScriptsDir.allPaths.get if !path.isDirectory
      } yield path -> path.toString.replaceFirst(toolScriptsDir.toString, "")
    }
    .value

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "codacy-bandit-base"

mainClass in Compile := Some("codacy.Engine")

dockerCommands := {
  dockerCommands.value.flatMap {
    case cmd @ Cmd("ADD", _) =>
      List(
        Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
        cmd,
        Cmd("RUN", "mv /opt/docker/docs /docs"),
        ExecCmd("RUN", Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*)
      )
    case other => List(other)
  }
}
