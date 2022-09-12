package com.duncpro.festivalgarden.queue.server

import com.duncpro.jackal.SQLException
import software.amazon.awssdk.services.rdsdata.model.BadRequestException

val SQLException.sqlStateCode: String? get() {
    val cause = this.cause
    if (cause is BadRequestException) {
        val message = cause.message ?: return null
        val match = "SQLState: "
        val fieldStartIndex = message.indexOf(match)
        if (fieldStartIndex == -1) return null
        val valueStartIndex = fieldStartIndex + match.length
        return message.substring(valueStartIndex, valueStartIndex + 5)
    }
    if (cause is java.sql.SQLException) {
        return cause.sqlState
    }
    return null
}

val SQLException.isKeyConflict: Boolean get() = this.sqlStateCode == "23505"