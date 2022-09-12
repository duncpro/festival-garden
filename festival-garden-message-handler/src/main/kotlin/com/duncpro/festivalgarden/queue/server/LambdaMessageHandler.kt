package com.duncpro.festivalgarden.queue.server

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.duncpro.festivalgarden.queue.MessageDispatcher
import com.duncpro.festivalgarden.queue.QueueMessage
import com.duncpro.festivalgarden.queue.SQSMessageDispatcher
import com.duncpro.festivalgarden.sharedbackendutils.ApplicationContext
import com.duncpro.festivalgarden.spotify.SpotifyCredentials
import com.duncpro.jackal.SQLDatabase
import com.duncpro.jackal.aws.AuroraServerlessCredentials
import com.duncpro.jackal.aws.AuroraServerlessDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.rdsdata.RdsDataAsyncClient
import java.lang.Exception
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors
import kotlin.concurrent.timer

private val logger = LoggerFactory.getLogger("com.duncpro.festivalgarden.queue.server.LambdaMessageHandler")

class LambdaMessageHandler: RequestHandler<SQSEvent, SQSBatchResponse> {
    override fun handleRequest(input: SQSEvent, lambdaContext: Context): SQSBatchResponse {
        val rdsClient = RdsDataAsyncClient.create()
        val database: SQLDatabase = AuroraServerlessDatabase(rdsClient, AuroraServerlessCredentials(
            System.getenv("PRIMARY_DB_RESOURCE_ARN"), System.getenv("PRIMARY_DB_SECRET_ARN")))
        val spotifyCredentials = SpotifyCredentials(
            spotifyClientId = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
            spotifyClientSecret = System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
        )
        val messageDispatcher: MessageDispatcher = SQSMessageDispatcher(System.getenv("PRIMARY_QUEUE_URL"))
        val applicationContext = ApplicationContext(database, spotifyCredentials, messageDispatcher)
        val queue: Queue<QueueMessage> = input.records
            .map { Json.decodeFromString<QueueMessage>(it.body) }
            .toCollection(LinkedList())

        runBlocking {
            val job = launch {
                rdsClient.use {
                    messageDispatcher.use {
                        try {
                            while (queue.isNotEmpty()) {
                                val message = queue.peek()
                                val result = processMessageSafely(message, applicationContext)
                                redirectOrDispose(message, result, applicationContext.queue)
                                queue.remove()
                            }
                        } catch (e: CancellationException) {
                            withContext(NonCancellable) {
                                resubmitRemaining(queue, messageDispatcher)
                            }
                        }
                    }
                }
            }

            GlobalScope.launch {
                delay(lambdaContext.remainingTimeInMillis - 5000L)
                job.cancel()
            }

            withContext(NonCancellable) { job.join() }
        }

        return SQSBatchResponse.builder().build()
    }
}

private suspend fun resubmitRemaining(queue: Queue<QueueMessage>, dispatcher: MessageDispatcher) {
    coroutineScope {
        while (queue.isNotEmpty()) {
            val cancelledMessage = queue.remove()
            launch { redirectOrDispose(cancelledMessage, MessageFailedTemporarily(0), dispatcher) }
        }
    }

}

private suspend fun redirectOrDispose(message: QueueMessage, result: MessageResult, dispatcher: MessageDispatcher) {
    when (result) {
        is MessageFailedTemporarily -> dispatcher.offer(message, result.delayMillis)
        SuccessfulMessageResult, MessageFailedPermanently -> return
    }
}

private suspend fun processMessageSafely(message: QueueMessage, applicationContext: ApplicationContext): MessageResult {
    try {
        return handleFGQueueMessageMessage(message, applicationContext)
    } catch (e: Exception) {
        if (e is CancellationException) return MessageFailedTemporarily(0) // Lambda timeout probably
        logger.warn("Unhandled exception occurred while processing message. Cancelling this message" +
                " permanently.", e)
        return MessageFailedPermanently
    }
}