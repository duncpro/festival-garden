import csstype.AlignItems
import csstype.Color
import csstype.Cursor
import csstype.Display
import csstype.FlexDirection
import csstype.FontWeight
import csstype.LinearColorStop
import csstype.None
import csstype.deg
import csstype.linearGradient
import csstype.pct
import csstype.px
import csstype.stop
import emotion.css.css
import emotion.react.css
import kotlinx.browser.window
import react.FC
import react.Props
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img

external interface ViewOnFestivalWizardProps: Props {
    var url: String
}

val ViewOnFestivalWizardButton = FC<ViewOnFestivalWizardProps> { props ->

    button {
        onClick = { window.open(props.url) }
        css {
            @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            cursor = "pointer" as Cursor
            borderRadius = 8.px
            fontWeight = FontWeight.bold
            height = 25.px
            border = None.none
            background = linearGradient(90.deg, stop(Color("#FC466B"), 0.pct), stop(Color("#3F5EFB"), 100.pct))
        }
        div {
            css {
                height = 100.pct
                display = Display.flex
                alignItems = AlignItems.center
                flexDirection = FlexDirection.row
            }
            img {
                src = "festival-wizard-logo.png"
                css {
                    height = 20.px
                    width = 20.px
                    marginRight = 5.px
                }
            }
            +"Tickets & Travel"
        }
    }
}