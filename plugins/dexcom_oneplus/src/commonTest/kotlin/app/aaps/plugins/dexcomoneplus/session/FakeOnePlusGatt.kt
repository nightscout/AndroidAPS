package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusKeksNotify

/**
 * A GATT client with no radio behind it, for the session rules.
 *
 * [backfillPackets] is the client's backfill queue: what the sensor already sent and the session
 * has not read yet. [dropLinkAfterBackfillReads] makes the link go down after that many reads, the
 * way a real link drops in the middle of a backfill, with the rest still queued.
 */
internal class FakeOnePlusGatt(
    val backfillPackets: ArrayDeque<ByteArray> = ArrayDeque(),
    private val dropLinkAfterBackfillReads: Int = Int.MAX_VALUE,
) : OnePlusGattClient {

    var connected = false
    var connects = 0
        private set
    private var backfillReads = 0
    val controlWrites = mutableListOf<ByteArray>()

    override fun connect(deviceAddress: String, autoConnect: Boolean) {
        connects++
        connected = true
    }

    override fun disconnect() {
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun writeAuthentication(payload: ByteArray?) = Unit

    override fun writeExtraData(payload: ByteArray?) = Unit

    override fun writeControl(payload: ByteArray?) {
        payload?.let { controlWrites.add(it) }
    }

    override fun enableControlNotifications() = Unit

    override fun enableControlIndications() = Unit

    override fun enableBackfillNotifications() = Unit

    override fun isBonded(): Boolean = true

    override fun createBond(): Boolean = true

    override fun removeBond(): Boolean = true

    override fun awaitBondComplete(timeoutMs: Long): Boolean = true

    override fun awaitKeksNotify(timeoutMs: Long): OnePlusKeksNotify? = null

    override fun awaitNotify(timeoutMs: Long): ByteArray? = null

    override fun awaitControlNotify(timeoutMs: Long): ByteArray? = null

    override fun awaitBackfillNotify(timeoutMs: Long): ByteArray? {
        val packet = backfillPackets.removeFirstOrNull()
        backfillReads++
        if (backfillReads >= dropLinkAfterBackfillReads) connected = false
        return packet
    }
}
