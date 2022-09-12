package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.queue.MessageDispatcher
import com.duncpro.festivalgarden.queue.QueueMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.request
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class LocalHttpMessageDispatcher(private val serverUrl: String): MessageDispatcher {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun offer(message: QueueMessage, delayMillis: Long) {
        httpClient.request {
            url("${serverUrl}/messages")
            contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            accept(ContentType.Any)
            setBody(message)
            method = io.ktor.http.HttpMethod.Post
        }
    }

    override fun close() {
        httpClient.close()
    }
}