package codacy.bandit

import com.codacy.plugins.api.results.Result.Issue
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.plugins.api.{Options, Source}
import com.codacy.tools.scala.seed.utils.ToolHelper._
import com.codacy.tools.scala.seed.utils.{CommandRunner, FileHelper}
import play.api.libs.json.Json

import scala.util.matching.Regex
import scala.util.{Success, Try}

private case class FilesByVersion(python2: List[String], python3: List[String])

private case class ClassifiedLine(filename: String, pythonVersion: Int)

object Bandit extends Tool {

  private val ClassifiedLineRegex: Regex = """(.*)###(\d)""".r

  override def apply(
    source: Source.Directory,
    conf: Option[List[Pattern.Definition]],
    files: Option[Set[Source.File]],
    options: Map[Options.Key, Options.Value]
  )(implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      val fullConfig = conf.withDefaultParameters
      val filesToLint: List[String] = files.fold(List(source.path.toString)) {
        paths => paths.map(_.toString).toList
      }

      lazy val enabledPatterns = fullConfig.map(_.map(_.patternId).to[Set])

      val filesByVersion = partitionFilesByPythonVersion(filesToLint)

      runTool(source, "python", filesByVersion.python2, enabledPatterns) ++
        runTool(source, "python3", filesByVersion.python3, enabledPatterns)
    }
  }

  private def toClassifiedLine(line: String): ClassifiedLine = {
    line match {
      case ClassifiedLineRegex(filename, pythonVersion) =>
        ClassifiedLine(filename, pythonVersion.toInt)
    }
  }

  private def partitionFilesByPythonVersion(
    filesToLint: List[String]
  ): FilesByVersion = {
    /*
     * The classifyScript.py will return one line per file in fileName###PythonVersion format
     * Example: B104.py###2
     */
    val command = List("python", "classifyScript.py") ++ filesToLint

    CommandRunner.exec(command) match {
      case Right(res) if res.exitCode == 0 =>
        val (python2, python3) =
          res.stdout.map(toClassifiedLine).partition(_.pythonVersion == 2)
        FilesByVersion(python2.map(_.filename), python3.map(_.filename))

      case Right(resultFromTool) if resultFromTool.exitCode > 0 =>
        throw new scala.Exception(
          s"[ClassifyScript]\n" +
            s"Exit code: ${resultFromTool.exitCode}\n" +
            s"stdout: ${resultFromTool.stdout}\n" +
            s"sterr: ${resultFromTool.stdout}\n"
        )
      case Left(failure) =>
        throw failure
    }
  }

  private lazy val nativeConfigFileNames = Set("bandit.yml", ".bandit")

  private def runTool(
    rootPath: Source.Directory,
    pythonEngine: String,
    filesToLint: List[String],
    enabledPatterns: Option[Set[Pattern.Id]]
  ): List[Result] = {
    lazy val nativeConfigFile = nativeConfigFileNames
      .map(filename => Try(better.files.File(rootPath.path) / filename))
      .collectFirst {
        case Success(file) if file.isRegularFile => file.toJava.getAbsolutePath
      }

    if (filesToLint.isEmpty) {
      List.empty[Result]
    } else {
      val toolResultPath =
        FileHelper.createTmpFile("", "tool-out-", ".json").toString
      val nativeConfigParams: List[String] =
        if (enabledPatterns.isEmpty) {
          nativeConfigFile.to[List].flatMap(cfgFile => List("-c", cfgFile))
        } else { List.empty }

      val command = List(
        pythonEngine,
        "-m",
        "bandit",
        "-f",
        "json",
        "-o",
        toolResultPath
      ) ++ nativeConfigParams ++ filesToLint

      CommandRunner.exec(command) match {
        case Right(resultFromTool) if resultFromTool.exitCode <= 1 =>
          val resultFromToolJson =
            scala.io.Source.fromFile(toolResultPath).getLines().mkString
          parseToolResult(resultFromToolJson)
            .filter(result => resultFilter(result, enabledPatterns))

        case Right(resultFromTool) if resultFromTool.exitCode > 1 =>
          throw new scala.Exception(
            s"Exit code: ${resultFromTool.exitCode}\n" +
              s"stdout: ${resultFromTool.stdout}\n" +
              s"sterr: ${resultFromTool.stdout}\n"
          )
        case Left(failure) =>
          throw failure
      }
    }
  }

  private def resultFilter(result: Result,
                           patternIds: Option[Set[Pattern.Id]]): Boolean = {
    result match {
      case Issue(_, _, id, _) =>
        patternIds.forall(_.contains(id))
      case _ =>
        true
    }
  }

  private def parseToolResult(toolResult: String): List[Result] = {
    val jsonResult = Json.parse(toolResult)

    val fileErrors: List[Result] =
      (jsonResult \ "errors").as[List[BanditFileError]].map(_.fileError)
    val warnings: List[Result] =
      (jsonResult \ "results").as[List[BanditWarning]].map(_.issue)

    fileErrors ++ warnings
  }

}
