package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.QueryResultRow
import com.duncpro.jackal.SQLExecutorProvider
import kotlinx.coroutines.future.await
import java.time.Instant
import java.util.UUID

data class SpotifyUserCredentials(
    val accessToken: String,
    val refreshToken: String,
    val expiration: Instant,
    val fgUserId: String
) {
    constructor(row: QueryResultRow) : this(
        accessToken = row.get("spotify_access_token", String::class.java)
            .orElseThrow(),
        refreshToken = row.get("spotify_refresh_token", String::class.java)
            .orElseThrow(),
        expiration = row.get("spotify_access_token_expiration", Long::class.java)
            .map(Instant::ofEpochMilli)
            .orElseThrow(),
        fgUserId = row.get("fg_user_id", String::class.java)
            .orElseThrow()
    )
}

suspend fun lookupSpotifyUserCredentials(fgUserId: String, database: SQLExecutorProvider): SpotifyUserCredentials? =
    sql("SELECT * FROM spotify_user_credentials WHERE fg_user_id = ?;")
        .withArguments(fgUserId)
        .executeQueryAsync(database)
        .await()
        .map(::SpotifyUserCredentials)
        .findFirst()
        .orElse(null)