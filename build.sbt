import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "codacy-bandit"
scalaVersion := "2.12.6"
version := "1.0.0-SNAPSHOT"
val banditVersion = "1.5.1"

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "3.0.183",
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

mainClass in Compile := Some("codacy.Engine")

dockerCommands := {
  dockerCommands.value.flatMap {
    case cmd @ Cmd("ADD", _) =>
      List(
        Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
        cmd,
        Cmd("RUN", installAll(banditVersion)),
        Cmd("RUN", "mv /opt/docker/docs /docs"),
        ExecCmd(
          "RUN",
          Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*
        )
      )
    case other => List(other)
  }
}

lazy val generateDocs = taskKey[Unit]("Generates Documentation'")
import scala.sys.process._
generateDocs := {
  val baseDir = "bandit"
  s"rm -rf ${baseDir}" !;
  s"git clone -b ${banditVersion} --single-branch --depth 1 https://github.com/PyCQA/bandit.git ${baseDir}" !;
  s"virtualenv ./${baseDir}/venv" !;
  s"./${baseDir}/venv/bin/pip install -U -r ${baseDir}/requirements.txt" !;
  s"./${baseDir}/venv/bin/pip install -r ${baseDir}/doc/requirements.txt" !;
  s"./${baseDir}/venv/bin/sphinx-build ${baseDir}/doc/source/ ${baseDir}/doc/build/ -b html -a -D html_add_permalinks=" !;
  docs.GenerateDocs.run(banditVersion, baseDir);
  s"rm -rf ${baseDir}" !;
}

