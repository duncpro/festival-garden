import com.duncpro.festivalgarden.interchange.InterchangeLandingPageFestMarker
import com.duncpro.festivalgarden.interchange.LandingPageDataResponseBody
import csstype.AlignItems
import csstype.BackgroundSize
import csstype.Border
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.FontWeight
import csstype.GridTemplateColumns
import csstype.JustifyContent
import csstype.Length
import csstype.LineStyle
import csstype.NamedColor
import csstype.None
import csstype.Overflow
import csstype.Position
import csstype.TextAlign
import csstype.Visibility
import csstype.pct
import csstype.px
import csstype.rgb
import csstype.rgba
import csstype.s
import csstype.url
import emotion.react.css
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.EventType
import org.w3c.dom.events.addEventHandler
import react.FC
import react.Fragment
import react.Props
import react.create
import react.createContext
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.p
import react.useContext
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import kotlin.math.min
import kotlin.random.Random
import react.useEffect

private val LandingPageShowcasedFestivalContext =
    createContext<InterchangeLandingPageFestMarker?>(null)

external interface LandingPagePinLabelProps: Props {
    var festival: InterchangeLandingPageFestMarker
}
val LandingPagePinLabel = FC<LandingPagePinLabelProps> { props ->
    val showcasedFestival = useContext(LandingPageShowcasedFestivalContext)
    val tooltip = useMemo(props.festival, showcasedFestival) {
        Fragment.create {
            div {
                css {
                    visibility = if (showcasedFestival == props.festival) Visibility.visible else Visibility.hidden
                    backgroundColor = rgba(0, 0, 0, 0.8)
                    borderRadius = 8.px
                    padding = 3.px
                }
                +props.festival.name
            }
        }
    }
    val (tooltipTarget, tooltipPortal) = usePopper<HTMLDivElement>(tooltip)

    +tooltipPortal

    div {
        div {
            css {
                color = if (showcasedFestival == props.festival) {
                    NamedColor.white
                } else {
                    Color("#FC466B")
                }
            }
            ref = tooltipTarget
            GlobePinSVG {
                isGlowing = false
            }
        }
    }
}

val LandingPage = FC<Props> {
    var windowWidth by useState<Int>(window.innerWidth)
    useEffectOnce {
        val unregister = window.addEventHandler(EventType("resize")) {
            windowWidth = window.innerWidth
        }
        cleanup { unregister() }
    }

    var globeMarkers by useState<Map<MarkerIdentity, InterchangeLandingPageFestMarker>> { emptyMap() }
    val (requestStatus, response, _) = useGetEndpoint<LandingPageDataResponseBody>("landing-page-data")
    useEffect(response) {
        if (response == null) return@useEffect
        globeMarkers = response.markers
            .associateBy { MarkerIdentity(it.longitude, it.latitude, it.name) }
        console.log("Successfully fetched markers for landing page")
    }

    var showcasedFestival by useState<InterchangeLandingPageFestMarker?>(null)
    val markerFocusRef = useRef<(List<MarkerIdentity>) -> Unit>()
    useRepeatingTask(globeMarkers) {
        if (globeMarkers.isEmpty()) return@useRepeatingTask
        val (identity, festival) = globeMarkers.entries.asSequence()
            .take(Random.nextInt(globeMarkers.size) + 1)
            .last()
        showcasedFestival = festival
        markerFocusRef.current?.invoke(listOf(identity))
        delay(3000)
    }


    div {
        css {
            color = Color("white")
            width = 100.pct
            backgroundImage = url("background.jpg")
            backgroundSize = BackgroundSize.cover
        }

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                width = "calc(100.pct - 30.px)".unsafeCast<Length>()
                justifyContent = JustifyContent.spaceBetween
                alignItems = AlignItems.center
                padding = 15.px
                backgroundColor = NamedColor.transparent
            }
            FestivalGardenLogo {
                color = NamedColor.white
            }
            LoginWithSpotifyButton {}
        }

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
            }
            div { css { height = 80.px } }

            div {
                css {
                    fontSize = 23.px
                    width = 90.pct
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                    textAlign = TextAlign.center
                }
                div {
                    +"Explore the music festivals of planet earth."
                }
                br {}
                div { +"Discover where your favorite artists are playing this year." }
            }

            div {
                css {
                    width = min(500, windowWidth - 15).px
                    height = min(500, windowWidth - 15).px
                    position = Position.relative
                    pointerEvents = None.none
                }
                LandingPageShowcasedFestivalContext.Provider {
                    value = showcasedFestival
                    Globe {
                        this.markerFocusRef = markerFocusRef
                        enableZoom = false
                        autoRotate = true
                        initialZoom = 200.0
                        markerComponentFactory = { markerIdentity, _ ->
                            Fragment.create {
                                LandingPagePinLabel { this.festival = globeMarkers[markerIdentity]!!  }
                            }
                        }
                        markers = globeMarkers.keys
                        autoRotate = true
                    }
                }
            }
        }
    }

    div {
        css {
            padding = 20.px
            color = Color("#FC466B")
            display = Display.grid
            columnGap = 50.px
            gridTemplateColumns = "repeat(auto-fit, minmax(240px, 1fr))".unsafeCast<GridTemplateColumns>()
        }
        div {
            h2 {
                +"What is this?"
            }
            p {
                +"Festival Garden is a free website which uses your Spotify library"
                +" in conjunction with "
                a {
                    href = "https://musicfestivalwizard.com"
                    +"Music Festival Wizard"
                }
                +" to find festivals you'll love."
            }
            p {
                +"After logging in with Spotify, your Liked Songs will be indexed and "
                +"compared to an online database of music festivals. Festival Garden will "
                +"then build you a personalized ranking of all upcoming festivals occurring within the next 365 days."
            }
            p {
                +"Click the \"Get Started\" button on the top-right corner of the page "
                +"to discover your festival garden!"
            }
        }
        div {
            h2 {
                +"Privacy"
            }
            p {
                +"Festival Garden does not share your Spotify music library (or listening "
                +"history) with any third-parties (for example advertisers)."
            }
            p {
                +"Festival Garden only requests access to your Liked Songs and Spotify "
                +"User Profile. This website can not make changes to your Spotify account "
                +" (like deleting or adding songs)."
            }
        }
        div {
            h2 {
                +"Trending Festivals"
            }
            when (requestStatus) {
                RequestState.PENDING -> {
                    div {
                        css {
                            width = 100.pct
                            height = 200.px
                            display = Display.flex
                            alignItems = AlignItems.center
                            justifyContent = JustifyContent.center
                        }
                        Spinner {
                            foreground = rgb(252, 70, 107)
                            background = rgba(252, 70, 107, 0.5)
                            speed = 1.s
                            size = 50.px
                            thickness = 7.px
                        }
                    }
                }
                RequestState.SUCCESS -> {
                    ol {
                        globeMarkers.values.asSequence()
                            .take(10)
                            .forEach { festival ->
                                li {
                                    +festival.name
                                    if (festival.locationName.isNotBlank()) {
                                        +" in ${festival.locationName}"
                                    }
                                }
                            }
                    }
                }
                RequestState.FAILED -> {
                    p {
                        +"An error occurred while fetching the trending festivals list from"
                        +" the Festival Garden server."
                    }
                }
            }

        }
    }
    div {
        css {
            paddingBottom = 10.px
            fontSize = 14.px
            width = 100.pct
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            flexDirection = FlexDirection.column
            color = Color("#FC466B")
        }

        div {
            css {
                borderColor = Color.currentcolor
                borderTop = Border(1.px, LineStyle.solid)
                width = 80.pct
                height = 10.px
                fontWeight = FontWeight.normal
            }
        }

        div {
            +"Developed by "
            a {
                +"Duncan Proctor"
                href = "https://github.com/duncpro"
            }
        }
        div {
            +"Night sky by "
            a {
                +"Graham Holtshausen"
                href = "https://unsplash.com/@freedomstudios"
            }
        }
        div {
            +"Satellite imagery by  "
            a {
                +"NASA"
                href = "https://nasa.gov"
            }
        }
        div {
            +"Music festival listings by "
            a {
                +"Music Festival Wizard"
                href = "https://musicfestivalwizard.com"
            }
        }
        div {
            +"Artist artwork by "
            a {
                +"Spotify"
                href = "https://spotify.com"
            }
        }
        div {
            +"Logo font by "
            a {
                href = "https://177studio.com/product/omegle-font/"
                +"177 Studios"
            }
        }
        div {
            +"© Duncan Proctor 2022 "
        }
    }
}