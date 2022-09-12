package com.duncpro.festivalgarden.local

import com.duncpro.festivalgarden.queue.MessageDispatcher
import com.duncpro.festivalgarden.restapi.createApplicationEndpoints
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.SQLDatabase
import com.duncpro.restk.CorsPolicies
import com.duncpro.restk.createRouter
import com.duncpro.restk.sun.httpServerOf
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.local.LocalFestivalGardenAPIServerKt")

class LocalFestivalGardenAPIServer(database: SQLDatabase, spotifyCredentials: SpotifyCredentials): AutoCloseable {

    private val httpServer: HttpServer

    private val messageDispatcher: MessageDispatcher = LocalHttpMessageDispatcher("http://localhost:8196")

    init {
        httpServer = httpServerOf(
            router = createRouter(
                endpoints = createApplicationEndpoints(ApplicationContext(database, spotifyCredentials, messageDispatcher)),
                corsPolicy = CorsPolicies.public()
            ),
            address = InetSocketAddress(8084)
        )
        httpServer.start()
        logger.info("Local Server Started")
    }

    override fun close() {
        httpServer.stop(0)
        messageDispatcher.close()
        logger.info("Local Server Shutdown Gracefully")
    }
}