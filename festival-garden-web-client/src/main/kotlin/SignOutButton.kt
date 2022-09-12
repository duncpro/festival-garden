import kotlinx.browser.localStorage
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.useContext

val SignOutButton = FC<Props> {
    val navigateTo = useContext(RouterContext)
    button {
        onClick = {
            localStorage.removeItem("authToken")
            navigateTo(Page.LANDING_PAGE)
        }
        +"Sign Out"
    }
}