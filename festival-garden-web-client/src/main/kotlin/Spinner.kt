import csstype.Animation
import csstype.Border
import csstype.Color
import csstype.Length
import csstype.LineStyle
import csstype.deg
import csstype.pct
import csstype.rotate
import emotion.css.keyframes
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div

external interface SpinnerProps: Props {
    var size: Length
    var thickness: Length
    var foreground: Color
    var background: Color
    var speed: csstype.AnimationDuration
}

val Spinner = FC<SpinnerProps> { props ->
    div {
        css {
            val spin = keyframes {
                from { transform = rotate(0.deg) }
                to { transform = rotate(360.deg) }
            }
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
            animation = "$spin ${props.speed} linear infinite" as Animation

            width = props.size
            height = props.size
            border = Border(props.thickness, LineStyle.solid, props.background)
            borderTop = Border(props.thickness, LineStyle.solid, props.foreground)
            borderRadius = 50.pct
        }
    }
}