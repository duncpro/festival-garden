package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTrackItem(
    val track: SpotifyTrack
)