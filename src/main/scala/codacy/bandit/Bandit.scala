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
      val excludedFiles = Set("Pipfile.lock", "requirements.txt", "poetry.lock", "uv.lock")
      val filesToLint: List[String] = files.fold(List(source.path.toString)) { paths =>
        paths
          .map(_.toString)
          .filterNot(path => excludedFiles.exists(excluded => path.endsWith(excluded)))
          .toList
      }

      lazy val enabledPatterns = fullConfig.map(_.map(_.patternId).to(Set))

      runTool(source, "python3", filesToLint, enabledPatterns)
    }
  }
  // https://bandit.readthedocs.io/en/latest/config.html
  private lazy val nativeIniFileNames = Set(".bandit", "bandit.ini")
  private lazy val nativeConfigFileNames = Set("bandit.yml", "bandit.yaml", "bandit.toml", "pyproject.toml")

  private def runTool(
      rootPath: Source.Directory,
      pythonEngine: String,
      filesToLint: List[String],
      enabledPatterns: Option[Set[Pattern.Id]]
  ): List[Result] = {
    lazy val nativeIniFile = nativeIniFileNames
      .map(filename => Try(better.files.File(rootPath.path) / filename))
      .collectFirst {
        case Success(file) if file.isRegularFile => file.toJava.getAbsolutePath
      }
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
          if (!nativeIniFile.isEmpty) {
            nativeIniFile.to(List).flatMap(iniFile => List("--ini", iniFile))
          } else {
            nativeConfigFile.to(List).flatMap(cfgFile => List("-c", cfgFile))
          }
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
      case Issue(file, _, id, _, _) =>
        patternIds.forall(_.contains(id)) && !(id == assertUsedPatternId && isTestFile(file.path))
      case _ =>
        true
    }
  }

  // B101 (assert_used) is expected in tests, so it is never reported for test files
  private val assertUsedPatternId = Pattern.Id("B101")
  // Python test conventions: pytest (test_*.py, *_test.py, conftest.py), unittest/Django (test.py, tests.py)
  // and anything under a test directory
  private val testDirNames = Set("test", "tests", "testing")
  private val testFileNames = Set("conftest.py", "test.py", "tests.py")

  private def isTestFile(path: String): Boolean = {
    val segments = path.replace('\\', '/').split('/').toList
    val fileName = segments.lastOption.getOrElse("")
    testFileNames.contains(fileName) || fileName.startsWith("test_") || fileName.endsWith("_test.py") ||
    fileName.endsWith("_tests.py") || segments.init.exists(testDirNames.contains)
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
