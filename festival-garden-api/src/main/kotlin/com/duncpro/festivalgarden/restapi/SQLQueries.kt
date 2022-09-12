package com.duncpro.festivalgarden.restapi

import com.duncpro.festivalgarden.dbmodels.Festival
import com.duncpro.festivalgarden.dbmodels.PerformingArtist
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.SQLExecutorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.time.Instant
import java.util.stream.Collectors

object SQLQueries {
    suspend fun selectPersonalizedFestivalRanking(userId: String, database: SQLExecutorProvider): List<Festival> {
        val query = withContext(Dispatchers.IO) {
            (this::class.java.getResourceAsStream("/select-personalized-festival-ranking.sql")
                ?: throw IllegalStateException("Bad bundle: SQL query script not in resources"))
                .let { stream -> InputStreamReader(stream) }
                .use { scanner -> scanner.readText() }
        }
        return sql(query)
            .withArguments(Instant.now().toEpochMilli(), userId)
            .executeQueryAsync(database)
            .await()
            .map(::Festival)
            .collect(Collectors.toList())
    }

    suspend fun countAllPerformingArtists(festivalId: String, database: SQLExecutorProvider): Long {
        return sql("SELECT COUNT(*) as count FROM performing_artist WHERE festival_id = ?")
            .withArguments(festivalId)
            .executeQueryAsync(database)
            .await()
            .map { row -> row.get("count", Long::class.java).orElseThrow() }
            .findFirst()
            .orElseThrow()
    }

    suspend fun selectKnownPerformingArtists(userId: String, festivalId: String, database: SQLExecutorProvider): List<PerformingArtist> =
        sql("""
            SELECT performing_artist.*, user_liked_artist.song_count FROM performing_artist INNER JOIN user_liked_artist 
            ON performing_artist.spotify_id = user_liked_artist.spotify_artist_id
            WHERE performing_artist.festival_id = ? AND user_liked_artist.user_id = ?
            ORDER BY user_liked_artist.song_count DESC
        """.trimIndent())
            .withArguments(festivalId, userId)
            .executeQueryAsync(database)
            .await()
            .map(::PerformingArtist)
            .collect(Collectors.toList())

    suspend fun selectUnknownPerformingArtists(userId: String, festivalId: String, database: SQLExecutorProvider): List<PerformingArtist> =
        sql("""
            SELECT * FROM performing_artist 
            WHERE performing_artist.festival_id = ?
            AND performing_artist.spotify_id NOT IN (
                SELECT spotify_artist_id FROM user_liked_artist
                    WHERE user_liked_artist.user_id = ?
            );
        """.trimIndent())
            .withArguments(festivalId, userId)
            .executeQueryAsync(database)
            .await()
            .map(::PerformingArtist)
            .collect(Collectors.toList())
}