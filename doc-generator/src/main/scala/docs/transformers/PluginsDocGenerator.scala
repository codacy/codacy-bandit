package docs.transformers

import docs.transformers.utils.Pandoc
import docs.model._
import scala.xml.{Node, NodeSeq}
import better.files._
import com.codacy.plugins.api.results.Pattern.Category
import com.codacy.plugins.api.results.Result.Level

object PluginsDocTransformer extends IPatternDocTransformer {

  /** Strips pattern ids and title from the <title> tag. */
  private def stripPluginsTitle(head: NodeSeq): Option[(String, String)] = {
    val htmlTitle = (head \\ "title").text
    val patternIdRegex = "(B[\\d]{3}).*".r
    val titleRegex = "B[\\d]{3}: (.*)".r
    for {
      patternId <- htmlTitle match {
        case patternIdRegex(c) => Some(c)
        case _ => None
      }
      bulk_title <- htmlTitle match {
        case titleRegex(c) => Some(c)
        case _ => None
      }
      title = bulk_title.replace(" — Bandit  documentation", "")
    } yield (patternId, title)
  }

  /** Find the html object with the details of the pattern.
    * Usually in <body><dd> or <body><div id="b000">
    */
  private def getBody(htmlPluginsDocs: Node, patternId: String) = {
    val dd = htmlPluginsDocs \\ "dd"
    val articleBody = for {
      divs <- htmlPluginsDocs \\ "div"
      if (divs \@ "id").startsWith(patternId.toLowerCase())
      divsChildren <- divs.child.filter { node =>
        val l = node.label
        l == "h1" || l == "h2" || l == "p"
      }
    } yield divsChildren

    if (dd.nonEmpty && dd.text.contains(patternId)) dd else NodeSeq.fromSeq(articleBody)
  }

  /** Get all Patterns on the html files
    * of the "bandit/doc/build/plugins/" docs directory
    */
  def getPatterns(originalDocsDir: File) = {
    val sourceDirectory = originalDocsDir / "plugins"
    for {
      htmlFiles <- sourceDirectory.listRecursively.toSeq
      htmlPluginsDocs <- Pandoc.loadHtml(htmlFiles)
      head <- htmlPluginsDocs \\ "head"
      patternIdWithTitle <- stripPluginsTitle(head)
      (patternId, title) = patternIdWithTitle
      body = getBody(htmlPluginsDocs, patternId)
      descriptionText = Pandoc.convert(body.toString())
    } yield Pattern(patternId.capitalize, title, descriptionText, Level.Warn, Category.Security)
  }
}
