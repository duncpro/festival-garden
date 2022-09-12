package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyClientLoginResponseBody(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expirationTimeout: Long,
    @SerialName("token_type") val tokenType: String
)