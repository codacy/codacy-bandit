import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := """codacy-engine-bandit"""

version := "1.0-SNAPSHOT"

val languageVersion = "2.11.7"

scalaVersion := languageVersion

resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.3.10" withSources(),
  "com.codacy" %% "codacy-engine-scala-seed" % "2.7.1"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0"

val banditTag = "1.0.2.dev5-with-m"
val banditPath = "/opt/docker/bandit"

val installAll =
  s"""apt-get update && apt-get install bash curl &&
      |apt-get -y install python &&
      |apt-get -y install python3 &&
      |apt-get -y install wget ca-certificates &&
      |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python &&
      |wget "https://bootstrap.pypa.io/get-pip.py" -O /dev/stdout | python3 &&
      |git clone https://github.com/codacy/bandit.git &&
      |(cd $banditPath && git checkout tags/$banditTag) &&
      |python -m pip install --upgrade --ignore-installed --no-cache-dir -e $banditPath &&
      |python3 -m pip install --upgrade --ignore-installed --no-cache-dir -e $banditPath
      |""".stripMargin.replaceAll(System.lineSeparator(), " ")

mappings in Universal <++= (resourceDirectory in Compile) map { (resourceDir: File) =>
  val src = resourceDir / "docs"
  val dest = "/docs"

  for {
    path <- (src ***).get
    if !path.isDirectory
  } yield path -> path.toString.replaceFirst(src.toString, dest)
}

mappings in Universal <++= (baseDirectory in Compile) map { (directory: File) =>
  val src = directory / "tool-scripts"

  for {
    path <- (src ***).get
    if !path.isDirectory
  } yield path -> src.toPath.relativize(path.toPath).toString
}

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "rtfpessoa/ubuntu-jdk8"

dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("WORKDIR", _) => List(cmd,
    Cmd("RUN", installAll)
  )
  case cmd@(Cmd("ADD", "opt /opt")) => List(cmd,
    Cmd("RUN", "mv /opt/docker/docs /docs"),
    Cmd("RUN", "adduser --uid 2004 --disabled-password --gecos \"\" docker"),
    ExecCmd("RUN", Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*)
  )
  case other => List(other)
}
