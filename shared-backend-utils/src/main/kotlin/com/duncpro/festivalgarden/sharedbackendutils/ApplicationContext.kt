package com.duncpro.festivalgarden.sharedbackendutils

import com.duncpro.festivalgarden.queue.MessageDispatcher
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.SQLDatabase

data class ApplicationContext(
    val database: SQLDatabase,
    val spotifyAppCredentials: SpotifyCredentials,
    val queue: MessageDispatcher
)