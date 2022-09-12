package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class InterchangeArtist(
    val name: String,
    val smallestImageUrl: String?
)