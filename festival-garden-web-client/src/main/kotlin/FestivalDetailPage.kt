import RequestState.*
import com.duncpro.festivalgarden.interchange.InterchangeArtist
import com.duncpro.festivalgarden.interchange.PersonalizedUnknownArtistsResponseBody
import csstype.AlignItems
import csstype.BackgroundSize
import csstype.Color
import csstype.Cursor
import csstype.Cursor.Companion.pointer
import csstype.Display
import csstype.Flex
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.NamedColor
import csstype.None
import csstype.None.none
import csstype.Overflow
import csstype.Position
import csstype.pct
import csstype.px
import csstype.s
import csstype.url
import emotion.react.css
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import org.w3c.dom.HTMLDivElement
import react.FC
import react.Props
import react.dom.html.ReactHTML.`object`
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.useContext
import react.useState
import react.useEffect
import react.useRef

external interface ArtistListingProps: Props {
    var artist: InterchangeArtist
}
private val ArtistListing = FC<ArtistListingProps> { props ->
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            justifyContent = JustifyContent.center
            alignItems = AlignItems.center
            padding = 10.px
        }
        div {
            css {
                width = 50.px
                height = 50.px
                borderRadius = 50.pct
                backgroundSize = BackgroundSize.cover
                props.artist.smallestImageUrl?.let { imageUrl ->
                    backgroundImage = url(imageUrl)
                }
            }
        }
        div {
            css {
                marginLeft = 25.px
            }
            +props.artist.name
        }
    }
}

val FestivalDetailPage = FC<Props> {
    val userContext = useContext(UserLandContext)
    val selectedFestival = userContext.selectedFestival!!

    val (unknownArtistsRequestState, unknownArtistsResponse, refresh) =
        useGetEndpoint<PersonalizedUnknownArtistsResponseBody>(
            "/festival/${selectedFestival.interchangeFestival.id}/personalized-unknown-artists")

    useEffect(userContext.userLibraryVersion) { refresh() }

    ScrollableSidebarPage {
        resetScrollPositionWhenChanged = arrayOf(userContext.selectedFestival)
        button {
            css {
                @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
                cursor = "pointer" as Cursor
                border = none
                backgroundColor = NamedColor.transparent
            }
            onClick = { userContext.deselectMusicFestival() }
            `object` {
                css {
                    pointerEvents = none
                    width = 25.px
                    height = 25.px
                }
                type = "image/svg+xml"
                data = "arrow-small-left.svg"
            }
        }

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
            }
            FestivalRankingBubble { festival = selectedFestival }
            div {
                css {
                    marginLeft = 10.px
                    display = Display.flex
                    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
                    flex = 1 as Flex
                }
                h1 { +selectedFestival.interchangeFestival.festivalName }
            }
        }
        ViewOnFestivalWizardButton {
            url = selectedFestival.interchangeFestival.url
        }

        val locationName = selectedFestival.interchangeFestival.locationName
        p {
            +("Begins on ${selectedFestival.interchangeFestival.prettyStartDate}" +
                if (locationName.isBlank()) "" else " in $locationName")
            +"."
        }

        h2 { +"Lineup" }
        h3 { +"Who you'll know" }
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.start
                flexDirection = FlexDirection.column
            }
            for (artist in selectedFestival.interchangeFestival.knownArtists) {
                ArtistListing { this.artist = artist }
            }
        }
        h3 { +"Who you'll meet" }
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.start
                flexDirection = FlexDirection.column
            }
            when (unknownArtistsRequestState) {
                PENDING -> {
                    Spinner {
                        foreground = Color.currentcolor
                        background = NamedColor.gray
                        speed = 1.s
                        size = 50.px
                        thickness = 9.px
                    }
                }
                SUCCESS -> {
                    for (artist in unknownArtistsResponse?.artists ?: emptyList()) {
                        ArtistListing { this.artist = artist }
                    }
                }
                FAILED -> p { +"Unable to fetch artists at this time." }
            }
        }
    }
}