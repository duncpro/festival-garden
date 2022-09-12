package com.duncpro.festivalgarden.restapi

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.duncpro.festivalgarden.queue.MessageDispatcher
import com.duncpro.festivalgarden.queue.SQSMessageDispatcher
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.aws.AuroraServerlessCredentials
import com.duncpro.jackal.aws.AuroraServerlessDatabase
import com.duncpro.jroute.rest.HttpMethod
import com.duncpro.restk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.rdsdata.RdsDataAsyncClient
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

class LambdaRequestHandler: com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(platformRequest: APIGatewayProxyRequestEvent, lambdaContext: Context): APIGatewayProxyResponseEvent {
        val rdsClient = RdsDataAsyncClient.create()
        val database: SQLDatabase = AuroraServerlessDatabase(rdsClient, AuroraServerlessCredentials(
            System.getenv("PRIMARY_DB_RESOURCE_ARN"), System.getenv("PRIMARY_DB_SECRET_ARN")))
        val spotifyCredentials = SpotifyCredentials(
            spotifyClientId = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
            spotifyClientSecret = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
        )
        val messageDispatcher: MessageDispatcher = SQSMessageDispatcher(System.getenv("PRIMARY_QUEUE_URL"))

        return messageDispatcher.use {
            rdsClient.use {
                runBlocking {
                    handlePlatformRequest(platformRequest, ApplicationContext(database, spotifyCredentials,
                        messageDispatcher), lambdaContext)
                }
            }
        }
    }
}


private suspend fun handlePlatformRequest(
    platformRequest: APIGatewayProxyRequestEvent,
    applicationContext: ApplicationContext,
    lambdaContext: Context
): APIGatewayProxyResponseEvent  {
        val response = com.duncpro.restk.handleRequest(
            method = HttpMethod.valueOf(platformRequest.httpMethod.uppercase()),
            path = platformRequest.path,
            query = platformRequest.multiValueQueryStringParameters ?: emptyMap(),
            header = platformRequest.multiValueHeaders ?: emptyMap(),
            body = kotlin.run {
                if (platformRequest.body == null) return@run EmptyRequestBody
                val isBase64Encoded = platformRequest.isBase64Encoded
                val bytes = if (isBase64Encoded) {
                    Base64.getDecoder().decode(platformRequest.body)
                } else {
                    platformRequest.body.toByteArray()
                }
                MemoryRequestBody(ByteBuffer.wrap(bytes))
            },
            router = createRouter(
                endpoints = createApplicationEndpoints(applicationContext).asSequence()
                    .map { createTimeConstrainedEndpoint(it, Instant.now()
                        .plusMillis(lambdaContext.remainingTimeInMillis.toLong())) }
                    .toSet(),
                // Despite AWS API Gateway supporting automatic preflight requests, it does not support
                // automatic attachment of CORS headers to the actual request/response body.
                // Therefore, it is necessary to use RestK's CORS support as-well.
                corsPolicy = CorsPolicies.public()
            )
        )

        val platformResponse = APIGatewayProxyResponseEvent()

        platformResponse.statusCode = response.statusCode
        platformResponse.body = response.body?.collect()?.let { String(it) }
        platformResponse.multiValueHeaders = response.header
        platformResponse.isBase64Encoded = false

        return platformResponse
}

private fun createTimeConstrainedEndpoint(endpoint: RestEndpoint, deadline: Instant?) = wrapEndpoint(endpoint) { req ->
    coroutineScope {
        val response = async { endpoint.handler(req) }

        if (deadline != null) {
            GlobalScope.launch {
                delay(deadline.toEpochMilli() - Instant.now().toEpochMilli())
                response.cancel()
            }
        }

        try {
            response.await()
        } catch (e: CancellationException) {
            responseOf {
                statusCode = 503
                header("Retry-After", 0)
            }
        }

    }
}