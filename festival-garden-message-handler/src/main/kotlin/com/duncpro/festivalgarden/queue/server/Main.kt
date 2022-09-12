package com.duncpro.festivalgarden.queue.server

import com.duncpro.festivalgarden.dbmodels.UserLibraryPage
import com.duncpro.festivalgarden.dbmodels.insertUserLibraryPage
import com.duncpro.festivalgarden.dbmodels.selectUserLibraryPage
import com.duncpro.festivalgarden.queue.InitializeLibraryProcessor
import com.duncpro.festivalgarden.queue.ProcessLibraryPage
import com.duncpro.festivalgarden.queue.ProcessPlaylistPage
import com.duncpro.festivalgarden.queue.QueueMessage
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyAccountNotFound
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyQueryFailedTemporarily
import com.duncpro.festivalgarden.sharedbackendutils.SpotifyQuerySuccess
import com.duncpro.festivalgarden.sharedbackendutils.executeSpotifyQuery
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.SQLException
import com.duncpro.jackal.SQLExecutorProvider
import com.duncpro.jackal.executeTransaction
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

sealed interface MessageResult
object SuccessfulMessageResult: MessageResult
object MessageFailedPermanently: MessageResult
data class MessageFailedTemporarily(val delayMillis: Long): MessageResult

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.queue.server.MainKt")

const val TRACK_PAGE_LENGTH = 50

suspend fun handleFGQueueMessageMessage(message: QueueMessage, context: ApplicationContext): MessageResult {
    logger.info("Inbound Queue Message: $message")
    val result = when (message) {
        is InitializeLibraryProcessor -> handleInitializeLibraryProcessor(message, context)
        is ProcessLibraryPage -> handleProcessLibraryPage(message, context)
        is ProcessPlaylistPage -> throw IllegalStateException()
    }
    logger.info("Message $result: $message")
    return result
}


@Suppress("LiftReturnOrAssignment")
private suspend fun handleInitializeLibraryProcessor(message: InitializeLibraryProcessor, context: ApplicationContext): MessageResult {
    try {
        // Don't re-index the user's library unnecessarily.
        // If the library index has already been written, return immediately and do not try this message again.
        val isCancelled = sql("SELECT has_written_library_index FROM anonymous_user WHERE id = ?")
            .withArguments(message.userId)
            .executeQueryAsync(context.database)
            .await()
            .map { row -> row.get("has_written_library_index", Boolean::class.java).orElseThrow() }
            .findFirst()
            .orElse(true)

        if (isCancelled) return MessageFailedPermanently

        // Count how many songs the user has saved in their library.
        val likedSongsQueryResult = executeSpotifyQuery(context, message.userId) { spotifyClient ->
            spotifyClient.getLikedSongs(0, 1).total
        }

        when (likedSongsQueryResult) {
            // Could be for any number of reasons, perhaps the user deleted their festival garden account?.
            // Or maybe the user de-authorized festival garden from the Spotify app dashboard. Whatever the
            // case may be, this message should not be tried again.
            is SpotifyAccountNotFound -> return MessageFailedPermanently

            // Festival Garden is operating at capacity in terms of Spotify's rate limit.
            // This message should be retried a little later, when the service is under less pressure.
            is SpotifyQueryFailedTemporarily -> return MessageFailedTemporarily(likedSongsQueryResult.delayMillis)

            is SpotifyQuerySuccess -> {
                // Always save at least one page in the index (even if it is an empty page), so that
                // the REST server can see 1/1 pages have completed, and there is no longer any need to poll.
                val totalPages = max(1, ceil(likedSongsQueryResult.result.toDouble() / TRACK_PAGE_LENGTH).toInt())

                for (i in 0 until totalPages) {
                    // user_library_page has PRIMARY KEY based on user_id and track offset,
                    // page_id is simply provided as a handle for convenience.
                    val newPage = UserLibraryPage(message.userId, i * TRACK_PAGE_LENGTH,
                        UUID.randomUUID().toString())
                    try {
                        // This function inserts the given user library into the database.
                        // If the page already exists, then this function do not throw an exception,
                        // and is simply a NO-OP.
                        insertUserLibraryPage(context.database, newPage)
                        context.queue.offer(ProcessLibraryPage(message.userId, newPage.pageId))
                    } catch (e: SQLException) {
                        // Key conflict is an expected result which occurs when a redundant message is received.
                        if (!e.isKeyConflict) throw e
                    }
                }
            }
        }


        sql("UPDATE anonymous_user SET has_written_library_index = TRUE WHERE id = ?;")
            .withArguments(message.userId)
            .executeUpdateAsync(context.database)
            .await()

//        val playlistsQueryResult = executeSpotifyQuery(context, message.userId) { spotifyClient ->
//            spotifyClient.getPlaylistsPage(0, 1).total
//        }
//
//        when (playlistsQueryResult) {
//            // Could be for any number of reasons, perhaps the user deleted their festival garden account?.
//            // Or maybe the user de-authorized festival garden from the Spotify app dashboard. Whatever the
//            // case may be, this message should not be tried again.
//            is SpotifyAccountNotFound -> return MessageFailedPermanently
//
//            // Festival Garden is operating at capacity in terms of Spotify's rate limit.
//            // This message should be retried a little later, when the service is under less pressure.
//            is SpotifyQueryFailedTemporarily -> return MessageFailedTemporarily(playlistsQueryResult.delayMillis)
//
//            is SpotifyQuerySuccess -> {
//                // Always save at least one page in the index (even if it is an empty page), so that
//                // the REST server can see 1/1 pages have completed, and there is no longer any need to poll.
//                val totalPages = max(1, ceil(playlistsQueryResult.result.toDouble() / TRACK_PAGE_LENGTH).toInt())
//
//                for (i in 0 until totalPages) {
//                    // user_library_page has PRIMARY KEY based on user_id and track offset,
//                    // page_id is simply provided as a handle for convenience.
//                    val newPage = UserLibraryPage(message.userId, i * TRACK_PAGE_LENGTH,
//                        UUID.randomUUID().toString())
//                    try {
//                        insertUserLibraryPage(context.database, newPage)
//                        context.queue.offer(ProcessLibraryPage(message.userId, newPage.pageId))
//                    } catch (e: SQLException) {
//                        // Key conflict is an expected result which occurs when a redundant message is received.
//                        if (!e.isKeyConflict) throw e
//                    }
//                }
//            }
//        }

        return SuccessfulMessageResult

    } catch (e: Exception) {
        logger.warn("Unexpected exception while initializing library processor.", e)
        return MessageFailedPermanently
    }
}

@Suppress("LiftReturnOrAssignment")
private suspend fun handleProcessLibraryPage(message: ProcessLibraryPage, context: ApplicationContext): MessageResult {
    val pageReference = selectUserLibraryPage(context.database, message.pageId)

    if (pageReference == null) {
        logger.info("Cannot process library page because the page referenced in the queue message no longer exists.")
        return MessageFailedPermanently
    }

    val queryResult = executeSpotifyQuery(context, message.userId) { spotifyUserClient ->
        spotifyUserClient.getLikedSongs(pageReference.pageStartTrackOffset, TRACK_PAGE_LENGTH)
    }

    when (queryResult) {
        is SpotifyAccountNotFound -> {
            sql("INSERT INTO user_library_page_result (user_id, page_id, was_successful) VALUES (?, ?, FALSE) " +
                    "ON CONFLICT DO NOTHING;")
                .withArguments(message.userId, message.pageId)
                .executeQueryAsync(context.database)
                .await()
            return MessageFailedPermanently
        }

        // Retry this exact same message after a brief delay
        is SpotifyQueryFailedTemporarily -> return MessageFailedTemporarily(queryResult.delayMillis)

        is SpotifyQuerySuccess -> {
            val spotifyPage = queryResult.result

            // Execute this block within a transaction so that if this is a duplicate message (the page has
            // been processed already) we don't count some songs twice, which would introduce error into
            // the user's personalized artist ranking. If this is a duplicate, an SQL exception will
            // be thrown since their will be a collision on the PRIMARY KEY (user_id, page_id).
            try {
                context.database.executeTransaction {
                    // If processing the page failed previously, then it is OK to process the page again,
                    // since processing a page is an all or nothing operation.
                    sql("DELETE FROM user_library_page_result WHERE user_id = ? AND page_id = ? AND was_successful = FALSE")
                        .withArguments(message.userId, message.pageId)
                        .executeUpdate()

                    sql("INSERT INTO user_library_page_result (user_id, page_id, was_successful) VALUES (?, ?, TRUE)")
                        .withArguments(message.userId, message.pageId)
                        .executeUpdate()

                  // Sort by artist ID to avoid SQL deadlock.
                    spotifyPage.items.asSequence()
                        .flatMap { it.track.album.artists.asSequence() }
                        .sortedBy { it.id }
                        .forEach { artist -> upsertUserLikedArtist(transaction, message.userId, artist.id) }

                    commit()
                }
            } catch (e: SQLException) {
                // Key conflict is an expected result which occurs when a redundant message is received.
                if (!e.isKeyConflict) throw e
            }


            return SuccessfulMessageResult
        }
    }
}

suspend fun upsertUserLikedArtist(database: SQLExecutorProvider, fgUserId: String, spotifyArtistId: String) {
    when (System.getenv("DATABASE_TYPE")) {
        "H2" -> {
            sql("""
                MERGE INTO user_liked_artist USING (VALUES(?, ?)) AS v (user_id, spotify_artist_id)
                    ON v.user_id = user_liked_artist.user_id AND v.spotify_artist_id = user_liked_artist.spotify_artist_id 
                WHEN MATCHED THEN
                    UPDATE SET user_liked_artist.song_count = user_liked_artist.song_count + 1 
                WHEN NOT MATCHED THEN 
                    INSERT (user_id, spotify_artist_id, song_count) VALUES(v.user_id, v.spotify_artist_id, 1);
                """.toString())
                .withArguments(fgUserId, spotifyArtistId)
                .executeUpdateAsync(database)
                .await()
        }
        "POSTGRES" -> {
            sql("""
                INSERT INTO user_liked_artist (user_id, spotify_artist_id, song_count) VALUES (?, ?, 1)
                    ON CONFLICT (user_id, spotify_artist_id) DO UPDATE SET song_count = user_liked_artist.song_count + 1;
            """.trimIndent())
                .withArguments(fgUserId, spotifyArtistId)
                .executeUpdateAsync(database)
                .await()
        }
        else -> throw IllegalStateException()
    }

}