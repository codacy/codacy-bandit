package codacy.bandit

import java.nio.file.Path

import codacy.dockerApi._
import codacy.dockerApi.utils.{CommandRunner, ToolHelper}
import play.api.libs.json.Json

import scala.io.Source
import scala.util.Try

private case class FilesByVersion(python2: List[String], python3: List[String])

private case class ClassifiedLine(filename: String, pythonVersion: Int)

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

      val filesByVersion = partitionFilesByPythonVersion(filesToLint)

      runTool("python", filesByVersion.python2, enabledPatterns) ++
        runTool("python3", filesByVersion.python3, enabledPatterns)
    }
  }

  private val ClassifiedLineRegex = """(.*)###(\d)""".r

  private def toClassifiedLine(line: String): ClassifiedLine = {
    line match {
      case ClassifiedLineRegex(filename, pythonVersion) =>
        ClassifiedLine(filename, pythonVersion.toInt)
    }
  }

  private def partitionFilesByPythonVersion(filesToLint: List[String]): FilesByVersion = {
    /*
     * The classifyScript.py will return one line per file in fileName###PythonVersion format
     * Example: B104.py###2
     */
    val command = List("python", "classifyScript.py") ++ filesToLint

    CommandRunner.exec(command) match {
      case Right(res) if res.exitCode == 0 =>
        val (python2, python3) = res.stdout.map(toClassifiedLine).partition(_.pythonVersion == 2)
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

  private def runTool(pythonEngine: String, filesToLint: List[String], enabledPatterns: => Set[PatternId]): List[Result] = {
    if (filesToLint.isEmpty) {
      List.empty[Result]
    }
    else {
      val toolResultPath = "/tmp/bandit-out.json"
      val command = List(pythonEngine, "-m", "bandit", "-f", "json", "-o", toolResultPath) ++ filesToLint

      CommandRunner.exec(command) match {
        case Right(resultFromTool) if resultFromTool.exitCode <= 1 =>
          val resultFromToolJson = Source.fromFile(toolResultPath).getLines().mkString
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
