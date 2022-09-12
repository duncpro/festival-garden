package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class PersonalizedUnknownArtistsResponseBody(
    val artists: List<InterchangeArtist>
)