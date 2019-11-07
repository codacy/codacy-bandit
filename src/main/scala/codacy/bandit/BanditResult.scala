package codacy.bandit

import com.codacy.plugins.api.results.Result.{FileError, Issue}
import com.codacy.plugins.api.results.{Pattern, Result}
import com.codacy.plugins.api.{ErrorMessage, Source}
import play.api.libs.json.{JsValue, Json, OFormat}

case class BanditFileError(filename: String, reason: String) {
  lazy val fileError =
    FileError(Source.File(filename), Some(ErrorMessage(reason)))
}

case class BanditWarning(
    test_id: String,
    filename: String,
    issue_text: String,
    line_number: Int,
    code: JsValue,
    issue_confidence: JsValue,
    issue_severity: JsValue,
    line_range: JsValue,
    test_name: JsValue
) {
  lazy val issue =
    Issue(Source.File(filename), Result.Message(issue_text), Pattern.Id(test_id), Source.Line(line_number))
}

object BanditFileError {
  implicit val BanditFileErrorFmt: OFormat[BanditFileError] =
    Json.format[BanditFileError]
}

object BanditWarning {
  implicit val BanditFileErrorFmt: OFormat[BanditWarning] =
    Json.format[BanditWarning]
}
