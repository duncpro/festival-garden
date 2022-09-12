import csstype.Color
import csstype.NamedColor
import csstype.rgba

val ClientMusicFestival.quartileColor: Color get() = when (this.quartile) {
    0 -> NamedColor.gold
    1 -> NamedColor.silver
    2 -> Color("#574327")
    3 -> rgba(255, 255, 255, .2)
    else -> throw IllegalStateException("Expected quartile in range: (0, 3] but was ${this.quartile}")
}