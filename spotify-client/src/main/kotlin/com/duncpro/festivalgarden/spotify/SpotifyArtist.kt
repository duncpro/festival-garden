package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyArtist(
    val id: String,
    val name: String,
    val genres: Set<String>,
    val popularity: Int,
    val images: Set<SpotifyImage>
)