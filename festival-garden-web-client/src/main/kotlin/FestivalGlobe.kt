import Animations.glow
import csstype.Auto.auto
import csstype.BackgroundSize
import csstype.NamedColor
import csstype.Visibility
import csstype.pct
import csstype.px
import csstype.rgba
import csstype.url
import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.Job
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.POINTER_DOWN
import org.w3c.dom.events.POINTER_UP
import org.w3c.dom.events.WHEEL
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.events.WheelEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit
import react.FC
import react.Fragment
import react.Props
import react.RefObject
import react.StateInstance
import react.create
import react.createContext
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG
import react.useContext
import react.useMemo
import react.useState
import react.useEffect
import react.useRef

sealed interface FestivalSelection
object NoSelection: FestivalSelection
data class FestivalSelected(val festival: ClientMusicFestival, val animateGlobe: Boolean): FestivalSelection

external interface FestivalGlobePinProps: Props {
    var festival: ClientMusicFestival
    var globeDOMRefs: RefObject<GlobeDOMBundle>
}

external interface GlobePinSVGProps: Props {
    var isGlowing: Boolean
}

val GlobePinSVG = FC<GlobePinSVGProps> { props ->
    div {
        ReactSVG.svg {
            css {
                width = 20.px
                height = 25.px
                if (props.isGlowing) { glow(this) }
            }
            viewBox = "-4 0 36 36"
            ReactSVG.path {
                fill = "currentColor"
                d =
                    "M14,0 C21.732,0 28,5.641 28,12.6 C28,23.963 14,36 14,36 C14,36 0,24.064 0,12.6 C0,5.641 6.268,0 14,0 Z"
            }
            ReactSVG.circle {
                cx = 14.0
                cy = 14.0
                r = 7.0
                fill = "black"
            }
        }
    }
}

data class FestivalGlobeContextValue(
    val hoveredFestival: StateInstance<ClientMusicFestival?>? = null,
    val onSelectFestival: (ClientMusicFestival) -> Unit = {},
    val selection: FestivalSelection = NoSelection
)
val FestivalGlobeContext = createContext(FestivalGlobeContextValue())

val FestivalGlobePin = FC<FestivalGlobePinProps> { props ->
    val globeContext = useContext(FestivalGlobeContext)
    var tooltipVisibility by useState<Visibility>(Visibility.hidden)
    val tooltip = useMemo(props.festival, tooltipVisibility) {
        Fragment.create {
            div {
                css {
                    visibility = tooltipVisibility
                    color = props.festival.quartileColor
                    backgroundColor = rgba(0, 0, 0, 0.8)
                    borderRadius = 8.px
                    padding = 3.px
                }
                +props.festival.interchangeFestival.festivalName
            }
        }
    }
    val (tooltipTarget, tooltipPortal) = usePopper<HTMLDivElement>(tooltip)
    var isHoveredOver by useState<Boolean>(false)
    useEffect(globeContext.selection, isHoveredOver) {
        val isSelected = globeContext.selection
            .let { selection -> selection is FestivalSelected && selection.festival.interchangeFestival.festivalName == props.festival.interchangeFestival.festivalName }
        val shouldShowTooltip = isHoveredOver || isSelected
        tooltipVisibility = if (shouldShowTooltip) Visibility.visible else Visibility.hidden
    }

    useEffect(isHoveredOver) {
        val hoveredFestivalState = globeContext.hoveredFestival
        if (isHoveredOver) {
            hoveredFestivalState?.set(props.festival)
        }
    }

    val wheelRef = useDOMEvent<WheelEvent, HTMLDivElement>(WheelEvent.WHEEL) { event ->
        props.globeDOMRefs.current?.canvas?.dispatchEvent(WheelEvent(event.type,
            event.unsafeCast<WheelEventInit>()))
    }

    val pointerDownRef = useDOMEvent<PointerEvent, HTMLDivElement>(PointerEvent.POINTER_DOWN) { event ->
        props.globeDOMRefs.current?.canvas?.dispatchEvent(PointerEvent(event.type,
            event.unsafeCast<PointerEventInit>()))
        globeContext.onSelectFestival.invoke(props.festival)
    }

    val pointerUpRef = useDOMEvent<PointerEvent, HTMLDivElement>(PointerEvent.POINTER_UP) { event ->
        props.globeDOMRefs.current?.canvas?.dispatchEvent(PointerEvent(event.type,
            event.unsafeCast<PointerEventInit>()))
    }

    +tooltipPortal

    // This div element is used internally by usePopper()
    div {
        ref = MergeRefCallback(wheelRef, pointerDownRef, pointerUpRef)
        div {
            css {
                width = 20.px
                height = 25.px
                color = props.festival.quartileColor
                pointerEvents = auto
            }
            onMouseOver = eventHandler@{
                if (window.matchMedia("(hover: none)").matches) return@eventHandler
                isHoveredOver = true
            }
            onTouchStart = {
                globeContext.onSelectFestival(props.festival)
            }
            onMouseOut = { isHoveredOver = false }
            ref = tooltipTarget
            GlobePinSVG {
                isGlowing = globeContext.selection.let { selection -> selection is FestivalSelected
                        && selection.festival.interchangeFestival.festivalName == props.festival.interchangeFestival.festivalName }
            }
        }
    }
}

external interface FestivalGlobeProps: Props {
    var festivals: List<ClientMusicFestival>
    var selection: FestivalSelection
    var onSelectFestival: (ClientMusicFestival) -> Unit
}

val FestivalGlobe = FC<FestivalGlobeProps> { props ->
    val nameFestivalMap = useMemo(props.festivals) {
        props.festivals.associateBy { it.interchangeFestival.festivalName }
    }

    // This logic keeps the hovered festival and selected festival markers on top of all the others.
    val markerFocusRef = useRef<(List<MarkerIdentity>) -> Unit>()
    val hoveredFestivalState = useState<ClientMusicFestival?>(null)
    useEffect(props.selection, hoveredFestivalState.get()) {
        val selectedFestival = props.selection.let { selection ->
            if (selection is FestivalSelected) selection.festival else null }
        val focusedMarkers =
            sequenceOf(selectedFestival, hoveredFestivalState.get())
                .filterNotNull()
                .map { festival ->
                    MarkerIdentity(festival.interchangeFestival.longitude, festival.interchangeFestival.latitude,
                        festival.interchangeFestival.festivalName)
                }
                .toList()

        markerFocusRef.current?.invoke(focusedMarkers)
    }

    val cameraTargetCoordinates = useMemo(props.selection) {
        when (val selection = props.selection) {
            is FestivalSelected -> {
                if (selection.animateGlobe) {
                    GlobeTargetPoint(selection.festival.interchangeFestival.longitude,
                        selection.festival.interchangeFestival.latitude)
                } else null
            }
            NoSelection -> null
        }
    }

    val animateToPointRef = useRef<(GlobeTargetPoint) -> Job>()
    useEffect(cameraTargetCoordinates) {
        if (cameraTargetCoordinates == null) return@useEffect
        val job = animateToPointRef.current?.invoke(cameraTargetCoordinates)
        cleanup { job?.cancel() }
    }

    div {
        css {
            height = 100.pct
            width = 100.pct
            backgroundColor = NamedColor.black
            backgroundImage = url("background.jpg")
            backgroundSize = BackgroundSize.cover
        }
        FestivalGlobeContext.Provider {
            value = FestivalGlobeContextValue(hoveredFestivalState, props.onSelectFestival, props.selection)

            Globe {
                this.markerFocusRef = markerFocusRef
                this.animateToPointRef = animateToPointRef
                autoRotate = false
                markers = nameFestivalMap.asSequence()
                    .map { (name, festival) -> MarkerIdentity(festival.interchangeFestival.longitude, festival.interchangeFestival.latitude, name) }
                    .toSet()
                markerComponentFactory = { festivalIdentity, globeDOMRefs ->
                    Fragment.create {
                        FestivalGlobePin {
                            festival = nameFestivalMap[festivalIdentity.uniqueMarkerId]!!
                            this.globeDOMRefs = globeDOMRefs
                        }
                    }
                }
            }
        }
    }
}