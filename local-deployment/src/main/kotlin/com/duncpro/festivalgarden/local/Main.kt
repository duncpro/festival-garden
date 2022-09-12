package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.spotify.SpotifyClient
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.compileSQLScript
import com.duncpro.jackal.executeTransaction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.IOException
import java.util.Scanner
import kotlin.io.path.Path
import kotlin.io.path.inputStream

fun main() = runBlocking {
    val (h2DatabaseServer, database) = setupLocalDatabase()

    val spotifyCredentials = SpotifyCredentials(
        System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
        System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET")
    )
    val localMessageBroker = LocalMessageBroker(database, spotifyCredentials)

    // Launch Local Festival Garden API Server
    val localApiServer = LocalFestivalGardenAPIServer(database, spotifyCredentials)

    try {
        Scanner(System.`in`).use { scanner ->
            while (scanner.hasNextLine()) {
                if (scanner.nextLine().equals("exit")) {
                    break
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }

    sql("SHUTDOWN".toString()).executeUpdate(database)

    // Cleanup
    localApiServer.close()
    localMessageBroker.close()
    h2DatabaseServer.stop()
    localMessageBroker.close()
}