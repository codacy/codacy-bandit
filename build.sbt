import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "codacy-bandit"

version := "1.0.0-SNAPSHOT"

val languageVersion = "2.12.6"

scalaVersion := languageVersion

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "3.0.183"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0.0"

lazy val toolVersion = taskKey[String](
  "Retrieve the version of the underlying tool from patterns.json"
)
toolVersion := {
  import better.files.File
  import play.api.libs.json.{JsString, JsValue, Json}

  val jsonFile = resourceDirectory.in(Compile).value / "docs" / "patterns.json"
  val patternsJsonValues =
    Json.parse(File(jsonFile.toPath).contentAsString).as[Map[String, JsValue]]

  patternsJsonValues
    .collectFirst {
      case ("version", JsString(version)) => version
    }
    .getOrElse(
      throw new Exception("Failed to retrieve version from docs/patterns.json")
    )
}

def installAll(toolVersion: String) =
  s"""apk --no-cache add bash wget ca-certificates git &&
     |apk add --update --no-cache python &&
     |apk add --update --no-cache python3 &&
     |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python &&
     |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python3 &&
     |python -m pip install bandit===${toolVersion} --upgrade --ignore-installed --no-cache-dir &&
     |python3 -m pip install bandit===${toolVersion} --upgrade --ignore-installed --no-cache-dir &&
     |python -m pip uninstall -y pip &&
     |python3 -m pip uninstall -y pip &&
     |apk del wget ca-certificates git &&
     |rm -rf /tmp/* &&
     |rm /var/cache/apk/*""".stripMargin.replaceAll(System.lineSeparator(), " ")

mappings.in(Universal) ++= resourceDirectory
  .in(Compile)
  .map { resourceDir: File =>
    val src = resourceDir / "docs"
    val dest = "/docs"
    (for {
      path <- better.files.File(src.toPath).listRecursively()
      if !path.isDirectory
    } yield path.toJava -> path.toString.replaceFirst(src.toString, dest)).toSeq
  }
  .value ++
  baseDirectory
    .in(Compile)
    .map { baseDirectory: File =>
      val toolScriptsDir = baseDirectory / "tool-scripts"
      (for {
        path <- better.files.File(toolScriptsDir.toPath).listRecursively()
        if !path.isDirectory
      } yield
        path.toJava -> path.toString.replaceFirst(toolScriptsDir.toString, "")).toSeq
    }
    .value

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "openjdk:8-jre-alpine"

dockerCommands := {
  dockerCommands.dependsOn(toolVersion).value.flatMap {
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
