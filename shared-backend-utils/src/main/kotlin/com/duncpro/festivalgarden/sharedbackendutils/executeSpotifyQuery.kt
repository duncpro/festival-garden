package com.duncpro.festivalgarden.sharedbackendutils

import com.duncpro.festivalgarden.dbmodels.SpotifyUserCredentials
import com.duncpro.festivalgarden.dbmodels.lookupSpotifyUserCredentials
import com.duncpro.festivalgarden.dbmodels.selectUserById
import com.duncpro.festivalgarden.spotify.SpotifyClient
import com.duncpro.festivalgarden.spotify.SpotifyOverloadedException
import com.duncpro.festivalgarden.spotify.SpotifyTokenExpiredException
import com.duncpro.festivalgarden.spotify.SpotifyUserClient
import com.duncpro.jackal.InterpolatableSQLStatement
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.sharedbackendutils.executeSpotifyQueryKt")

sealed interface SpotifyQueryResult<T>
class SpotifyQueryFailedTemporarily<T>(val delayMillis: Long): SpotifyQueryResult<T>
class SpotifyAccountNotFound<T>: SpotifyQueryResult<T>
data class SpotifyQuerySuccess<T>(val result: T): SpotifyQueryResult<T>

suspend fun <T> executeSpotifyQuery(context: ApplicationContext, fgUserId: String,
                                    query: suspend (SpotifyUserClient) -> T): SpotifyQueryResult<T> {
        val user = selectUserById(fgUserId, context.database)
        if (user == null) {
            logger.info("Failed to complete Spotify query because the user's Festival Garden account no longer exists." +
                    " Perhaps it expired, or they deleted it?")
            return SpotifyAccountNotFound<T>()
        }

        val spotifyUserCredentials = lookupSpotifyUserCredentials(user.id.toString(), context.database)
        if (spotifyUserCredentials == null) {
            logger.info("Failed to complete Spotify query for user: $fgUserId because the user does not have an authorized " +
                    "Spotify account associated with their anonymous festival garden identity")
            return SpotifyAccountNotFound<T>()
        }

        try {
            try {
                return SpotifyUserClient(spotifyUserCredentials.accessToken, handleRateLimitInternally = false)
                    .use { spotifyClient -> return@use SpotifyQuerySuccess(query(spotifyClient)) }
            } catch (e: SpotifyTokenExpiredException) {
                logger.info(
                    "Spotify user query failed because the user's authorization token has expired. Attempting" +
                            " to retrieve an updated authorization token from Spotify now...", e
                )
                val newSpotifyUserCredentials: SpotifyUserCredentials =
                    SpotifyClient(context.spotifyAppCredentials, handleRateLimitInternally = false)
                        .use { spotifyAppClient ->
                            val spotifyAuthResponse =
                                spotifyAppClient.refreshUserAuthToken(refreshToken = spotifyUserCredentials.refreshToken)
                            if (spotifyAuthResponse == null) {
                                logger.info(
                                    "Failed to refresh user's Spotify authorization token. The user must have" +
                                            " revoked Festival Garden's authorization."
                                )

                                // An invalid refresh token indicates the account is no longer authorized with
                                // spotify. In such a case, discard the user_spotify_credentials associated
                                // with the refresh token. That way, the next time the user logs in to their
                                // Festival Garden anonymous account, they will be forcefully re-authorized with Spotify.
                                sql("DELETE FROM spotify_user_credentials WHERE fg_user_id = ? AND" +
                                        " spotify_refresh_token = ?")
                                    .withArguments(fgUserId, spotifyUserCredentials.refreshToken)
                                    .executeQueryAsync(context.database)
                                    .await()

                                return SpotifyAccountNotFound<T>()
                            }
                            return@use spotifyUserCredentials.copy(
                                accessToken = spotifyAuthResponse.accessToken,
                                expiration = Instant.now().plusSeconds(spotifyAuthResponse.expiresIn),
                            )
                        }

                sql("UPDATE spotify_user_credentials SET spotify_access_token = ?," +
                            " spotify_access_token_expiration = ? WHERE fg_user_id = ?")
                    .withArguments(newSpotifyUserCredentials.accessToken)
                    .withArguments(newSpotifyUserCredentials.expiration.toEpochMilli())
                    .withArguments(newSpotifyUserCredentials.fgUserId)
                    .executeUpdateAsync(context.database)
                    .await()


                logger.info("Retrieved new authorization token from Spotify (Festival Garden user ${newSpotifyUserCredentials.fgUserId})")

                return SpotifyUserClient(newSpotifyUserCredentials.accessToken,
                    handleRateLimitInternally = false)
                    .use { newSpotifyClient -> query(newSpotifyClient) }
                    .let { queryResponse -> SpotifyQuerySuccess(queryResponse) }
            }
        }  catch (e: SpotifyOverloadedException) {
            logger.info("Failed to complete Spotify query for user: $fgUserId because the Spotify Rate" +
                    " Limit has been hit. This query can be retried in ${e.delayMillis} ms.", e)
            return SpotifyQueryFailedTemporarily<T>(e.delayMillis)
        }
    }
