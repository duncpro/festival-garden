package com.duncpro.festivalgarden.restapi

import com.duncpro.festivalgarden.dbmodels.AnonymousUser
import com.duncpro.jackal.InterpolatableSQLStatement
import com.duncpro.jackal.InterpolatableSQLStatement.sql
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.executeTransaction
import com.duncpro.restk.RestException
import com.duncpro.restk.RestRequest
import com.duncpro.restk.asString
import com.duncpro.restk.header
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.restapi.Auth")

/**
 * The maximum lifetime of a Festival Garden Anonymous User Account.
 * After this duration has passed (since the anonymous account was created) the
 * client associated with the account will no longer be able to authenticate using
 * the access token. The temporary account will be deleted after its lifetime has
 * expired. The user is free to create another anonymous account and reconnect with
 * Spotify any number of times.
 */
val ANONYMOUS_ACCOUNT_LIFETIME = Duration.ofDays(14)

val ALPHABET_LOWER: Set<Char> = setOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
    'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')

val ALPHABET_UPPER: Set<Char> = ALPHABET_LOWER.asSequence()
    .map { it.uppercaseChar() }
    .toSet()

val ALPHABET_BOTH_CASES = ALPHABET_LOWER union ALPHABET_UPPER
val ALPHANUMERIC_BOTH_CASES = ALPHABET_BOTH_CASES union setOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')

val secureRandomDataGenerator = SecureRandom()

fun mintRandomKey(length: Int): String {
    val allowedTokenCharacters = ALPHANUMERIC_BOTH_CASES.toList()
    var freshlyMintedToken = ""
    while (freshlyMintedToken.length < length) {
        val nextCharIndex = secureRandomDataGenerator.nextInt(allowedTokenCharacters.size)
        freshlyMintedToken += allowedTokenCharacters[nextCharIndex]
    }
    return freshlyMintedToken
}

fun mintAuthToken(): String = mintRandomKey(255)
fun mintSpotifyStateArg(): String = mintRandomKey(16)

fun RestRequest.bearerToken(): String = bearerTokenOrNull() ?: throw RestException(statusCode = 401,
    message = "This endpoint requires the user to be authenticated, but no bearer token was prevent.")

fun RestRequest.bearerTokenOrNull(): String? {
    val value = header["authorization"]?.firstOrNull() ?: return null
    if (!value.startsWith("Bearer ")) throw RestException(
        statusCode = 400,
        message = "Expected Authorization Header to have value beginning with \"Bearer \""
    )
    return value.replaceFirst("Bearer ", "")
}

suspend fun authenticateOrNull(bearerToken: String, database: SQLDatabase): AnonymousUser? {
    logger.info("Verifying authentication token: $bearerToken")
    val user = sql("SELECT * FROM anonymous_user WHERE token = ?;")
        .withArguments(bearerToken)
        .executeQueryAsync(database)
        .await()
        .map(::AnonymousUser)
        .findFirst()
        .orElse(null)

    if (user == null) {
        logger.info("Authentication token is fake, ignoring it.")
        return null
    }

    if (user.tokenExpiration.isBefore(Instant.now())) {
        logger.info("Auth token expired, can not authenticate")
        return null
    }
    return user
}

suspend fun authenticate(bearerToken: String, database: SQLDatabase): AnonymousUser
    = authenticateOrNull(bearerToken, database) ?: throw RestException(401)

suspend fun issueNewIdentity(database: SQLDatabase): AnonymousUser {
    val newUser = AnonymousUser(
        id = UUID.randomUUID(),
        token = mintAuthToken(),
        tokenExpiration = Instant.now().plus(ANONYMOUS_ACCOUNT_LIFETIME),
        spotifyStateArg = null,
        hasWrittenLibraryIndex = false
    )

    database.executeTransaction {
        sql("""
            INSERT INTO anonymous_user (
                id,
                token,
                token_expiration,
                has_written_library_index
            ) VALUES (?, ?, ?, ?);
            """)
            .withArguments(newUser.id.toString(), newUser.token, newUser.tokenExpiration.toEpochMilli())
            .withArguments(newUser.hasWrittenLibraryIndex)
            .executeUpdateAsync(database)
            .await()
        commit()
    }

    logger.info("Issued new identity to client: $newUser")

    return newUser
}

val SPOTIFY_AUTH_SCOPE: List<String> = listOf("user-library-read", "user-read-private", "user-read-email")