import ActionState.*
import csstype.AlignItems
import csstype.AnimationDuration
import csstype.Color
import csstype.Cursor
import csstype.Display
import csstype.Flex
import csstype.FlexDirection
import csstype.FontWeight
import csstype.Globals
import csstype.Globals.inherit
import csstype.Globals.unset
import csstype.JustifyContent
import csstype.Length
import csstype.NamedColor
import csstype.px
import csstype.s
import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.useContext


val LoginWithSpotifyButton = FC<Props> {
    val navigateTo = useContext(RouterContext)
    button {
        css {
            backgroundColor = Color("#1DB954")
            borderRadius = 15.px
            padding = 8.px
            border = unset
            color = NamedColor.white
            Animations.glow(this, color = Color("#1DB954"), intensity = 10.px)
        }
        onClick = { navigateTo(Page.USER_LAND) }
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                minWidth = 150.px
            }
            img {
                src = "white-spotify-icon.png"
                height = 30.0
            }
            div {
                css {
                    fontSize = 17.px
                    fontWeight = FontWeight.bold
                    marginLeft = 8.px
                }
                +"Get Started"
            }
        }
    }
}
