package com.duncpro.festivalgarden.spotify

import io.ktor.client.statement.HttpResponse

class UnexpectedSpotifyAPIException(message: String, val response: HttpResponse? = null):
    RuntimeException(message)