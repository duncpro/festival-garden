package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyArtistsPage(
    val artists: SpotifyDataPage<SpotifyArtist>
)