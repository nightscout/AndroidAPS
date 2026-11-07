package app.aaps.plugins.eversense.protocol

import java.lang.reflect.InvocationTargetException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.mockito.Mockito

class NotificationCompatibilityTest {
    private data class Event(val delay: Long, val bytes: ByteArray)
    private class Timeline(var value: Long = 100) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(value)
        override fun millis(): Long = value
    }
    private fun event(vararg bytes: Int, delay: Long = 10) = Event(delay, bytes.map(Int::toByte).toByteArray())

    @TestFactory fun notificationTraces(): List<DynamicTest> {
        val traces = mutableListOf(
            listOf(event(1, 1, 1, 10), event(1, 1, 1, 10, delay = 200), event(1, 1, 1, 10, delay = 1000)),
            listOf(event(1, 3, 1, 10), event(2, 3, 20), event(2, 3, 20), event(3, 3, 30)),
            listOf(event(1, 3, 1, 10), event(3, 3, 30), event(2, 3, 20), event(1, 1, 1, 40)),
            listOf(event(1, 3, 1, 10), event(2, 4, 20), event(1, 1, 1, 40)),
            listOf(event(1, 2, 1), event(), event(1), event(2, 2)),
            listOf(event(1, 2), event(1, 1, 1), event(1, 0, 1, 42)),
            listOf(event(1, 4, 1, 10), event(1, 2, 1, 20), event(2, 2, 30))
        )
        val random = Random(191365)
        repeat(100) {
            traces.add((0 until 40).map {
                val bytes = ByteArray(random.nextInt(24)).also(random::nextBytes)
                if (bytes.isNotEmpty()) bytes[0] = random.nextInt(8).toByte()
                if (bytes.size > 1) bytes[1] = random.nextInt(7).toByte()
                Event(listOf(0L, 10L, 999L, 1000L, 1001L)[random.nextInt(5)], bytes)
            })
        }
        return traces.mapIndexed { index, trace -> DynamicTest.dynamicTest("trace $index") {
            val type = Class.forName("app.aaps.plugins.eversense.reference.EversenseGattCallback")
            val constructor = type.constructors.single()
            val store = Mockito.mock(constructor.parameterTypes[1]) { invocation ->
                if (invocation.method.name == "restrict") invocation.mock else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
            }
            val reference = constructor.newInstance(Mockito.mock(constructor.parameterTypes[0]), store, 1L)
            val append = type.getDeclaredMethod("accumulateChunk", ByteArray::class.java).apply { isAccessible = true }
            val clock = Timeline()
            val rewritten = NotificationAssembler(clock::millis)
            try {
                PacketTime.withClock(clock) {
                    trace.forEachIndexed { eventIndex, event ->
                        clock.value += event.delay
                        val expected = outcome { append.invoke(reference, event.bytes) as? ByteArray }
                        val actual = outcome { rewritten.accept(event.bytes) }
                        assertEquals(expected, actual, "trace $index event $eventIndex")
                    }
                }
            } finally { type.getMethod("closeSession").invoke(reference) }
        } }
    }
    private fun outcome(action: () -> ByteArray?): String = try {
        action()?.joinToString(",") ?: "incomplete"
    } catch (e: Exception) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        "exception:${cause.javaClass.simpleName}"
    }
}
