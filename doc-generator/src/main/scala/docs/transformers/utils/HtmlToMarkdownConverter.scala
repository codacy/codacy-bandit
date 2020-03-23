package docs.transformers.utils

import scala.sys.process._

object HtmlToMarkdownConverter {

  def convert(htmlString: String): String = {
    val input = new java.io.ByteArrayInputStream(htmlString.getBytes("UTF-8"))
    val result: String =
      (Seq("pandoc", "-f", "html-native_divs-native_spans", "-t", "commonmark") #< input).!!
    result
  }
}
