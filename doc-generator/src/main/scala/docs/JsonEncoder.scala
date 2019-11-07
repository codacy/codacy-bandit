package docs

import docs.model._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe._

object JsonEncoder {

  /** Used to generate the
    * "resources/docs/patterns.json" file.
    *
    * Gets a list of Patterns, returns the encoded json string.
    */
  def patternsJsonEncoder(version: String, patterns: Seq[Pattern]): String = {
    case class PatternFile(name: String, version: String, patterns: Seq[Pattern])
    implicit val encodePattern: Encoder[Pattern] =
      Encoder.forProduct3("patternId", "level", "category")(u => (u.patternId, u.level.toString, u.category.toString))
    PatternFile("Bandit", version, patterns).asJson.toString()
  }

  /** Used to generate the
    * "resources/docs/description/description.json" file.
    *
    * Gets a list of Patterns, returns the encoded json string.
    */
  def descriptionJsonEncoder(patterns: Seq[Pattern]): String = {
    implicit val encodePattern: Encoder[Pattern] =
      Encoder.forProduct2("patternId", "title")(u => (u.patternId, u.title))
    patterns.asJson.toString()
  }
}
