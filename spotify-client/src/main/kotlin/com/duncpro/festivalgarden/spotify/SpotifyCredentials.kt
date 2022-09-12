package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyCredentials(
    val spotifyClientId: String,
    val spotifyClientSecret: String,
)