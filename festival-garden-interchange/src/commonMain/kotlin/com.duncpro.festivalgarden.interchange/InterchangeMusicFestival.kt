package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class InterchangeMusicFestival(
    val festivalName: String,
    val url: String,
    val longitude: Double,
    val latitude: Double,
    val startDate: Long,
    val endDate: Long,

    // Might be an empty string
    val locationName: String,
    val id: String,
)