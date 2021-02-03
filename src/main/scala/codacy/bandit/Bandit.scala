package codacy.bandit

import com.codacy.plugins.api.results.Result.Issue
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.plugins.api.{Options, Source}
import com.codacy.tools.scala.seed.utils.ToolHelper._
import com.codacy.tools.scala.seed.utils.{CommandRunner, FileHelper}
import play.api.libs.json.Json

import scala.util.{Success, Try}

private case class FilesByVersion(python2: List[String], python3: List[String])

private case class ClassifiedLine(filename: String, pythonVersion: Int)

object Bandit extends Tool {
  override def apply(
      source: Source.Directory,
      conf: Option[List[Pattern.Definition]],
      files: Option[Set[Source.File]],
      options: Map[Options.Key, Options.Value]
  )(implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      val fullConfig = conf.withDefaultParameters
      val filesToLint: List[String] = files.fold(List(source.path.toString)) { paths =>
        paths.map(_.toString).toList
      }

      lazy val enabledPatterns = fullConfig.map(_.map(_.patternId).to(Set))

      runTool(source, "python3", filesToLint, enabledPatterns)
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
          nativeConfigFile.to(List).flatMap(cfgFile => List("-c", cfgFile))
        } else { List.empty }

      val command = List(pythonEngine, "-m", "bandit", "-f", "json", "-o", toolResultPath) ++ nativeConfigParams ++ filesToLint

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
        case Right(_) => throw new IllegalStateException
      }
    }
  }

  private def resultFilter(result: Result, patternIds: Option[Set[Pattern.Id]]): Boolean = {
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
