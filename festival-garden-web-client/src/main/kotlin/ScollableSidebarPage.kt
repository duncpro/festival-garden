import csstype.Length
import csstype.Overflow
import csstype.pct
import csstype.px
import emotion.react.css
import org.w3c.dom.HTMLDivElement
import react.FC
import react.MutableRefObject
import react.PropsWithChildren
import react.dom.html.ReactHTML.div
import react.useRef
import react.useEffect

external interface ScrollableSidebarPageProps: PropsWithChildren {
    var scrollStateRef: MutableRefObject<Double>?
    var resetScrollPositionWhenChanged: Array<Any?>?
}

private object PreventReRenderCallPlaceholder
val ScrollableSidebarPage = FC<ScrollableSidebarPageProps> { props ->
    val scrollRef = useMemorizedScrollPosition<HTMLDivElement>(props.scrollStateRef)
    val elementRef = useRef<HTMLDivElement>()

    val effectArgs = (props.resetScrollPositionWhenChanged ?: arrayOf(PreventReRenderCallPlaceholder))
    useEffect(*effectArgs) {
        // Do not reset scroll position when component is mounted.
        if (effectArgs.size == 1 && effectArgs[0] == PreventReRenderCallPlaceholder) return@useEffect
        elementRef.current?.scrollTo(0.0, 0.0)
    }

    div {
        ref = MergeRefCallback(scrollRef, elementRef.asRefCallback())
        css {
            height = "calc(100% - 30px)".unsafeCast<Length>()
            width = "calc(100% - 30px)".unsafeCast<Length>()
            padding = 15.px
            overflow = Overflow.scroll
        }
        +props.children
    }
}