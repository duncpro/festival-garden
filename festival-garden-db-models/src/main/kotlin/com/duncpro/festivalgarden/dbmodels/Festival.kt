package com.duncpro.festivalgarden.dbmodels

import com.duncpro.jackal.QueryResultRow

class Festival(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val startDate: Long,
    val endDate: Long,
    val url: String,
    val id: String,
    val regionName: String?,
    val municipalityName: String?
) {
    constructor(row: QueryResultRow) : this(
        name = row.get("name", String::class.java).orElseThrow(),
        id = row.get("id", String::class.java).orElseThrow(),
        startDate = row.get("start_date", Long::class.java).orElseThrow(),
        endDate = row.get("end_date", Long::class.java).orElseThrow(),
        url = row.get("url", String::class.java).orElseThrow(),
        longitude = row.get("longitude", Double::class.java).orElseThrow(),
        latitude = row.get("latitude", Double::class.java).orElseThrow(),
        regionName = row.get("region_name", String::class.java).orElse(null),
        municipalityName = row.get("municipality_name", String::class.java).orElse(null)
    )
}