import csstype.Color
import csstype.FontFamily
import csstype.FontWeight
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML

external interface FestivalGardenLogoProps: Props {
    var color: Color?
}

val FestivalGardenLogo = FC<FestivalGardenLogoProps> { props ->
    ReactHTML.div {
        css {
            fontFace {
                fontFamily = "omegle"
                src = "url('omegle.ttf')"
            }
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS", "CAST_NEVER_SUCCEEDS")
            fontFamily = "omegle" as FontFamily
            color = props.color ?: Color("#FC466B")
            fontSize = 45.px
            fontWeight = FontWeight.normal
        }
        +"Festival Garden"
    }
}