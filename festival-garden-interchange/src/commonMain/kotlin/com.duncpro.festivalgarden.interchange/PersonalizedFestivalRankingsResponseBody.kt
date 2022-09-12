package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class PersonalizedFestivalRankingsResponseBody(
    // The backend processes the user's Spotify library in the background.
    // The client should continue polling until this boolean value is true.
    // The topFestivals field will only contain partial results until this value is true.
    val isFinishedProcessingMusicLibrary: Boolean,
    /**
     * The percentage of library pages which have been processed, or null if the user's
     * library has not been indexed yet.
     */
    val libraryProcessingProgress: Double?,
    val didErrorOccurWhileProcessingLibrary: Boolean,
    val festivals: Set<InterchangeMusicFestival>,
    val personalizedFestivalMetadata: Map<InterchangeMusicFestival, PersonalizedFestivalMetadata>
)

@Serializable
data class PersonalizedFestivalMetadata(
    val rank: Int,
    val quartile: Int,
    val knownArtists: List<InterchangeArtist>
)