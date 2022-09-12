package com.duncpro.festivalgarden.festivalwizardscraper

import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.InterpolatableSQLStatement
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.compileSQLScript
import com.duncpro.jackal.executeTransaction
import com.duncpro.jackal.jdbc.DataSourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readText

class SmokeTest {
    private lateinit var databaseFile: File
    private lateinit var database: SQLDatabase

    private suspend fun initializeDatabaseTables() = database.executeTransaction {
        val initScriptFilePath = Path(System.getenv("DATABASE_INIT_SCRIPT_PATH"))
        compileSQLScript(initScriptFilePath.inputStream())
            .onEach { it.executeUpdate() }
            .collect()
        commit()
    }

    @BeforeEach
    fun setupDatabase() {
        databaseFile = File.createTempFile("fest-garden-test", "db")
        databaseFile.deleteOnExit()
        val dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:${databaseFile.absolutePath}")
        database = DataSourceWrapper(Dispatchers.IO.asExecutor(), dataSource)
        runBlocking { initializeDatabaseTables() }
    }

    @AfterEach
    fun teardownDatabase() {
        sql("SHUTDOWN".toString()).executeUpdate(database)
    }


    @Test
    fun runSmokeTest() = runBlocking {
        val spotifyCredentials: SpotifyCredentials = System.getenv("SPOTIFY_CREDENTIALS_FILE_PATH")
            .let(::Path)
            .inputStream()
            .use { Json.decodeFromStream(it) }

        val config = FestivalWizardScraperConfiguration(spotifyCredentials, 2)
        runFestivalWizardScraper(database, config)
    }
}
