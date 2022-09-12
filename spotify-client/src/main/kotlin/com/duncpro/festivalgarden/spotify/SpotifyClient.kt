package com.duncpro.festivalgarden.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestPipeline.Phases.Before
import io.ktor.client.request.accept
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parametersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.pipeline.Pipeline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

val RelaxedJson = Json { ignoreUnknownKeys = true }

class SpotifyClient(
    val spotifyCredentials: SpotifyCredentials,
    private val handleRateLimitInternally: Boolean
): AutoCloseable {
    private data class SpotifyAuthState(
        val expiration: Instant,
        val accessToken: String
    )

    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) { json(RelaxedJson) }
            if (handleRateLimitInternally) {
                install(HttpRequestRetry) {
                    retryIf { _, httpResponse -> setOf(429, 503, 502).contains(httpResponse.status.value) }
                    delayMillis { response?.headers?.get("Retry-After")?.toLong()?.times(1000) ?: 2000 }
                }
            }
        }
    }

    private fun installRateLimiterIfNecessary(httpClient: HttpClient) {
        if (!handleRateLimitInternally) {
            httpClient.receivePipeline.intercept(HttpReceivePipeline.Phases.Before) { response ->
                if (response.status.value == 429) {
                    throw SpotifyOverloadedException(
                        delayMillis = (response.headers["Retry-After"]
                            ?: throw UnexpectedSpotifyAPIException("Expected Retry-After header to exist"))
                            .toLong()
                            .times(1000) // Spotify uses seconds not milliseconds
                    )
                }
            }
        }
    }

    private val httpClient = createHttpClient()
    private val loginHttpClient = createHttpClient()

    init {
        installRateLimiterIfNecessary(httpClient)
        installRateLimiterIfNecessary(loginHttpClient)
    }

    /**
     * Finds the Spotify artist whose name best matches the given [name].
     * @see <a href="https://developer.spotify.com/console/get-search-item/">Spotify Search Endpoint</>
     * @throws UnexpectedSpotifyAPIException if the Spotify API returns an erroneous response for which there is no
     * directly corresponding exception class.
     * @return a [SpotifyArtist] representing the best-match artist, or `null` if the Spotify API returned no matches
     * for the given [name].
     */
    suspend fun lookupArtistByName(name: String): SpotifyArtist? {
        val response = httpClient.get("https://api.spotify.com/v1/search?type=artist&limit=50") {
            parameter("q", name)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.body<SpotifyErrorResponseBody>()
            // An empty query, or a query containing unsupported characters was used.
            if (errorBody.error.message == "No search query") return null
            throw UnexpectedSpotifyAPIException("Failed to lookup artist by name spotify responded with ${response.status}:" +
                    " ${response.bodyAsText()}")
        }
        return response.body<SpotifyArtistsPage>().artists.items
            .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
    }

    @Volatile
    private var authState: SpotifyAuthState? = null

    private suspend fun ensureLoggedIn(): SpotifyAuthState {
        val existingAuthState = this.authState
        val expired = existingAuthState?.expiration?.isBefore(Instant.now()) ?: true

        if (expired) {
            val (accessToken, expirationTimeout) = this.login()
            val newAuthState = SpotifyAuthState(
                expiration = Instant.now().plusMillis(expirationTimeout),
                accessToken
            )
            this.authState = newAuthState
            return newAuthState
        }

        return existingAuthState!!;
    }

    init {
        httpClient.requestPipeline.intercept(Before) {
            context.bearerAuth(ensureLoggedIn().accessToken)
        }
    }

    /**
     * Sends a client authentication request to the Spotify API.
     * @see <a href="https://developer.spotify.com/documentation/general/guides/authorization/client-credentials/">
     *  Spotify Client Authentication<a/>
     * @throws UnexpectedSpotifyAPIException if the Spotify API returns an erroneous response for which there is no
     * directly corresponding exception class.
     */
    private suspend fun login(): SpotifyClientLoginResponseBody {
        val form = parametersOf("grant_type", "client_credentials")
        val response = loginHttpClient.submitForm("https://accounts.spotify.com/api/token", form) {
            basicAuth(spotifyCredentials.spotifyClientId, spotifyCredentials.spotifyClientSecret)
        }

        if (!response.status.isSuccess()) {
            throw UnexpectedSpotifyAPIException("Failed to login to Spotify because the Spotify server returned a" +
                    " non-successful status  code: ${response.status}.", response)
        }

        return response.body()
    }

    suspend fun redeemUserAuthToken(code: String, redirectUri: String): SpotifyUserLoginResponseBody? {
        val form = parametersOf(
            Pair("grant_type", listOf("authorization_code")),
            Pair("redirect_uri", listOf(redirectUri)),
            Pair("code", listOf(code))
        )
        val response = loginHttpClient.submitForm("https://accounts.spotify.com/api/token", form) {
            basicAuth(spotifyCredentials.spotifyClientId, spotifyCredentials.spotifyClientSecret)
        }
        if (!response.status.isSuccess()) return null
        return response.body()
    }

    suspend fun refreshUserAuthToken(refreshToken: String): SpotifyUserTokenRefreshResponseBody? {
        val form = parametersOf(
            Pair("grant_type", listOf("refresh_token")),
            Pair("refresh_token", listOf(refreshToken)),
        )
        val response = loginHttpClient.submitForm("https://accounts.spotify.com/api/token", form) {
            basicAuth(spotifyCredentials.spotifyClientId, spotifyCredentials.spotifyClientSecret)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) return null
        return response.body()
    }

    suspend fun getPublicPlaylistTracksPage(url: String): SpotifyDataPage<SpotifyTrackItem>? {
        val response = httpClient.get(url)
        if (response.status.value == 404) return null /* null */;
        if (!response.status.isSuccess()) throw UnexpectedSpotifyAPIException("Failed to get public playlist", response)
        return response.body()
    }

    override fun close() {
        httpClient.close()
        loginHttpClient.close()
    }
}

