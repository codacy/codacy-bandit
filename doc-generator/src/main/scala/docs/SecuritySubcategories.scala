package docs

import com.codacy.plugins.api.results.Pattern
import com.codacy.plugins.api.results.Pattern.Subcategory
import com.codacy.plugins.api.results.Pattern.Subcategory._

object SecuritySubcategories {

  def get(patternId: Pattern.Id): Option[Subcategory.Value] = patternId.value match {
    case "B702" | "B701" | "B308" => Some(XSS)
    case "B307" | "B506" | "B319" | "B320" | "B313" | "B609" | "B318" | "B316" | "B317" | "B102" | "B314" | "B315" =>
      Some(InputValidation)
    case "B108" | "B103" => Some(FileAccess)
    case "B310" => Some(HTTP)
    case "B409" | "B410" | "B405" | "B301" | "B321" | "B312" | "B407" | "B402" | "B411" | "B302" | "B406" | "B412" |
        "B408" | "B401" | "B403" | "B404" =>
      Some(InsecureModulesLibraries)
    case "B311" | "B505" | "B303" | "B304" | "B305" => Some(Cryptography)
    case "B606" | "B602" | "B603" | "B604" | "B605" | "B607" | "B601" => Some(CommandInjection)
    case "B107" | "B106" | "B111" | "B109" | "B105" => Some(Auth)
    case "B608" => Some(SQLInjection)
    case "B501" | "B503" | "B309" | "B502" | "B504" => Some(SSL)
    case _ => None
  }
}
