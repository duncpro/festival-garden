package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyUserProfile(
    @SerialName("display_name") val displayName: String,
    val images: List<SpotifyUserImage>
)

@Serializable
data class SpotifyUserImage(
    val height: Int?,
    val width: Int?,
    val url: String
)