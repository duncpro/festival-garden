package com.duncpro.festivalgarden.queue

import kotlinx.coroutines.future.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.Duration

class SQSMessageDispatcher(private val queueUrl: String): MessageDispatcher {
    private val sqsClient = SqsAsyncClient.create()

    override suspend fun offer(message: QueueMessage, delayMillis: Long) {
        sqsClient.sendMessage(SendMessageRequest.builder()
            .messageBody(Json.encodeToString(message))
            .delaySeconds((delayMillis / 1000).toInt())
            .queueUrl(queueUrl)
            .build())
            .await()
    }

    override fun close() {
        sqsClient.close()
    }
}