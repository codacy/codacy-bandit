package docs.model

sealed trait Level

sealed trait Category

object Level {

  case object Warning extends Level

  case object Info extends Level

  case object Error extends Level

}

object Category {

  case object ErrorProne extends Category

  case object CodeStyle extends Category

  case object UnusedCode extends Category

  case object Security extends Category

  case object Compatibility extends Category

  case object Performance extends Category

  case object Documentation extends Category

}

case class Pattern(patternId: String,
                   title: String,
                   descriptionText: String,
                   level: Level,
                   category: Category) {
  override def toString: String = s"$patternId - $title"
}
