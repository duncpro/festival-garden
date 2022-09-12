import Page.*
import csstype.FontFamily
import csstype.FontWeight
import csstype.HtmlAttributes
import csstype.Overflow
import csstype.px
import csstype.vw
import emotion.react.css
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.defaultRequest
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.EventType
import org.w3c.dom.events.addEventHandler
import react.FC
import react.Fragment
import react.create
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import react.useEffectOnce
import react.useState
import react.Props
import react.PropsWithChildren
import react.useContext
import react.useMemo

val RootStyleWrapper = FC<PropsWithChildren> { props ->
    var height by useState<Int>(window.innerHeight)
    useEffectOnce {
        val unregister = window.addEventHandler(EventType("resize")) {
            height = window.innerHeight
        }
        cleanup { unregister() }
    }

    div {
        css {
            fontFamily = FontFamily.sansSerif
            fontWeight = FontWeight.bold
            this.height = height.px
            this.width = 100.vw
            overflow = Overflow.scroll
        }
        +props.children
    }
}

val EntrypointComponent = FC<Props> {
    RootStyleWrapper {
        AssetPreloadGuard {
            Router {}
        }
    }
}

fun main() {
    val rootDiv = document.getElementById("root") ?: error("Couldn't find root container!")
    val reactRoot = createRoot(rootDiv)
    console.log("Using Festival Garden Backend: ${js("bundle.deployment.config.backendUrl")}")

    startTweenAnimationLoop()


    reactRoot.render(Fragment.create { EntrypointComponent() })
}
