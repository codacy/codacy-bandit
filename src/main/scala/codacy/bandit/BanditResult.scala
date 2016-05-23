package codacy.bandit

import codacy.dockerApi._
import play.api.libs.json.{JsValue, Json}

case class BanditFileError(filename: String, reason: String) {
  lazy val fileError =
    FileError(SourcePath(filename), Some(ErrorMessage(reason)))
}

case class BanditWarning(test_id: String, filename: String, issue_text: String, line_number: Int, code: JsValue,
                         issue_confidence: JsValue, issue_severity: JsValue, line_range: JsValue, test_name: JsValue) {
  lazy val issue =
    Issue(
      SourcePath(filename),
      ResultMessage(issue_text),
      PatternId(test_id),
      ResultLine(line_number)
    )
}

object BanditFileError {
  implicit val BanditFileErrorFmt = Json.format[BanditFileError]
}

object BanditWarning {
  implicit val BanditFileErrorFmt = Json.format[BanditWarning]
}