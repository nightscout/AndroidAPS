package app.aaps.plugins.eversense.connection

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class PacketExchangeTest {
    @Test fun responseBeforeWaitingIsNotLost() {
        val packet = Any()
        val exchange = PacketExchange(packet)
        exchange.complete()
        assertSame(packet, exchange.await(1))
    }
    @Test fun missingResponseHasBoundedTimeout() {
        assertThrows(TimeoutException::class.java) { PacketExchange(Any()).await(1) }
    }
    @Test fun teardownFailureUnblocksWaitAndLateResponseCannotOverwriteIt() {
        val exchange = PacketExchange(Any())
        val cause = IllegalStateException("session closed")
        exchange.fail(cause)
        assertFalse(exchange.complete())
        val error = assertThrows(ExecutionException::class.java) { exchange.await(1) }
        assertSame(cause, error.cause)
    }
    @Test fun oldResponseCannotCompleteAnotherExchange() {
        val old = PacketExchange(Any())
        assertThrows(TimeoutException::class.java) { old.await(1) }
        val next = PacketExchange(Any())
        old.complete()
        assertThrows(TimeoutException::class.java) { next.await(1) }
    }
}
