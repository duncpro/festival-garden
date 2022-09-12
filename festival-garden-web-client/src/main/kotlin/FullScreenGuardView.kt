import csstype.AlignItems
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.Overflow
import csstype.TextAlign
import csstype.pct
import csstype.px
import csstype.rgb
import csstype.rgba
import csstype.s
import emotion.react.css
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div

sealed interface GuardViewState {
    data class Failed(val error: Throwable): GuardViewState
    object Loading: GuardViewState
    object Pass: GuardViewState
}

external interface FullScreenGuardViewProps: PropsWithChildren {
    var state: GuardViewState
}

val Guard = FC<FullScreenGuardViewProps> { props ->
    if (props.state == GuardViewState.Pass) {
        +props.children
        return@FC
    }

    div {
        css {
            this.width = 100.pct
            this.height = 100.pct
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            color = Color("#FC466B")
        }
        FestivalGardenLogo {}
        ReactHTML.div { css { this.height = 50.px } }
        when (props.state) {
            is GuardViewState.Failed -> {
                ReactHTML.div {
                    css {
                        maxWidth = 500.px
                        padding = 10.px
                        textAlign = TextAlign.center
                    }
                    +"Something has gone wrong with Festival Garden. "
                    +"Try refreshing the page, or moving somewhere with a better connection. "
                    +"If that doesn't work please send an email to me! "
                    +"You can reach me at "
                    ReactHTML.a {
                        href = "mailto:duncpro@icloud.com"
                        +"duncpro@icloud.com"
                    }
                }
                ReactHTML.div {
                    css {
                        width = 90.pct
                        overflow = Overflow.scroll
                        padding = 5.px
                    }
                    ReactHTML.pre {
                        +(props.state as GuardViewState.Failed).error.stackTraceToString()
                    }
                }
            }
            GuardViewState.Loading -> Spinner {
                foreground = rgb(252, 70, 107)
                background = rgba(252, 70, 107, 0.5)
                speed = 1.s
                size = 50.px
                thickness = 7.px
            }
            GuardViewState.Pass -> throw AssertionError()
        }
    }
}