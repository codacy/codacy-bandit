package docs.transformers.utils

import better.files._
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl

import scala.sys.process._
import scala.xml.Elem
import scala.xml.parsing.NoBindingFactoryAdapter

object Pandoc {

  /** Build pandoc command */
  private def command(): String = {
    val output_type = "markdown_mmd"
    val output_options =
      "-native_divs-native_spans-fenced_divs-bracketed_spans-mmd_link_attributes+escaped_line_breaks"

    val sb = new StringBuilder()
    sb.append("pandoc")
    sb.append(" ")
    sb.append("-f")
    sb.append(" ")
    sb.append("html")
    sb.append(" ")
    sb.append("-t")
    sb.append(" ")
    sb.append(output_type)
    sb.append(output_options)
    sb.result()
  }

  /** Run pandoc */
  private def runCommand(cmd: String, htmlString: String): String = {
    Seq("echo", htmlString) #> cmd !!
  }

  /** Get the elements of an html file */
  def loadHtml(file: File): Elem = {
    new scala.xml.factory.XMLLoader[scala.xml.Elem] {
      override def parser = (new SAXFactoryImpl).newSAXParser

      override def adapter = new NoBindingFactoryAdapter
    }.loadFile(file.path.toAbsolutePath.toString)
  }

  /** Convert an html with pandoc */
  def convert(htmlString: String): Option[String] = {
    val cmd = command
    val output = runCommand(cmd, htmlString)
    Some(output).filter(_.nonEmpty)
  }

}
