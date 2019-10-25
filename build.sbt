import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "codacy-bandit"
scalaVersion := "2.13.1"
version := "1.0.0-SNAPSHOT"

lazy val toolVersion = settingKey[String]("The tool version")
toolVersion := scala.io.Source.fromFile("bandit-version").mkString.trim

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
      "com.github.tkqubo" % "html-to-markdown" % "0.3.0",
      "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
      "com.github.pathikrit" %% "better-files" % "3.8.0",
      "com.typesafe.play" %% "play-json" % "2.7.4",
      "com.codacy" %% "codacy-plugins-api" % "3.0.80"
    ) ++ Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % "0.12.3"),
    scalaVersion := "2.13.1",
    scalacOptions --= Seq("-Xlint"), // circe implicit val for encode
    Compile / fork := true
  )

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "3.1.0",
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0.0"

def installAll(version: String) =
  s"""apk --no-cache add bash wget ca-certificates git &&
     |apk add --update --no-cache python &&
     |apk add --update --no-cache python3 &&
     |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python &&
     |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python3 &&
     |python -m pip install bandit===${version} --upgrade --ignore-installed --no-cache-dir &&
     |python3 -m pip install bandit===${version} --upgrade --ignore-installed --no-cache-dir &&
     |python -m pip uninstall -y pip &&
     |python3 -m pip uninstall -y pip &&
     |apk del wget ca-certificates git &&
     |rm -rf /tmp/* &&
     |rm -rf /var/cache/apk/*""".stripMargin
    .replaceAll(System.lineSeparator(), " ")

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

dockerBaseImage := "openjdk:8-jre-alpine"

mainClass in Compile := Some("codacy.Engine")

dockerCommands := {
  dockerCommands.value.flatMap {
    case cmd @ Cmd("ADD", _) =>
      List(
        Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
        cmd,
        Cmd("RUN", installAll(toolVersion.value)),
        Cmd("RUN", "mv /opt/docker/docs /docs"),
        ExecCmd(
          "RUN",
          Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*
        )
      )
    case other => List(other)
  }
}