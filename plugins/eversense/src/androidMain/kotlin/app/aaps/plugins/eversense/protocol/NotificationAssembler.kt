package app.aaps.plugins.eversense.protocol

import java.io.ByteArrayOutputStream

/** Reassembles one 365 notification stream without owning Bluetooth, storage or retry policy. */
internal class NotificationAssembler(
    private val now: () -> Long,
    private val notice: (Notice) -> Unit = {}
) {
    enum class Notice { SHORT_HEADER, DUPLICATE, RESTARTED, OUT_OF_ORDER }
    private class Pending(val total: Int, val bytes: ByteArrayOutputStream, var next: Int = 2)
    private var pending: Pending? = null
    private var previous: ByteArray? = null
    private var previousAt = 0L

    fun reset() {
        pending = null
        previous = null
        previousAt = 0
    }

    fun accept(fragment: ByteArray): ByteArray? {
        if (fragment.size < 2) {
            notice(Notice.SHORT_HEADER)
            return null
        }
        val timestamp = now()
        if (timestamp - previousAt < 1000 && previous?.contentEquals(fragment) == true) {
            notice(Notice.DUPLICATE)
            return null
        }
        previous = fragment
        previousAt = timestamp
        val index = fragment[0].toInt() and 255
        val total = fragment[1].toInt() and 255
        val current = if (index == 1) {
            if (pending != null) notice(Notice.RESTARTED)
            // Keep the established malformed-header outcome as well as the valid wire format.
            val body = fragment.copyOfRange(3, fragment.size)
            Pending(total, ByteArrayOutputStream().apply { write(body) }).also { pending = it }
        } else {
            val active = pending
            if (active == null || index != active.next || total != active.total) {
                pending = null
                notice(Notice.OUT_OF_ORDER)
                return null
            }
            active.bytes.write(fragment.copyOfRange(2, fragment.size))
            active.next++
            active
        }
        if (current.next <= current.total) return null
        pending = null
        return current.bytes.toByteArray()
    }
}
