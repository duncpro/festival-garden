package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.AsyncSQLTransaction
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.QueryResultRow
import com.duncpro.jackal.SQLExecutorProvider
import com.duncpro.jackal.aws.AuroraServerlessAsyncTransaction
import com.duncpro.jackal.aws.AuroraServerlessDatabase
import com.duncpro.jackal.jdbc.DataSourceWrapper
import com.duncpro.jackal.jdbc.JDBCTransaction
import kotlinx.coroutines.future.await

data class UserLikedArtist(
    val spotifyArtistId: String,
    val fgUserId: String,
    val songCount: Int
) {
    constructor(row: QueryResultRow) : this(
        spotifyArtistId = row.get("spotify_artist_id", String::class.java).orElseThrow(),
        fgUserId = row.get("user_id", String::class.java).orElseThrow(),
        songCount = row.get("song_count", Int::class.java).orElseThrow()
    )
}