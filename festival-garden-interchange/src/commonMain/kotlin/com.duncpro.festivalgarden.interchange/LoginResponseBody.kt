package com.duncpro.festivalgarden.interchange

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseBody(
    /**
     * The Festival Garden authentication token associated with the client.
     * This is not the Spotify authorization token. The Spotify authorization token is not shared with the client.
     */
    val festivalGardenAuthToken: String,

    /**
     * The presence of a redirect URL indicates that the account is not properly authorized with Spotify,
     * and the player needs to enter their Spotify username and password before they can begin using
     * Festival Garden. If a redirect URL is present, the client should redirect the user's web browser
     * to the redirect URL.
     */
    val redirect: String?
)