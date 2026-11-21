package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.packets.e365.GetGlucoseDataPacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseLogValuesPacket
import app.aaps.plugins.eversense.packets.e365.GetSensorInformationPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The 365 decoding that was changed on purpose after the frozen snapshot, so [ProtocolCompatibilityTest] leaves these
 * answers out. A placeholder must never be stored as glucose, and a text the parser does not need must not stop a sync.
 */
class DecodingFixesTest {

    private val now = 1_735_689_610_000L
    private val sensorIdLength = 10

    /** A current-glucose answer: glucose at 20+L, signal at 22+L, the "unavailable" reason at 24+L, trend at 164+L. */
    private fun glucoseFrame(glucose: Int, unavailable: Int = 0): ByteArray = ByteArray(192).also { frame ->
        frame[0] = 0x42; frame[1] = 0x1f; frame[11] = sensorIdLength.toByte()
        ByteBuffer.wrap(frame, 12 + sensorIdLength, 8).order(ByteOrder.LITTLE_ENDIAN).putLong((now - 946_684_800_000L) / 1000 * 1024)
        frame[20 + sensorIdLength] = glucose.toByte(); frame[21 + sensorIdLength] = (glucose ushr 8).toByte()
        frame[22 + sensorIdLength] = 55
        frame[24 + sensorIdLength] = unavailable.toByte(); frame[25 + sensorIdLength] = (unavailable ushr 8).toByte()
        frame[164 + sensorIdLength] = 4
    }

    private fun current(frame: ByteArray) = GetGlucoseDataPacket(sensorIdLength).apply { appendData(frame.toUByteArray()) }.parseResponse()

    @Test fun aCurrentReadingIsTakenFromTheLowerLimitToBelow450() {
        assertEquals(20, current(glucoseFrame(20))?.glucoseInMgDl)
        assertEquals(100, current(glucoseFrame(100))?.glucoseInMgDl)
        assertEquals(449, current(glucoseFrame(449))?.glucoseInMgDl)
    }

    @Test fun aPlaceholderIsNotAReading() {
        assertNull(current(glucoseFrame(0)))
        assertNull(current(glucoseFrame(19)))
        assertNull(current(glucoseFrame(450)))
    }

    @Test fun aReadingTheTransmitterMarksAsUnavailableIsNotTaken() {
        assertNull(current(glucoseFrame(100, unavailable = 3)))
    }

    @Test fun aHistoryEntryBelowTheLowerLimitIsSkipped() {
        val record = 193
        val glucoseAt = sensorIdLength + 8 + 4
        val frame = ByteArray(7 + 2 * record).also { it[6] = 13 }
        for ((index, glucose) in listOf(0, 120).withIndex()) {
            val start = 7 + index * record
            frame[start + glucoseAt] = glucose.toByte(); frame[start + glucoseAt + 1] = (glucose ushr 8).toByte()
        }
        val history = GetGlucoseLogValuesPacket(from = 0, to = 1, sensorIdLength = sensorIdLength)
            .apply { appendData(frame.toUByteArray()) }.parseResponse()
        assertEquals(listOf(120), history?.glucoseHistory?.map { it.valueInMgDl })
    }

    /** The protocol version is text such as "01.06"; another form ("1.10.2") used to throw, and the sync with it. */
    @Test fun sensorInformationDoesNotDependOnTheProtocolVersionText() {
        val frame = ByteArray(139 + 2 * sensorIdLength).also { frame ->
            "1.10.2".toByteArray().copyInto(frame, 69)
            frame[103] = sensorIdLength.toByte()
            frame[114 + 2 * sensorIdLength] = 62
        }
        val info = GetSensorInformationPacket().apply { appendData(frame.toUByteArray()) }.parseResponse()
        assertNotNull(info)
        assertEquals(62, info!!.batteryLevel)
    }
}
