package codacy.bandit

import java.nio.file.Path

import codacy.dockerApi._
import codacy.dockerApi.utils.{CommandRunner, FileHelper, ToolHelper}
import play.api.libs.json.Json

import scala.io.Source
import scala.util.{Success, Try}

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

      lazy val enabledPatterns = fullConfig.map(_.map(_.patternId).to[Set])

      val filesByVersion = partitionFilesByPythonVersion(filesToLint)

      runTool(path, "python", filesByVersion.python2, enabledPatterns) ++
        runTool(path, "python3", filesByVersion.python3, enabledPatterns)
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

  private lazy val nativeConfigFileNames = Set("bandit.yml", ".bandit")

  private def runTool(rootPath:Path, pythonEngine: String, filesToLint: List[String], enabledPatterns: Option[Set[PatternId]]): List[Result] = {
    lazy val nativeConfigFile = nativeConfigFileNames.map( filename => Try(new better.files.File(rootPath) / filename) )
      .collectFirst{ case Success(file) if file.isRegularFile => file.toJava.getAbsolutePath }

    if (filesToLint.isEmpty) {
      List.empty[Result]
    }
    else {
      val toolResultPath = FileHelper.createTmpFile("", "tool-out-", ".json").toString
      val nativeConfigParams:List[String] =
        if(enabledPatterns.isEmpty) nativeConfigFile.to[List].flatMap( cfgFile => List("-c", cfgFile))
        else List.empty
      val command = List(pythonEngine, "-m", "bandit", "-f", "json", "-o", toolResultPath) ++ nativeConfigParams ++ filesToLint

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

  private def resultFilter(result: Result, patternIds: Option[Set[PatternId]]): Boolean = {
    result match {
      case Issue(_, _, id, _) =>
        patternIds.forall(_.contains(id))
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
