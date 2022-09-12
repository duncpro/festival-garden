package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.queue.QueueMessage
import com.duncpro.festivalgarden.queue.server.MessageFailedTemporarily
import com.duncpro.festivalgarden.queue.server.handleFGQueueMessageMessage
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jroute.rest.HttpMethod
import com.duncpro.restk.ContentTypes
import com.duncpro.restk.CorsPolicies
import com.duncpro.restk.RestEndpoint
import com.duncpro.restk.asString
import com.duncpro.restk.body
import com.duncpro.restk.createRouter
import com.duncpro.restk.responseOf
import com.duncpro.restk.sun.httpServerOf
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress

class LocalMessageBroker(private val database: SQLDatabase, private val spotifyCredentials: SpotifyCredentials): AutoCloseable {
    private val server: HttpServer
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        val channel = Channel<QueueMessage>(Channel.UNLIMITED)
        server = httpServerOf(
            router = createRouter(
                endpoints = setOf(
                    RestEndpoint(
                        method = HttpMethod.POST,
                        route = "/messages",
                        consumeContentType = setOf(ContentTypes.Application.JSON),
                        produceContentType = null,
                        handler = { request ->
                            val body = request.body.asString()
                            val message = Json.decodeFromString<QueueMessage>(body)
                            channel.send(message)
                            responseOf { statusCode = 200 }
                        }
                    )
                ),
                corsPolicy = CorsPolicies.public()
            ),
            address = InetSocketAddress(8196)
        )

        coroutineScope.launch {
            val localDispatcher = LocalHttpMessageDispatcher("http://localhost:8196")
            localDispatcher.use {
                channel.consumeAsFlow()
                    .map { message -> message to handleFGQueueMessageMessage(message, ApplicationContext(database, spotifyCredentials, localDispatcher)) }
                    .onEach { (message, result) -> if (result is MessageFailedTemporarily) channel.send(message) }
                    .collect()
            }
        }


        server.start()
    }

    override fun close() {
        coroutineScope.cancel()
        server.stop(0)
    }
}