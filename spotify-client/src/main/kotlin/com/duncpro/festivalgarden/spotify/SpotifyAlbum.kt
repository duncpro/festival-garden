package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAlbum(
    val artists: Set<PartialSpotifyArtist>
)