import PollingState.NOT_POLLING
import PollingState.POLLING
import com.duncpro.festivalgarden.interchange.InterchangeArtist
import com.duncpro.festivalgarden.interchange.UserProfileDetails
import csstype.AlignItems
import csstype.BackgroundSize
import csstype.Border
import csstype.Color
import csstype.Cursor
import csstype.Display
import csstype.Flex
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.LineStyle
import csstype.NamedColor
import csstype.TextAlign
import csstype.pct
import csstype.px
import csstype.s
import csstype.url
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.useContext
import react.MutableRefObject
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span

external interface FestivalRankingPageProps: Props {
    var scrollStateRef: MutableRefObject<Double>
}

val FestivalRankingTitle = FC<FestivalRankingPageProps> { props ->
    val userContext = useContext(UserLandContext)
    div {
        css {
            marginTop = 20.px
            width = 100.pct
            display = Display.flex
            alignItems = AlignItems.center
            flexDirection = FlexDirection.column
            textAlign = TextAlign.center
        }
        div {
            css {
                height = 75.px
                width = 75.px
                borderRadius = 50.pct
                backgroundSize = BackgroundSize.cover
                backgroundColor = Color.currentcolor
                border = Border(2.px, LineStyle.solid, Color.currentcolor)
                backgroundImage = userContext.userProfile?.profilePictureUrl?.let { url(it) }
            }
        }

        h2 {
            val ownerName = (userContext.userProfile?.let { profile ->
                val firstName = profile.spotifyUserDisplayName
                    .split(" ")
                    .first()
                "${firstName}'s"
            } ?: "Your")

            +ownerName
            br {}
            +"Festival Garden"
        }

    }
}

val FestivalRankingPage = FC<FestivalRankingPageProps> { props ->
    val userContext = useContext(UserLandContext)

    ScrollableSidebarPage {
        scrollStateRef = props.scrollStateRef
        if (userContext.personalizedFestivalRanking.isEmpty()) {
            when (userContext.userLibraryProcessingState) {
                POLLING -> {
                    ReactHTML.div {
                        css {
                            width = 100.pct
                            display = Display.flex
                            alignItems = AlignItems.center
                            justifyContent = JustifyContent.center
                            height = 100.pct
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            textAlign = TextAlign.center
                        }
                        Spinner {
                            foreground = Color.currentcolor
                            background = NamedColor.gray
                            speed = 1.s
                            size = 100.px
                            thickness = 15.px
                        }
                        div {
                            css {
                                padding = 10.px
                            }
                            +"We're scanning your music library, sit tight. "
                            +"This can take about a minute, especially if you've got a lot of good music!"
                        }
                    }
                }
                NOT_POLLING -> {
                    ReactHTML.p { +"We couldn't find any festivals for you right now." }
                    RefreshMusicLibraryButton()
                }
            }
        }

        if (userContext.personalizedFestivalRanking.isNotEmpty()) {
            FestivalRankingTitle {}
            div {
                if (userContext.userLibraryProcessingState == POLLING) {
                    div {
                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            justifyContent = JustifyContent.center
                        }
                        Spinner {
                            foreground = Color.currentcolor
                            background = NamedColor.gray
                            speed = 1.s
                            size = 25.px
                            thickness = 5.px
                        }
                        div {
                            css {
                                marginLeft = 10.px
                                flex = 1.unsafeCast<Flex>()
                            }
                            +"Your garden is growing. This should only take a minute or two."
                        }


                    }
                } else {
                   div {
                       css {
                           textAlign = TextAlign.center
                       }
                       span {
                           css {
                               color = Color("#FC466B")
                           }
                           +"festgarden.com"
                       }
                       +" \uD83C\uDF89"
                   }
                }

                for (festival in userContext.personalizedFestivalRanking) {
                    SidebarFestivalRow {
                        this.festival = festival
                    }
                }

                div {
                    css {
                        textAlign = TextAlign.center
                    }
                    RefreshMusicLibraryButton()
//                    SignOutButton()
                }
            }
        }
    }

}
val SidebarFestivalRow = FC<SidebarFestivalRowProps> { props ->
    val userContext = useContext(UserLandContext)

    div {
        onClick = {
            userContext.selectFestival(props.festival)
        }
        css {
            borderRadius = 10.px
            padding = 15.px
            marginTop = 15.px
            marginBottom = 15.px
            @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            cursor = "pointer" as Cursor
        }
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
            }

            FestivalRankingBubble { festival = props.festival }

            div {
                css {
                    display = Display.flex
                    paddingLeft = 20.px
                    flexDirection = FlexDirection.column

                    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
                    flex = 1 as Flex
                }
                div {
                    css {
                        fontSize = 20.px
                    }
                    +props.festival.interchangeFestival.festivalName
                }
                div {
                    +props.festival.interchangeFestival.prettyStartDate
                }
                div {
                    props.festival.interchangeFestival.locationName.let { locationName -> +locationName }
                }
            }
        }

        p {
            val untruncatedArtistsString = props.festival.interchangeFestival.knownArtists
                .map(InterchangeArtist::name)
                .joinToString(", ")

            +(untruncatedArtistsString.take(200) + if (untruncatedArtistsString.length > 200) "..." else "")
        }
    }
}