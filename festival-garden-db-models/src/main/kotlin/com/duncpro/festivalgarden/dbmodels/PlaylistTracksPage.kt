package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.InterpolatableSQLStatement
import com.duncpro.jackal.QueryResultRow
import com.duncpro.jackal.SQLExecutorProvider
import kotlinx.coroutines.future.await

data class PlaylistTracksPage(
    val fgUserId: String,
    val pageUrl: String,
    val pageId: String
) {
    constructor(row: QueryResultRow) : this(
        fgUserId = row.get("user_id", String::class.java)
            .orElseThrow(),
        pageUrl = row.get("page_url", String::class.java)
            .orElseThrow(),
        pageId = row.get("page_id", String::class.java)
            .orElseThrow()
    )
}

suspend fun insertPlaylistTracksPage(database: SQLExecutorProvider, page: PlaylistTracksPage) {
    // If the page has already been inserted then simply skip over it.
    InterpolatableSQLStatement.sql(
        """
        INSERT INTO playlist_tracks_page (
            user_id,
            page_url,
            page_id
        ) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
    """.trimIndent()
    )
        .withArguments(page.fgUserId, page.pageUrl, page.pageId)
        .executeUpdateAsync(database)
        .await()
}

suspend fun selectPlaylistsTracksPage(database: SQLExecutorProvider, pageId: String): PlaylistTracksPage? {
    return InterpolatableSQLStatement.sql("SELECT * FROM playlist_tracks_page WHERE page_id = ?;")
        .withArguments(pageId)
        .executeQueryAsync(database)
        .await()
        .map(::PlaylistTracksPage)
        .findFirst()
        .orElse(null)
}