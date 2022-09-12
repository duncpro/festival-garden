import PollingState.*
import com.duncpro.festivalgarden.interchange.InterchangeMusicFestival
import com.duncpro.festivalgarden.interchange.PersonalizedFestivalRankingsResponseBody
import com.duncpro.festivalgarden.interchange.UserProfileDetails
import csstype.Color
import csstype.Position
import csstype.TextAlign
import csstype.ZIndex
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.createContext
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useState
import kotlin.math.ceil

data class ClientMusicFestival(
    val interchangeFestival: InterchangeMusicFestival,
    val quartile: Int,
    val rank: Int
)

data class UserLandContextValue(
    val personalizedFestivalRanking: List<ClientMusicFestival> = emptyList(),
    val userLibraryProcessingState: PollingState = NOT_POLLING,
    val selectedFestival: ClientMusicFestival? = null,
    val selectFestival: (festival: ClientMusicFestival) -> Unit = {},
    val deselectMusicFestival: () -> Unit = {},
    val userLibraryVersion: Int = 0,
    val userProfile: UserProfileDetails? = null,
    val hoveredFestival: ClientMusicFestival? = null,
    val setHoveredFestival: (ClientMusicFestival?) -> Unit = {}
)

fun createClientFestivals(topFestivals: List<InterchangeMusicFestival>): List<ClientMusicFestival> {
    val clientMusicFestivals = mutableListOf<ClientMusicFestival>()
    val quartileSize = ceil(topFestivals.size / 4.0).toInt()

    for ((index, interchangeFestival) in topFestivals.withIndex()) {
        clientMusicFestivals.add(ClientMusicFestival(
            interchangeFestival,
            quartile = index / quartileSize,
            rank = index + 1
        ))
    }

    return clientMusicFestivals
}

val UserLandContext = createContext<UserLandContextValue>(UserLandContextValue())

val Credits = FC<Props> {
    val displayMode = useDisplayMode()

    if (displayMode != DisplayMode.NORMAL) return@FC

    div {
        css {
            position = Position.absolute
            right = 20.px
            bottom = 20.px
            textAlign = TextAlign.right
            @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            zIndex = 2 as ZIndex
        }

        div {
            +"Satellite Imagining Provided by "
            a {
                href = "https://nasa.gov"
                +"NASA"
            }
        }
        div {
            +"Musical Preferences Provided by "
            a {
                href = "https://spotify.com"
                +"Spotify"
            }
        }
        div {
            span {
                +"Music Festival Listings by "
                a {
                    href = "https://musicfestivalwizard.com"
                    +"Music Festival Wizard"
                }
            }

        }
    }
}

val UserLand = FC<Props> {
    val (response, pollingStatus, cancelRankingPoll) =
        usePoll<PersonalizedFestivalRankingsResponseBody>("/personalized-festival-ranking", 5 * 1000)

    var selectedFestival by useState<FestivalSelection>(NoSelection)

    useEffect(response) {
        if (response?.isFinishedProcessingMusicLibrary == true) cancelRankingPoll()
    }

    val clientFestivals = useMemo(response) { createClientFestivals(response?.festivals ?: emptyList()) }

    val (_, userProfileDetails, _) = useGetEndpoint<UserProfileDetails>("/user-profile")

    div {
        css {
            height = 100.pct
            width = 100.pct
            color = Color("#dcdcdc")
        }
        UserLandContext.Provider {
            value = UserLandContextValue(
                clientFestivals,
                pollingStatus.state,
                selectedFestival = selectedFestival
                    .let { selection -> if (selection is FestivalSelected) selection.festival else null },

                selectFestival = { festival -> selectedFestival = FestivalSelected(festival, true) },
                deselectMusicFestival = { selectedFestival = NoSelection },
                userLibraryVersion = pollingStatus.successfulRequestCount,
                userProfile = userProfileDetails
            )
            UserLandSidebar()
            Credits()
            FestivalGlobe {
                festivals = clientFestivals
                this.selection = selectedFestival
                onSelectFestival = { festival -> selectedFestival = FestivalSelected(festival, false) }
            }
        }
    }
}
