package docs.transformers.utils

import better.files._
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl

import scala.sys.process._
import scala.xml.Elem
import scala.xml.parsing.NoBindingFactoryAdapter
import scala.language.postfixOps

object Pandoc {

  /** Get the elements of an html file */
  def loadHtml(file: File): Elem = {
    new scala.xml.factory.XMLLoader[scala.xml.Elem] {
      override def parser = (new SAXFactoryImpl).newSAXParser

      override def adapter = new NoBindingFactoryAdapter
    }.loadFile(file.path.toAbsolutePath.toString)
  }

  /** Convert an html with pandoc */
  def convert(htmlString: String): String = {
    val input = new java.io.ByteArrayInputStream(htmlString.getBytes("UTF-8"))
    val result: String =
      (Seq("pandoc", "-f", "html-native_divs-native_spans", "-t", "markdown") #< input).!!
    result // Some(result).filter(_.nonEmpty)
  }

}
