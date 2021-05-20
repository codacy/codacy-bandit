package docs.transformers

import better.files._
import com.codacy.plugins.api.results.Pattern

/** Get a list of patterns from an html file */
trait IPatternDocTransformer {
  def getPatterns(originalDocsDir: File): Seq[(Pattern.Specification, Pattern.Description, String)]
}
