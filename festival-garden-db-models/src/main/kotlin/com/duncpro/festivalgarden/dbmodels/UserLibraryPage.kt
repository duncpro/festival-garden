package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.QueryResultRow
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.SQLExecutorProvider
import kotlinx.coroutines.future.await

data class UserLibraryPage(
    val fgUserId: String,
    val pageStartTrackOffset: Int,
    val pageId: String
) {
    constructor(row: QueryResultRow) : this(
        fgUserId = row.get("user_id", String::class.java)
            .orElseThrow(),
        pageStartTrackOffset = row.get("page_start_track_offset", Int::class.java)
            .orElseThrow(),
        pageId = row.get("page_id", String::class.java)
            .orElseThrow()
    )
}

suspend fun insertUserLibraryPage(database: SQLExecutorProvider, page: UserLibraryPage) {
    // If the page has already been inserted then simply skip over it.
    sql("""
        INSERT INTO user_library_page (
            user_id,
            page_start_track_offset,
            page_id
        ) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;
    """.trimIndent())
        .withArguments(page.fgUserId, page.pageStartTrackOffset, page.pageId)
        .executeUpdateAsync(database)
        .await()
}

suspend fun selectUserLibraryPage(database: SQLExecutorProvider, pageId: String): UserLibraryPage? {
    return sql("SELECT * FROM user_library_page WHERE page_id = ?;")
        .withArguments(pageId)
        .executeQueryAsync(database)
        .await()
        .map(::UserLibraryPage)
        .findFirst()
        .orElse(null)
}