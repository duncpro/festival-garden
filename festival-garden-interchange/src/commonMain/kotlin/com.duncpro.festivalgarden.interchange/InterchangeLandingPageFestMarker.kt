package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class InterchangeLandingPageFestMarker(
    val longitude: Double,
    val latitude: Double,
    val name: String,
    val locationName: String
)