import DisplayMode.*
import DrawerState.CLOSED
import DrawerState.OPEN
import csstype.BorderRadius
import csstype.BoxShadow
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.Length
import csstype.NamedColor
import csstype.None
import csstype.Position
import csstype.ZIndex
import csstype.pct
import csstype.px
import csstype.rgba
import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.EventType
import org.w3c.dom.events.addEventHandler
import org.w3c.dom.get
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML.`object`
import react.dom.html.ReactHTML.div
import react.useContext
import react.useEffectOnce
import react.useRef
import react.useState
import kotlin.math.min
import react.useEffect

external interface SidebarFestivalRowProps: Props {
    var festival: ClientMusicFestival
}

enum class DrawerState { OPEN, CLOSED }

val Sidebar = FC<PropsWithChildren> { props ->
    val displayMode = useDisplayMode()

    fun calcDrawerOffsetMax(): Double {
        return window.innerHeight - 20.0 /* d top of page */ - 40 /* height of handle */
    }
    var drawerOffsetMax by useState<Double>(calcDrawerOffsetMax())
    var drawerOffset by useState<Double>(calcDrawerOffsetMax())
    val drawerTween = useRef<TweenModule.Tween<Vector1>>(null)

    fun animateDrawer(targetState: DrawerState) {
        val prev = drawerTween.current
        prev?.stop()
        drawerTween.current = createTween(
            initial = Vector1(drawerOffset),
            final = Vector1(when (targetState) {
                OPEN -> 0.0
                CLOSED -> drawerOffsetMax
            }),
            duration = 600,
            easing = TweenModule.Easing.Quadratic.Out,
            onUpdate = { newOffset -> drawerOffset = newOffset.n }
        ).start()
    }
    fun toggleDrawer() {
        val targetState = if (drawerOffset > (drawerOffsetMax / 2)) OPEN else CLOSED
        animateDrawer(targetState)
    }

    useAsyncEffectOnce {
        delay(500)
        animateDrawer(DrawerState.OPEN)
    }

    useEffectOnce {
        val unregister = window.addEventHandler(EventType("resize")) {
            drawerOffsetMax = calcDrawerOffsetMax()
        }
        cleanup { unregister() }
    }

    val originalTouchYRef = useRef<Int>()
    val beforeTouchDrawerOffset = useRef<Double>()
    fun finishDrawerMovement() {
        if (beforeTouchDrawerOffset.current!! - drawerOffset < 0) {
            animateDrawer(CLOSED)
        }
        if (beforeTouchDrawerOffset.current!! - drawerOffset > 0) {
            animateDrawer(OPEN)
        }
    }


    div {
        css {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
            zIndex = 99999 as ZIndex
            position = Position.fixed
            top = (20 + min(drawerOffset, drawerOffsetMax)).px
            bottom = (20 + (min(drawerOffset, drawerOffsetMax) * -1)).px


            when (displayMode) {
                NORMAL -> {
                    left = 20.px
                    width = 400.px
                }
                SMALL -> {
                    width = 90.pct
                    left = 5.pct
                    right = 5.pct
                }
            }
        }

        // Drawer Handle
        div {
            onTouchStart = { event ->
                beforeTouchDrawerOffset.current = drawerOffset
                originalTouchYRef.current = event.touches[0]!!.clientY
            }
            onTouchMove = eventHandler@{ event ->
                val touchDelta = originalTouchYRef.current!! - event.touches[0]!!.clientY
                drawerOffset = (beforeTouchDrawerOffset.current!! - touchDelta)
            }
            onTouchEnd = { _ -> finishDrawerMovement() }
            onClick = { toggleDrawer() }
            css {
                @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
                borderRadius = "10px 10px 0 0" as Length
                width = 100.pct
                backgroundColor = rgba(41, 41, 41, 1.0)
                display = Display.flex
                justifyContent = JustifyContent.center
                flexDirection = FlexDirection.row
                boxShadow = BoxShadow(0.px, 2.px, 9.px, NamedColor.black)
                height = 40.px
            }
            `object` {
                css {
                    pointerEvents = None.none
                    width = 40.px
                    color = Color.currentcolor
                }
                type = "image/svg+xml"
                data = "handle.svg"
            }
        }

        div {
            css {
                @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                height = "calc(100% - 40px)" as Length
                width = 100.pct
                backgroundColor = rgba(0, 0, 0, 0.65)
                @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                borderRadius = "0 0px 10px 10px" as BorderRadius
            }
            +props.children
        }
    }
}