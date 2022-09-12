package com.duncpro.festivalgarden.queue

interface MessageDispatcher: AutoCloseable {
    suspend fun offer(message: QueueMessage, delayMillis: Long = 0)

    override fun close()
}