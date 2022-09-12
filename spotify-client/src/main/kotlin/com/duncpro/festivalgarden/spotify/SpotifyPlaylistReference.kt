package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistTracksReference(
    val total: Int,
    val href: String
)

@Serializable
data class SpotifyPlaylistReference(
    val tracks: SpotifyPlaylistTracksReference
)