package com.duncpro.festivalgarden.festivalwizardscraper


import com.duncpro.festivalgarden.dbmodels.Festival
import com.duncpro.festivalgarden.dbmodels.PerformingArtist
import com.duncpro.festivalgarden.spotify.SpotifyArtist
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.aws.AuroraServerlessCredentials
import com.duncpro.jackal.aws.AuroraServerlessDatabase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.rdsdata.RdsDataAsyncClient
import java.time.Duration
import kotlin.random.Random
import com.duncpro.festivalgarden.spotify.SpotifyClient
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.InterpolatableSQLStatement
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.executeTransaction
import kotlinx.coroutines.future.await
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient

private val logger = LoggerFactory.getLogger("Main")

data class FestivalWizardScraperConfiguration(
    val spotifyCredentials: SpotifyCredentials,
    val scrapeLimit: Int
)

fun main(): Unit = runBlocking {
    val nettyAwsClient = NettyNioAsyncHttpClient.builder().build()
    val rdsDataAsyncClient = RdsDataAsyncClient.builder()
        .httpClient(nettyAwsClient)
        .build()

    nettyAwsClient.use {
        rdsDataAsyncClient.use {
            runFestivalWizardScraper(
                AuroraServerlessDatabase(
                    rdsDataAsyncClient,
                    AuroraServerlessCredentials(
                        System.getenv("PRIMARY_DB_RESOURCE_ARN"),
                        System.getenv("PRIMARY_DB_SECRET_ARN")
                    )
                ),
                config = FestivalWizardScraperConfiguration(
                    scrapeLimit = Int.MAX_VALUE,
                    spotifyCredentials = SpotifyCredentials(
                        spotifyClientId = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
                        spotifyClientSecret = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
                    )
                )
            )
        }
    }
}

suspend fun runFestivalWizardScraper(database: SQLDatabase, config: FestivalWizardScraperConfiguration) = coroutineScope {
    val spotifyClient = SpotifyClient(config.spotifyCredentials, handleRateLimitInternally = true)
    coroutineContext.job.invokeOnCompletion { spotifyClient.close() }

    // Wait at least 3 seconds between each request to https:/www.musicalfestivalwizard.com, and at most 6 seconds.
    val festivalWizardApiClient = HttpClient(CIO)
    coroutineContext.job.invokeOnCompletion { festivalWizardApiClient.close() }
    val festivalWizardRateLimiter = RateLimiter { Duration.ofMillis(3000 + Random.nextLong(3000)) }
    festivalWizardApiClient.requestPipeline.intercept(HttpRequestPipeline.Before) { festivalWizardRateLimiter.acquirePermit() }

    logger.info("Starting Festival Wizard Scraper")

    scrapeFestivalWizardIndex(festivalWizardApiClient)
        .map { festival -> FWFestival(festival, scrapeFestivalDetailPage(festivalWizardApiClient, festival.url)) }
        .onEach { logger.debug("Scraped Music Festival from https://www.musicalfestivalwizard.com: $it") }
        .take(config.scrapeLimit)
        .map { fwFestival -> async { compileFGFestival(fwFestival, spotifyClient) } }
        .mapNotNull(Deferred<Pair<Festival, Set<PerformingArtist>>?>::await)
        .onEach { (festival, performingArtists)  -> putFestivalInDatabase(festival, performingArtists, database) }
        .collect()

    festivalWizardRateLimiter.close()

    logger.info("Festival Wizard Scraper Finished Successfully")
}

fun generateFestivalId(name: String): String {
    fun filterNotAllowedChars(orig: String): String {
        val alphabetLower: Set<Char> = setOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')
        val alphabetUpper: Set<Char> = alphabetLower.asSequence()
            .map { it.uppercaseChar() }
            .toSet()
        val alphabetBothCases = alphabetLower union alphabetUpper
        val alphanumericBothCases = alphabetBothCases union setOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
        val allowed = alphanumericBothCases union setOf("-")

        return orig.asSequence()
            .filter { allowed.contains(it) }
            .joinToString("")
    }

    return filterNotAllowedChars(name.replace(" ", "-").lowercase())
}

suspend fun compileFGFestival(fwFestival: FWFestival, spotifyClient: SpotifyClient): Pair<Festival, Set<PerformingArtist>>? = coroutineScope {
    // If for some reason it was not possible to gather the details of this festival,
    // then it can not be compiled to an FGFestival. Therefore, return null.
    if (fwFestival.details == null) return@coroutineScope null

    val spotifyArtists = fwFestival.details.performingArtists.asFlow()
        .mapNotNull { artistName -> async {
            try {
                spotifyClient.lookupArtistByName(artistName)
            } catch (e: HttpRequestTimeoutException) {
                logger.warn("Festival will be missing artist data, failed to lookup artist from Spotify" +
                        " because Spotify failed to respond to the search request in time.", e)
                return@async null
            }
        } }
        .mapNotNull(Deferred<SpotifyArtist?>::await)
        .toSet()

    val festival = Festival(
        name = fwFestival.identity.name,
        id = generateFestivalId(fwFestival.identity.name),
        longitude = fwFestival.identity.location.geo.longitude,
        latitude = fwFestival.identity.location.geo.latitude,
        startDate = fwFestival.identity.startDate.toInstant().toEpochMilli(),
        endDate = fwFestival.identity.endDate.toInstant().toEpochMilli(),
        url = fwFestival.identity.url.toString(),
        regionName = fwFestival.identity.location.address?.addressRegion,
        municipalityName = fwFestival.identity.location.address?.addressLocality
    )
    val performingArtists: Set<PerformingArtist> = spotifyArtists.asSequence()
        .map { spotifyArtist -> PerformingArtist(
            spotifyId = spotifyArtist.id,
            spotifyPopularity = spotifyArtist.popularity,
            spotifyGenres = spotifyArtist.genres,
            name = spotifyArtist.name,
            smallestImageUrl = spotifyArtist.images.asSequence()
                .sortedBy { image -> image.width * image.height } // Get image with the smallest area
                .map { image -> image.url }
                .firstOrNull()
        ) }
        .toSet()

    return@coroutineScope festival to performingArtists
}


suspend fun putFestivalInDatabase(festival: Festival,
                                  performingArtists: Set<PerformingArtist>,
                                  database: SQLDatabase
): Unit = database.executeTransaction {
    sql("DELETE FROM festival WHERE id = ?;")
        .withArguments(festival.id)
        .executeUpdate()

    // Insert fresh records
    sql("""INSERT INTO festival (
                    "name",
                    id,
                    url,
                    start_date,
                    end_date,
                    longitude,
                    latitude,
                    region_name,
                    municipality_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
        .withArguments(festival.name, festival.id, festival.url, festival.startDate, festival.endDate)
        .withArguments(festival.longitude, festival.latitude)
        .withArguments(festival.regionName, festival.municipalityName)
        .executeUpdate()

    sql("DELETE FROM performing_artist WHERE festival_id = ?")
        .withArguments(festival.id)
        .executeUpdateAsync(database)
        .await()

    for (artist in performingArtists) {
        sql("""
            INSERT INTO performing_artist (
                festival_id,
                spotify_id,
                "name",
                spotify_popularity,
                spotify_genres,
                smallest_image_url
            ) VALUES (?, ?, ?, ?, ?, ?);
        """.trimIndent())
            .withArguments(festival.id, artist.spotifyId, artist.name, artist.spotifyPopularity)
            .withArguments(artist.spotifyGenres.joinToString(", "))
            .withArguments(artist.smallestImageUrl)
            .executeUpdate()
    }

    commit()

    logger.info("Successfully wrote festival to database: ${festival.name} (${performingArtists.size} artists): ${performingArtists}!")
}