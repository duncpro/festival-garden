package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class ProcessSpotifyLibraryProgressResponseBody(
    val percentageFinished: Double
)