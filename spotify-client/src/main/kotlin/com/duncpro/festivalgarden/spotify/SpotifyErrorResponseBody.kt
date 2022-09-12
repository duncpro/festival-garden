package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyErrorResponseBody(
    val error: SpotifyError
)

@Serializable
data class SpotifyError(val message: String)