import Page.*
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams
import react.FC
import react.Props
import react.createContext
import react.useState

enum class Page {
    LANDING_PAGE,
    USER_LAND
}

val NOOP_ROUTER: (Page) -> Unit = { page -> console.warn("Tried to navigate to $page but router was not configured.") }
val RouterContext = createContext(NOOP_ROUTER)

val Router = FC<Props> {
    var currentRoute by useState<Page> {
        URLSearchParams(window.location.search).get("force_route")
            ?.let(Page::valueOf) ?: Page.LANDING_PAGE
    }

    RouterContext.Provider {
        value = { newRoute ->
            currentRoute = newRoute
        }
        when (currentRoute) {
            LANDING_PAGE -> LandingPage {}
            USER_LAND -> SpotifyAuthorizationGuard {
                UserLand {}
            }
        }
    }
}