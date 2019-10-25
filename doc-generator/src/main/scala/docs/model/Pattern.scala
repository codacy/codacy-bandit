package docs.model

import com.codacy.plugins.api.results.Pattern.Category
import com.codacy.plugins.api.results.Result.Level

case class Pattern(patternId: String,
                   title: String,
                   descriptionText: String,
                   level: Level,
                   category: Category) {
  override def toString: String = s"$patternId - $title"
}
