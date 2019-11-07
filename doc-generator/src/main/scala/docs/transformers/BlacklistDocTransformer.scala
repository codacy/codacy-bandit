package docs.transformers

import docs.transformers.utils.Pandoc
import docs.model._
import scala.xml.Node
import better.files._
import com.codacy.plugins.api.results.Pattern.Category
import com.codacy.plugins.api.results.Result.Level

object BlacklistDocTransformer extends IPatternDocTransformer {
  val patternIdIntervalRegex = "(b[\\d]{3}-b[\\d]{3}).*".r
  val patternIdRegex = "(b[\\d]{3}).*".r

  /**
    * Find the body for each pattern on a given html doc.
    * Usually there is more than 1 pattern in a blacklist docs.
    */
  private def patternsDocumentationBody(htmlPluginsDocs: Node) = {
    for {
      div <- htmlPluginsDocs \\ "body" \\ "div"
      patternId = (div \ "@id").text
      if patternIdRegex.matches(patternId)
    } yield div
  }

  /**
    * All the pattern ids from a given html node.
    * There are usually more than one in a given doc.
    *
    * Example:
    * <div class="section" id="b304-b305-ciphers-and-modes">
    */
  private def patternIds(body: Node): Seq[String] = {
    (body \ "@id").text match {
      case patternIdIntervalRegex(c) =>
        val Array(firstPatternIdStr, lastPatternIdStr) = c.replace("b", "").split("-")
        val pIds = firstPatternIdStr.toInt.to(lastPatternIdStr.toInt)
        pIds.map("b" + _.toString)
      case patternIdRegex(c) => Seq(c)
      case _ => Seq.empty
    }
  }

  /** Get the pattern title from the first paragraph*/
  private def getTitle(body: Node, patternId: String) = {
    val isB313B320 = 313.to(320).contains(patternId.tail.toInt)
    val pNodes = (body \ "p")
    val node = if (isB313B320) pNodes(1) else pNodes(0)
    val bulk_title = node.text.replace("\n", " ")
    bulk_title.split("\\. ").head
  }

  /** Get all Patterns on the html files
    * of the "bandit/doc/build/blacklists/" docs directory
    */
  def getPatterns(originalDocsDir: File) = {
    val sourceDirectory = originalDocsDir / "blacklists"
    for {
      htmlFiles <- sourceDirectory.listRecursively.toSeq
      htmlPluginsDocs <- Pandoc.loadHtml(htmlFiles)
      body <- patternsDocumentationBody(htmlPluginsDocs)
      descriptionText = Pandoc.convert(body.toString())
      patternId <- patternIds(body)
      title = getTitle(body, patternId)
    } yield Pattern(patternId.capitalize, title, descriptionText, Level.Warn, Category.Security)
  }
}
