package com.duncpro.festivalgarden.festivalwizardscraper

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

val RelaxedJson = Json { ignoreUnknownKeys = true }

enum class LoopResult { CONTINUE, BREAK }

inline fun loop(action: () -> LoopResult) {
    var isNotBroken = true
    while (isNotBroken) {
        if (action() == LoopResult.BREAK) isNotBroken = false
    }
}

object URLSerializer: KSerializer<URL> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(URLSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): URL = URL(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }
}

fun <T> countElements(label: String? = null): (T) -> Unit  {
    val counter = AtomicInteger(0)
    return { _ -> println("${label ?: ""} ${counter.addAndGet(1)}") }
}

private object RateLimiterPermit

interface RateLimiter {
    fun close()
    suspend fun acquirePermit()
}

/**
 * Creates a new rate limiter with the specified parameters.
 *
 * @param [permitsPerTick] a function returning the number of permits which should be made available
 *  during some "tick". Each tick is described by some time interval, after that time interval has ended
 *  all unused permits are revoked, and a new tick begins. This function is invoked each time
 *  a new tick begins.
 * @param [tickInterval] a function returning the minimal duration of some tick. This function is invoked each time
 *  a new tick begins, that tick will have a duration equal to this function's return value.
 */
fun CoroutineScope.RateLimiter(permitsPerTick: () -> Int, tickInterval: () -> Duration): RateLimiter {
    val permits = Channel<RateLimiterPermit>(Channel.UNLIMITED)

    val revokeUnusedPermits = {
        loop {
            if (permits.tryReceive().isSuccess) return@loop LoopResult.CONTINUE
            return@loop LoopResult.BREAK
        }
    }

    val issuer = launch {
        loop {
            if (!isActive) return@loop LoopResult.BREAK
            revokeUnusedPermits()
            repeat(permitsPerTick()) { permits.send(RateLimiterPermit) }
            delay(tickInterval().toMillis())
            return@loop LoopResult.CONTINUE
        }
    }

    return object : RateLimiter {
        override fun close() = issuer.cancel()

        override suspend fun acquirePermit() { permits.receive() }
    }
}

fun CoroutineScope.RateLimiter(tickInterval: () -> Duration): RateLimiter = RateLimiter({1}, tickInterval)

object InstantToUnixEpochSerializer: KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(InstantToUnixEpochSerializer::class.qualifiedName!!,
        PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
}
