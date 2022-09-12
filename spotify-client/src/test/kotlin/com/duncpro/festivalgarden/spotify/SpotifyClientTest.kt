package com.duncpro.festivalgarden.spotify

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class SpotifyClientTest {
    private val credentials = SpotifyCredentials(
        spotifyClientId = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
        spotifyClientSecret = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET")
    )

    @Test
    fun getPublicPlaylistTracksPage() = runBlocking {
        val page = SpotifyClient(credentials, handleRateLimitInternally = false).use { spotifyClient ->
            spotifyClient.getPublicPlaylistTracksPage("https://api.spotify.com/v1/playlists/1iwyVb8MGG9pDcpJ4dhDDy/tracks")
        }
        println(page)
    }
}