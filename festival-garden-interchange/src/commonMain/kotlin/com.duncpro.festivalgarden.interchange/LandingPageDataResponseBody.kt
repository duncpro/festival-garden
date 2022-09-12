package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class LandingPageDataResponseBody(
    val markers: Set<InterchangeLandingPageFestMarker>
)