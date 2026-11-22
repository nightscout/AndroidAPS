package app.aaps.plugins.eversense.protocol

import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Joining the 365's notification chunks into messages, for fixed and random traces with gaps around the one-second
 * limit, against digests of what the first driver produced (see [GoldenDigests]).
 */
class NotificationGoldenTest {

    private val golden = GoldenDigests("notification")

    private class Event(val delay: Long, val bytes: ByteArray)

    private fun event(vararg bytes: Int, delay: Long = 10) = Event(delay, bytes.map(Int::toByte).toByteArray())

    private fun outcome(action: () -> ByteArray?): String = try {
        action()?.joinToString(",") ?: "incomplete"
    } catch (e: Exception) {
        "exception:${e.javaClass.simpleName}"
    }

    @Test fun messagesAreJoinedAsInTheFirstDriver() {
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
        traces.forEachIndexed { index, trace ->
            var now = 100L
            val assembler = NotificationAssembler(now = { now })
            PacketTime.withClock({ now }) {
                for (event in trace) {
                    now += event.delay
                    golden.add(if (index < 7) "trace $index" else "random traces", outcome { assembler.accept(event.bytes) })
                }
            }
        }
        golden.check()
    }
}
