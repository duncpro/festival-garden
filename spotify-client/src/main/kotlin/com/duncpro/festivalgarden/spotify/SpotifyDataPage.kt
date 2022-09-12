package com.duncpro.festivalgarden.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyDataPage<T>(
    val items: List<T>,
    val total: Int,
    val offset: Int,
    val limit: Int
)