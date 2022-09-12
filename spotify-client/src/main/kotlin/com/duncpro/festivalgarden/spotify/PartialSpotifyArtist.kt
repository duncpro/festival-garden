package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class PartialSpotifyArtist(
    val id: String,
    val name: String
)