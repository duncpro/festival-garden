package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDetails(
    val spotifyUserDisplayName: String,
    val profilePictureUrl: String?
)
