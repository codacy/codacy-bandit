package docs

import better.files.Dsl.SymbolicOperations
import better.files._
import docs.transformers._
import docs.model._

object JsonEncoder {

  /** Used to generate the
    * "resources/docs/patterns.json" file.
    *
    * Gets a list of Patterns, returns the encoded json string.
    */
  def patternsJsonEncoder(version: String, patterns: Seq[Pattern]): String = {
    import io.circe._, io.circe.generic.auto._, io.circe.syntax._
    case class PatternFile(name: String,
                           version: String,
                           patterns: Seq[Pattern])
    implicit val encodePattern: Encoder[Pattern] =
      Encoder.forProduct3("patternId", "level", "category")(u =>
        (u.patternId, u.level.toString, u.category.toString))
    PatternFile("Bandit", version, patterns).asJson.toString()
  }

  /** Used to generate the
    * "resources/docs/description/description.json" file.
    *
    * Gets a list of Patterns, returns the encoded json string.
    */
  def descriptionJsonEncoder(patterns: Seq[Pattern]): String = {
    import io.circe._, io.circe.generic.auto._, io.circe.syntax._
    implicit val encodePattern: Encoder[Pattern] =
      Encoder.forProduct2("patternId", "title")(u => (u.patternId, u.title))
    patterns.asJson.toString()
  }
}
