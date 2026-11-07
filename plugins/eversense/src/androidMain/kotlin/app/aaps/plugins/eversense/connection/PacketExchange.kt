package app.aaps.plugins.eversense.connection

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Completion is retained even when the response arrives before the sender starts waiting. */
internal class PacketExchange<T>(val packet: T) {
    private val response = CompletableFuture<T>()
    fun complete() = response.complete(packet)
    fun fail(error: Throwable) = response.completeExceptionally(error)
    fun await(timeoutMs: Long): T = response.get(timeoutMs, TimeUnit.MILLISECONDS)
}
