package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A backfill that loses its link half way must still store every record that already arrived.
 *
 * The backfill session is shared code, so these run on the JVM host.
 */
class OnePlusBackfillSessionTest {

    /** One G7 backfill record: dexTime | glucose (12 bit) | state | flags | trend, little endian. */
    private fun record(dexTime: Int, mgdl: Int): ByteArray = byteArrayOf(
        (dexTime and 0xff).toByte(),
        ((dexTime shr 8) and 0xff).toByte(),
        ((dexTime shr 16) and 0xff).toByte(),
        ((dexTime shr 24) and 0xff).toByte(),
        (mgdl and 0xff).toByte(),
        ((mgdl shr 8) and 0x0f).toByte(),
        0x06, // Ok
        0x00,
        0x00,
    )

    private val currentDex = 100_000

    private fun packets() = ArrayDeque(
        listOf(
            record(currentDex - 900, 120),
            record(currentDex - 600, 125),
            record(currentDex - 300, 130),
        ),
    )

    private fun run(gatt: FakeOnePlusGatt): List<OnePlusGlucoseSample> {
        val delivered = mutableListOf<OnePlusGlucoseSample>()
        gatt.connected = true
        OnePlusBackfillSession(gatt = gatt, onGlucose = { delivered.add(it) })
            .runOnce(currentDexTimeSeconds = currentDex, shouldContinue = { gatt.isConnected() })
        return delivered
    }

    @Test
    fun `records still queued when the link drops are stored too`() {
        // The link goes down right after the first packet is read. Two more are already queued.
        val gatt = FakeOnePlusGatt(backfillPackets = packets(), dropLinkAfterBackfillReads = 1)
        val delivered = run(gatt)
        assertEquals(listOf(120.0, 125.0, 130.0), delivered.map { it.mgdl })
        assertEquals(0, gatt.backfillPackets.size)
    }

    @Test
    fun `the drop does not change the values or their spacing`() {
        val gatt = FakeOnePlusGatt(backfillPackets = packets(), dropLinkAfterBackfillReads = 1)
        val delivered = run(gatt)
        // 5 minutes apart on the sensor clock, 5 minutes apart here.
        assertEquals(300_000L, delivered[1].timestampMs - delivered[0].timestampMs)
        assertEquals(300_000L, delivered[2].timestampMs - delivered[1].timestampMs)
    }

    @Test
    fun `a link that drops before anything arrived stores nothing`() {
        val gatt = FakeOnePlusGatt(dropLinkAfterBackfillReads = 1)
        assertEquals(emptyList(), run(gatt))
    }
}
