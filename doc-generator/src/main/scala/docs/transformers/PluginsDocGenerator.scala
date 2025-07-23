package docs.transformers

import docs.transformers.utils.HtmlLoader

import scala.xml._
import better.files._
import com.codacy.plugins.api.results.Pattern.{Category, Scantype}
import com.codacy.plugins.api.results.Result.Level
import com.codacy.plugins.api.results.Pattern
import docs.{DefaultPatterns, SecuritySubcategories}

object PluginsDocTransformer extends IPatternDocTransformer {

  /** Strips pattern ids and title from the <title> tag. */
  private def stripPluginsTitle(head: NodeSeq): Option[(Pattern.Id, Pattern.Title)] = {
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
    } yield (Pattern.Id(patternId), Pattern.Title(title))
  }

  private def stripSeverity(head: NodeSeq): Level.Value = {
    val text = (for {
      highlightDivs <- head \\ "div" if (highlightDivs \@ "class").startsWith("highlight")
      node <- highlightDivs \\ "pre" if node.text.contains("Severity: ")
    } yield node.text).headOption

    val severityRegex = "(\n|.)*Severity: (High|Medium|Low)(\n|.)*".r
    text.getOrElse("") match {
      case severityRegex(_, "High", _) => Level.Err
      case severityRegex(_, "Medium" | "Low", _) => Level.Warn
      case _ => Level.Warn
    }
  }

  /** Find the html object with the details of the pattern.
    * Usually in <body><dd> or <body><div id="b000">
    */
  private def getBody(htmlPluginsDocs: Node, patternId: Pattern.Id): NodeSeq = {
    val dd = htmlPluginsDocs
    val articleBody = for {
      divs <- htmlPluginsDocs
      if (divs \@ "id").startsWith(patternId.value.toLowerCase())
      divsChildren <- divs.child.filter { node =>
        val l = node.label
        l == "h1" || l == "h2" || l == "p"
      }
    } yield divsChildren

    if (dd.nonEmpty && dd.text.contains(patternId.value)) dd else NodeSeq.fromSeq(articleBody)
  }

  /** Get all Patterns on the html files
    * of the "bandit/doc/build/plugins/" docs directory
    */
  def getPatterns(originalDocsDir: File) = {
    val sourceDirectory = originalDocsDir / "plugins"
    for {
      htmlFiles <- sourceDirectory.listRecursively.toSeq
      htmlPluginsDocs <- HtmlLoader.loadHtml(htmlFiles)
      head <- htmlPluginsDocs
      (patternId, title) <- stripPluginsTitle(head)
      patternIdCapitalized = Pattern.Id(patternId.value.capitalize)
      body = getBody(htmlPluginsDocs, patternId)
      descriptionText = Some(Pattern.DescriptionText(body.head.text))
      html = body.toString
      severity = stripSeverity(htmlPluginsDocs)
      specification = Pattern.Specification(
        patternIdCapitalized,
        severity,
        Category.Security,
        SecuritySubcategories.get(patternIdCapitalized),
        Some(ScanType.SAST),
        Set.empty,
        Set.empty,
        enabled = DefaultPatterns.list.contains(patternIdCapitalized.value)
      )
      description = Pattern.Description(patternIdCapitalized, title, descriptionText, None, Set.empty)
    } yield (specification, description, html)
  }
}
