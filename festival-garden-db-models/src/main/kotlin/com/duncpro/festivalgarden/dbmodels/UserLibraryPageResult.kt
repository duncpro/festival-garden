package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.QueryResultRow

data class UserLibraryPageResult(
    val userId: String,
    val pageId: String,
    val wasSuccessful: Boolean
) {
    constructor(row: QueryResultRow) : this(
        userId = row.get("user_id", String::class.java).orElseThrow(),
        pageId = row.get("page_id", String::class.java).orElseThrow(),
        wasSuccessful = row.get("was_successful", Boolean::class.java).orElseThrow()
    )
}