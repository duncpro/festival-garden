package com.duncpro.festivalgarden.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SpotifyUserClient(
    private val userAuthorizationToken: String,
    handleRateLimitInternally: Boolean
): AutoCloseable {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        defaultRequest {
            url("https://api.spotify.com")
            bearerAuth(userAuthorizationToken)
        }
        if (handleRateLimitInternally) {
            install(HttpRequestRetry) {
                retryIf { _, httpResponse -> setOf(429, 503, 502).contains(httpResponse.status.value) }
                delayMillis { response?.headers?.get("Retry-After")?.toLong()?.times(1000) ?: 2000 }
            }
        }
    }

    init {
        httpClient.receivePipeline.intercept(HttpReceivePipeline.Phases.Before) { response ->
            if (response.status.value == 401) throw SpotifyTokenExpiredException()
        }
    }

    init {
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

    @Throws(SpotifyTokenExpiredException::class, SpotifyOverloadedException::class)
    suspend fun getLikedSongs(trackOffset: Int, pageLengthLimit: Int): SpotifyDataPage<SpotifyTrackItem> {
        val response = httpClient.get("/v1/me/tracks?offset=$trackOffset&limit=$pageLengthLimit")
        if (!response.status.isSuccess()) {
            throw UnexpectedSpotifyAPIException("Spotify returned an unexpected response code after request for" +
                    " user liked tracks: ${response.status} ${response.request.url}: ${response.bodyAsText()}", response)
        }
        return response.body()
    }

    // https://api.spotify.com/v1/me
    @Throws(SpotifyTokenExpiredException::class, SpotifyOverloadedException::class)
    suspend fun getProfile(): SpotifyUserProfile {
        val response = httpClient.get("/v1/me")
        if (!response.status.isSuccess()) {
            throw UnexpectedSpotifyAPIException("Spotify returned an unexpected response code after request for" +
                    " profile: ${response.status} ${response.request.url}: ${response.bodyAsText()}", response)
        }
        return response.body()
    }

    @Throws(SpotifyTokenExpiredException::class, SpotifyOverloadedException::class)
    suspend fun getPlaylistsPage(playlistsOffset: Int, pageLengthLimit: Int): SpotifyDataPage<SpotifyPlaylistReference> {
        val response = httpClient.get("/v1/me/tracks?offset=$playlistsOffset&limit=$pageLengthLimit")
        if (!response.status.isSuccess()) {
            throw UnexpectedSpotifyAPIException("Spotify returned an unexpected response code after request for" +
                    " user playlists: ${response.status} ${response.request.url}: ${response.bodyAsText()}", response)
        }
        return response.body()
    }


    override fun close() { httpClient.close() }
}