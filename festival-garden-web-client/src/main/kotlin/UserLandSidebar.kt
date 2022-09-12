import react.FC
import react.Props
import react.useContext
import react.useRef

val UserLandSidebar = FC<Props> {
    val userContext = useContext(UserLandContext)
    val rankingsScrollStateRef = useRef<Double>()

    Sidebar {
        if (userContext.selectedFestival == null) {
            FestivalRankingPage {
                scrollStateRef = rankingsScrollStateRef
            }
        }
        if (userContext.selectedFestival != null) { FestivalDetailPage {} }
    }
}