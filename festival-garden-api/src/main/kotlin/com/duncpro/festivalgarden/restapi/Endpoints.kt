package com.duncpro.festivalgarden.restapi

import com.duncpro.festivalgarden.dbmodels.PerformingArtist
import com.duncpro.festivalgarden.dbmodels.lookupSpotifyUserCredentials
import com.duncpro.festivalgarden.interchange.InterchangeLandingPageFestMarker
import com.duncpro.festivalgarden.interchange.PersonalizedFestivalRankingsResponseBody
import com.duncpro.festivalgarden.interchange.InterchangeMusicFestival
import com.duncpro.festivalgarden.interchange.LandingPageDataResponseBody
import com.duncpro.festivalgarden.interchange.LoginResponseBody
import com.duncpro.festivalgarden.interchange.PersonalizedUnknownArtistsResponseBody
import com.duncpro.festivalgarden.interchange.UserProfileDetails
import com.duncpro.festivalgarden.queue.InitializeLibraryProcessor
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyAccountNotFound
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyQueryFailedTemporarily
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyQuerySuccess
import com.duncpro.festivalgarden.spotify.SpotifyClient
import com.duncpro.festivalgarden.spotify.SpotifyOverloadedException
import com.duncpro.jroute.rest.HttpMethod
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.executeTransaction
import com.duncpro.jroute.rest.HttpMethod.DELETE
import com.duncpro.jroute.rest.HttpMethod.GET
import com.duncpro.restk.ContentType
import com.duncpro.restk.ContentTypes
import com.duncpro.restk.RestEndpoint
import com.duncpro.restk.RestException
import com.duncpro.restk.RestRequest
import com.duncpro.restk.RestResponse
import com.duncpro.restk.asString
import com.duncpro.restk.header
import com.duncpro.restk.path
import com.duncpro.restk.query
import com.duncpro.restk.responseOf
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.stream.Collectors
import com.duncpro.festivalgarden.sharedbackendutils.executeSpotifyQuery
import java.time.Duration

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.restapi.EndpointsKt")

typealias ApplicationRequestHandler = suspend (RestRequest, ApplicationContext) -> RestResponse

fun FGApiEndpoint(method: HttpMethod, route: String, consume: Set<ContentType>, produce: ContentType?,
                  handler: ApplicationRequestHandler): (ApplicationContext) -> RestEndpoint = { context ->
    RestEndpoint(method, route, consume, produce) { request -> handler(request, context) }
}

val GetUserProfileDetailsEndpoint = FGApiEndpoint(
    method = GET,
    route =  "/user-profile",
    consume = emptySet(),
    produce = ContentTypes.Application.JSON,
    handler = { request, context ->
        val user = authenticate(request.bearerToken(), context.database)
        val spotifyQueryResult = executeSpotifyQuery(context, user.id.toString()) { spotifyUserClient ->
            spotifyUserClient.getProfile()
        }

        when (spotifyQueryResult) {
            is SpotifyAccountNotFound -> responseOf {
                // The client will interpret a 401 error as a "force reauthorization directive",
                // in which the user will be redirected to the Spotify authorization screen.
                responseOf {
                    statusCode = 401
                }
            }
            is SpotifyQueryFailedTemporarily -> responseOf {
                statusCode = 503
                header("Retry-After", spotifyQueryResult.delayMillis)
            }
            is SpotifyQuerySuccess -> {
                val profile = spotifyQueryResult.result
                val userProfileImage = profile.images.asSequence()
                    .map { it.url }
                    .firstOrNull()

                responseOf {
                    statusCode = 200
                    jsonBody(UserProfileDetails(profile.displayName,
                        userProfileImage))
                }
            }
        }
    }
)

val SPOTIFY_AUTH_CALLBACK_URL = System.getenv("BACKEND_URL") + "/finalize-spotify-authorization/"

val GetPersonalizedFestivalRankingEndpoint = FGApiEndpoint(
    method = GET,
    route = "/personalized-festival-ranking",
    consume = emptySet(),
    produce = ContentTypes.Application.JSON,
    handler = { request, context ->
        val user = authenticate(request.bearerToken(), context.database)

        val totalPages = sql("SELECT COUNT(*) as page_count FROM user_library_page WHERE user_id = ?")
            .withArguments(user.id.toString())
            .executeQueryAsync(context.database)
            .await()
            .map { row -> row.get("page_count", Long::class.java).orElseThrow() }
            .findFirst()
            .orElseThrow()

        val processedPages = sql("SELECT COUNT(*) as page_count FROM user_library_page_result WHERE user_id = ?")
            .withArguments(user.id.toString())
            .executeQueryAsync(context.database)
            .await()
            .map { row -> row.get("page_count", Long::class.java).orElseThrow() }
            .findFirst()
            .orElseThrow()

        val didErrorOccurWhileProcessingLibrary = sql("SELECT COUNT(*) as page_count FROM user_library_page_result WHERE user_id = ?" +
                " AND was_successful = FALSE;")
            .withArguments(user.id.toString())
            .executeQueryAsync(context.database)
            .await()
            .map { row -> row.get("page_count", Long::class.java).orElseThrow() }
            .findFirst()
            .map { pageCount -> pageCount > 0 }
            .orElseThrow()

        // Begin processing the user's Spotify Library (if we haven't already).
        // We'll more than likely be introducing duplicate messages if the service is under any kind
        // of significant load. Race conditions will be resolved at the receiving end.
        if (!user.hasWrittenLibraryIndex) context.queue.offer(InitializeLibraryProcessor(user.id.toString()))

        val totalScannedArtists = sql("SELECT COUNT(*) as c FROM user_liked_artist WHERE user_id = ?")
            .withArguments(user.id.toString())
            .executeQueryAsync(context.database)
            .await()
            .map { row -> row.get("c", Long::class.java).orElseThrow() }
            .findFirst()
            .orElse(0)

        // TODO: Polling should cease regardless of completion after some time period.
        //  There's no need to waste compute resources when something probably went wrong (dropped message, database failure).
        val stopPolling = totalPages == processedPages && user.hasWrittenLibraryIndex

        val ranking = SQLQueries.selectPersonalizedFestivalRanking(user.id.toString(), context.database)
            .asFlow()
            .map { festival ->
                InterchangeMusicFestival(festival.name, festival.url, festival.longitude, festival.latitude,
                    startDate = festival.startDate,
                    endDate = festival.endDate,
                    locationName = sequenceOf(festival.municipalityName, festival.regionName)
                        .filterNotNull()
                        .joinToString(", "),
                    id = festival.id
                )
            }
            .toList()

        responseOf {
            statusCode = 200
            jsonBody(PersonalizedFestivalRankingsResponseBody(
                isFinishedProcessingMusicLibrary = stopPolling,
                libraryProcessingProgress = if (user.hasWrittenLibraryIndex) {
                    processedPages.toDouble() / totalPages
                } else {
                    null
                },
                didErrorOccurWhileProcessingLibrary,
                festivals = ranking,
                knownArtists = ranking
                    .associateWith { festival -> SQLQueries.selectKnownPerformingArtists(user.id.toString(),
                        festival.id, context.database) }
                    .mapValues { (_, artists) -> artists.map(PerformingArtist::toInterchangeArtist) },
                totalScannedArtists = totalScannedArtists
                ))
        }
    }
)

val FetchPersonalizedUnknownArtists = FGApiEndpoint(
    method = GET,
    route = "/festival/{festivalId}/personalized-unknown-artists",
    consume = emptySet(),
    produce = ContentTypes.Application.JSON,
    handler = { request, context ->
        val user = authenticate(request.bearerToken(), context.database)
        val festivalId = request.path("festivalId").asString()

        val unknownArtists = SQLQueries.selectUnknownPerformingArtists(user.id.toString(), festivalId, context.database)
            .map(PerformingArtist::toInterchangeArtist)

        responseOf {
            statusCode = 200
            jsonBody(PersonalizedUnknownArtistsResponseBody(unknownArtists))
        }
    }
)

val FetchLandingPageMarkersEndpoint = FGApiEndpoint(
    method = GET,
    route = "/landing-page-data",
    consume = emptySet(),
    produce = ContentTypes.Application.JSON,
    handler = { _, context ->
        val markers = sql("""
            SELECT AVG(performing_artist.spotify_popularity), region_name, municipality_name, longitude, latitude, festival."name" as "name" FROM festival INNER JOIN performing_artist
                ON performing_artist.festival_id = festival.id
            WHERE festival.start_date >= ? AND festival.end_date <= ?
            GROUP BY performing_artist.festival_id, festival.region_name, festival.municipality_name, festival."name", festival.latitude, festival.longitude
            ORDER BY AVG(performing_artist.spotify_popularity) DESC LIMIT 50;
        """.trimIndent())
            .withArguments(Instant.now().toEpochMilli())
            .withArguments(Instant.now().plus(Duration.ofDays(60)).toEpochMilli())
            .executeQueryAsync(context.database)
            .await()
            .map { row ->
                InterchangeLandingPageFestMarker(
                    longitude = row.get("longitude", Double::class.java).orElseThrow(),
                    latitude = row.get("latitude", Double::class.java).orElseThrow(),
                    name = row.get("name", String::class.java).orElseThrow(),
                    locationName = sequenceOf(
                        row.get("municipality_name", String::class.java).orElse(null),
                        row.get("region_name", String::class.java).orElse(null)
                    )
                        .filter { !it.isNullOrBlank() }
                        .joinToString(", ")
                )
            }
            .collect(Collectors.toSet())

        responseOf {
            statusCode = 200
            jsonBody(LandingPageDataResponseBody(markers))
        }
    }
)

// This endpoint removes all traces of the calling user from the database.
// This will result in any ongoing library processing tasks to fail permanently, effectively being discarded.
val DeleteAccountEndpoint = FGApiEndpoint(
    method = DELETE,
    route = "/account",
    consume = emptySet(),
    produce = null,
    handler = { request, context ->
        val user = authenticate(request.bearerToken(), context.database)

        context.database.executeTransaction {
            sql("DELETE FROM anonymous_user WHERE id = ?")
                .withArguments(user.id.toString())
                .executeUpdate()

            sql("DELETE FROM user_liked_artist WHERE user_id = ?")
                .withArguments(user.id.toString())
                .executeUpdate()

            sql("DELETE FROM spotify_user_credentials WHERE fg_user_id = ?")
                .withArguments(user.id.toString())
                .executeUpdate()

            sql("DELETE FROM user_library_page WHERE user_id = ?")
                .withArguments(user.id.toString())
                .executeUpdate()

            commit()
        }
        responseOf {
            statusCode = 200
        }
    }
)

val LoginEndpoint = FGApiEndpoint(
    method = GET,
    route = "/login",
    consume = emptySet(),
    produce = ContentTypes.Application.JSON
) { request, context ->

    // If the user is not currently logged in, issue them in a new anonymous identity.
    val user = request.bearerTokenOrNull()
        ?.let { token -> authenticateOrNull(token, context.database) }
        ?: issueNewIdentity(context.database)

    // Fetch the authorized Spotify account which is associated with this identity.
    // If this is a new identity there will be no Spotify credentials.
    // If a previous attempt to refresh the user's Spotify access token failed, then this also might be null,
    // since executeSpotifyQuery purges bad credentials from the database.
    val spotifyCredentials = lookupSpotifyUserCredentials(user.id.toString(), context.database)

    // If no credentials exist, re-authorize this Festival Garden anonymous identity with Spotify.
    if (spotifyCredentials == null) {
        val spotifyStateArg = mintSpotifyStateArg()
        sql("UPDATE anonymous_user SET spotify_state_arg = ? WHERE id = ?;")
            .withArguments(spotifyStateArg, user.id.toString())
            .executeUpdateAsync(context.database)
            .await()

        val query = compileQueryString(mapOf(
            Pair("client_id", context.spotifyAppCredentials.spotifyClientId),
            Pair("response_type", "code"),
            Pair("scope", SPOTIFY_AUTH_SCOPE.joinToString(" ")),
            Pair("state", spotifyStateArg),
            Pair("redirect_uri", SPOTIFY_AUTH_CALLBACK_URL)))

        return@FGApiEndpoint responseOf {
            jsonBody(LoginResponseBody(
                festivalGardenAuthToken = user.token,
                redirect = "https://accounts.spotify.com/authorize$query"
            ))
            statusCode = 200
        }
    }

    return@FGApiEndpoint responseOf {
        jsonBody(LoginResponseBody(
            festivalGardenAuthToken = user.token,
            redirect = null
        ))
        statusCode = 200
    }
}

val FinalizeSpotifyLinkEndpoint = FGApiEndpoint(
    method = GET,
    route = "/finalize-spotify-authorization",
    consume = setOf(ContentTypes.ANY),
    produce = null
) { request, context ->
    if (request.query.containsKey("error")) {
        return@FGApiEndpoint responseOf {
            statusCode = 302
            header("Location", System.getenv("FRONTEND_URL"))
        }
    }

    val spotifyStateArg = request.query("state").asString()
    val spotifyAuthCodeArg = request.query("code").asString()

    context.database.executeTransaction {
        val fgUserId = sql("SELECT id FROM anonymous_user WHERE spotify_state_arg = ?;")
            .withArguments(spotifyStateArg)
            .executeQuery(context.database)
            .findFirst()
            .orElseThrow { RestException(401) }
            .get("id", String::class.java).orElseThrow()

        // Spotify state arg is only valid for a single use.
        sql("UPDATE anonymous_user SET spotify_state_arg = NULL WHERE id = ?;")
            .withArguments(fgUserId)
            .executeUpdate()

        SpotifyClient(context.spotifyAppCredentials, handleRateLimitInternally = false).use { spotifyClient ->
            try {
                val spotifyResponse = spotifyClient.redeemUserAuthToken(spotifyAuthCodeArg, SPOTIFY_AUTH_CALLBACK_URL)
                    ?: throw RestException(401)

                sql("INSERT INTO spotify_user_credentials (spotify_access_token, spotify_refresh_token, " +
                        "spotify_access_token_expiration, fg_user_id) VALUES (?, ?, ?, ?);")
                    .withArguments(spotifyResponse.accessToken)
                    .withArguments(spotifyResponse.refreshToken)
                    .withArguments(Instant.now().plusSeconds(spotifyResponse.expiresIn).toEpochMilli())
                    .withArguments(fgUserId)
                    .executeUpdate()

                commit()

                responseOf {
                    statusCode = 302
                    header("Location", System.getenv("FRONTEND_URL") + "?force_route=USER_LAND")
                }
            } catch (e: SpotifyOverloadedException) {
                logger.warn("Operating at maximum capacity of Spotify API. Client can not be authorized" +
                        " because the application is busy performing other requests. The Festival Garden" +
                        " web client will perform the Spotify authorization process again (in hopes)" +
                        " that this congestion will have cleared.", e)
                responseOf {
                    statusCode = 302
                    header("Location", System.getenv("FRONTEND_URL") + "?force_route=USER_LAND")
                }
            }
        }
    }
}

fun createApplicationEndpoints(applicationContext: ApplicationContext): Set<RestEndpoint> {
    return sequenceOf(LoginEndpoint, FinalizeSpotifyLinkEndpoint, DeleteAccountEndpoint, GetPersonalizedFestivalRankingEndpoint,
        FetchLandingPageMarkersEndpoint, FetchPersonalizedUnknownArtists, GetUserProfileDetailsEndpoint)
        .map { it(applicationContext) }
        .toSet()
}