import csstype.AlignItems
import csstype.Color
import csstype.Display
import csstype.FontWeight
import csstype.JustifyContent
import csstype.TextAlign
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML
external interface FestivalRankingBubbleProps: Props {
    var festival: ClientMusicFestival
}
val FestivalRankingBubble = FC<FestivalRankingBubbleProps> { props ->
    ReactHTML.div {
        css {
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            height = 50.px
            width = 50.px
            borderRadius = 50.pct
            fontWeight = FontWeight.bold
            backgroundColor = props.festival.quartileColor
            fontSize = 25.px
            textAlign = TextAlign.center
            color = Color("#292929")
        }
        +props.festival.rank.toString()
    }
}