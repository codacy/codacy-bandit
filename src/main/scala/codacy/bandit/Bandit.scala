package codacy.bandit

import java.nio.file.Path

import codacy.dockerApi._
import codacy.dockerApi.utils.{CommandRunner, ToolHelper}
import play.api.libs.json.Json

import scala.io.Source
import scala.util.Try

object Bandit extends Tool {

  override def apply(path: Path, conf: Option[List[PatternDef]], files: Option[Set[Path]])(implicit spec: Spec): Try[List[Result]] = {
    Try {
      val fullConfig = ToolHelper.getPatternsToLint(conf)
      val filesToLint: List[String] = files.fold(List(path.toString)) {
        paths =>
          paths.map(_.toString).toList
      }

      lazy val enabledPatterns = fullConfig.fold(Set.empty[PatternId]) {
        patternDef =>
          patternDef.map(_.patternId).toSet
      }

      val toolResultPath = "/tmp/bandit-out.json"
      val command = List("bandit", "-f", "json", "-o", toolResultPath) ++ filesToLint

      CommandRunner.exec(command) match {
        case Right(resultFromTool) if resultFromTool.exitCode <= 1 =>
          val resultFromToolJson = Source.fromFile(toolResultPath).getLines().mkString
          parseToolResult(resultFromToolJson)
            .filter(result => resultFilter(result, enabledPatterns))

        case Right(resultFromTool) if resultFromTool.exitCode > 1 =>
          throw new Exception(
            s"Error code: ${resultFromTool.exitCode}\n" +
              s"stdout: ${resultFromTool.stdout}\n" +
              s"sterr: ${resultFromTool.stdout}\n"
          )
        case Left(failure) =>
          throw failure
      }
    }
  }

  private def resultFilter(result: Result, patternIds: Set[PatternId]): Boolean = {
    result match {
      case Issue(_, _, id, _) =>
        patternIds.contains(id)
      case _ =>
        true
    }
  }

  private def parseToolResult(toolResult: String): List[Result] = {
    val jsonResult = Json.parse(toolResult)

    val fileErrors: List[Result] = (jsonResult \ "errors").as[Seq[BanditFileError]].map(_.fileError).toList
    val warnings: List[Result] = (jsonResult \ "results").as[Seq[BanditWarning]].map(_.issue).toList

    fileErrors ++ warnings
  }

}
