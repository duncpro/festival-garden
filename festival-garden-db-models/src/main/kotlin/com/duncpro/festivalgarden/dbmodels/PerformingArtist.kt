package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.QueryResultRow

data class PerformingArtist(
    val name: String,
    val spotifyId: String,
    val spotifyGenres: Set<String>,
    val spotifyPopularity: Int,
    val smallestImageUrl: String?
) {
    constructor(row: QueryResultRow) : this(
        name = row.get("name", String::class.java).orElseThrow(),
        spotifyId = row.get("spotify_id", String::class.java).orElseThrow(),
        spotifyGenres = row.get("spotify_genres", String::class.java)
            .orElseThrow()
            .split(", ")
            .toSet(),
        spotifyPopularity = row.get("spotify_popularity", Int::class.java).orElseThrow(),
        smallestImageUrl = row.get("smallest_image_url", String::class.java).orElse(null)
    )
}