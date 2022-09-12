package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyImage(
    val height: Int,
    val width: Int,
    val url: String
)