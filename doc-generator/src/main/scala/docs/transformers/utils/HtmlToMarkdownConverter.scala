package docs.transformers.utils

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter

object HtmlToMarkdownConverter {

  def convert(htmlString: String): String =
    FlexmarkHtmlConverter.builder().build().convert(htmlString).replaceAll("<br />", "")
}
