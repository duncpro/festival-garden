package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.festivalwizardscraper.FestivalWizardScraperConfiguration
import com.duncpro.festivalgarden.festivalwizardscraper.runFestivalWizardScraper
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.inputStream

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.local.FillLocalDatabaseMainKt")

fun main() = runBlocking {
    val (h2DatabaseServer, database) = setupLocalDatabase()

    logger.info("Filling local database with data from festival wizard")

    try {
        runFestivalWizardScraper(database, config = FestivalWizardScraperConfiguration(
            spotifyCredentials = System.getenv("SPOTIFY_CREDENTIALS_FILE_PATH")
                .let(::Path)
                .inputStream()
                .use { Json.decodeFromStream(it) },
            scrapeLimit = Int.MAX_VALUE
        ))
    } finally {
        h2DatabaseServer.stop()
    }
}