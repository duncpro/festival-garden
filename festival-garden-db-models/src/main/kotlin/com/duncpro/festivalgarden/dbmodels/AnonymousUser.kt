package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.QueryResultRow
import com.duncpro.jackal.SQLExecutorProvider
import kotlinx.coroutines.future.await
import java.time.Instant
import java.util.*

data class AnonymousUser(
    val id: UUID,
    val token: String,
    val tokenExpiration: Instant,
    val spotifyStateArg: String?,
    val hasWrittenLibraryIndex: Boolean
) {
    constructor(row: QueryResultRow) : this(
        id = UUID.fromString(row.get("id", String::class.java).orElseThrow()),
        token = row.get("token", String::class.java).orElseThrow(),
        tokenExpiration = Instant.ofEpochMilli(row.get("token_expiration", Long::class.java).orElseThrow()),
        spotifyStateArg = row.get("spotify_state_arg", String::class.java).orElse(null),
        hasWrittenLibraryIndex = row.get("has_written_library_index", Boolean::class.java).orElseThrow()
    )
}


suspend fun selectUserById(userId: String, database: SQLExecutorProvider): AnonymousUser? =
    sql("SELECT * FROM anonymous_user WHERE id = ?")
        .withArguments(userId)
        .executeQueryAsync(database)
        .await()
        .map(::AnonymousUser)
        .findFirst()
        .orElse(null)