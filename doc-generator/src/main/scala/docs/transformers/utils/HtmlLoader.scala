package docs.transformers.utils

import better.files.File
import scala.xml.Elem
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import scala.xml.parsing.NoBindingFactoryAdapter

object HtmlLoader {

  /** Get the elements of an html file */
  def loadHtml(file: File): Elem = {
    new scala.xml.factory.XMLLoader[scala.xml.Elem] {
      override def parser = (new SAXFactoryImpl).newSAXParser

      override def adapter = new NoBindingFactoryAdapter
    }.loadFile(file.path.toAbsolutePath.toString)
  }
}
