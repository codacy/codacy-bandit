package docs.transformers

import docs.model._
import better.files._

/** Get a list of patterns from an html file */
trait IPatternDocTransformer {
  def getPatterns(originalDocsDir: File): Seq[Pattern]
}
