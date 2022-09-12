import ActionState.*
import csstype.AlignItems
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.FontWeight
import csstype.None
import csstype.None.none
import csstype.px
import emotion.react.css
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.useContext

val RefreshMusicLibraryButton = FC<Props> {
    val authContext = useContext(SpotifyAuthorizationContext)
    val httpClientRef = useFestivalGardenAPIClient()

    val (deleteAccount, deletionProgress) = useExclusiveAsyncAction {
        val httpClient = httpClientRef.current ?: return@useExclusiveAsyncAction
        val response = httpClient.delete {
            url.appendPathSegments("account")
            accept(ContentType.Any)
        }
        if (!response.status.isSuccess()) throw Exception("Backend replied with unexpected response: ${response.status}")
        authContext.invalidate()
    }

    button {
        disabled = setOf(PENDING, UNAVAILABLE).contains(deletionProgress)
        css {
            backgroundColor = Color("#dcdcdc")
            border = none
            padding = 10.px
            color = Color("#292929")
            borderRadius = 10.px
            fontWeight = FontWeight.bold
        }

        div {
            css {
                alignItems = AlignItems.center
                display = Display.flex
                flexDirection = FlexDirection.row
            }

            when (deletionProgress) {
                PREFLIGHT, UNAVAILABLE, -> +"Refresh Spotify Library"
                PENDING -> +"Loading... (Deleting Anonymous Festival Garden Account)"
                FAILED -> +"Try Again (Festival Garden Account Deletion Failed)"
                SUCCESS -> +"Success! Redirect incoming..."
            }
        }
        onClick = { deleteAccount() }
    }
}