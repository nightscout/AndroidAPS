package com.nightscout.eversense.packets.e3

import com.nightscout.eversense.enums.CalibrationPhase
import com.nightscout.eversense.enums.CalibrationReadiness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetCalibrationPhasePacketTest {

    private fun makePacket(vararg bytes: Int): GetCalibrationPhasePacket {
        val packet = GetCalibrationPhasePacket()
        packet.appendData(bytes.map { it.toUByte() }.toUByteArray())
        return packet
    }

    @Test
    fun `empty receivedData returns null`() {
        val packet = GetCalibrationPhasePacket()
        assertNull(packet.parseResponse())
    }

    @Test
    fun `value 1 parses to WARMING_UP`() {
        val packet = makePacket(0, 0, 0, 0, 1)
        assertEquals(CalibrationPhase.WARMING_UP, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 2 parses to DAILY_CALIBRATION`() {
        val packet = makePacket(0, 0, 0, 0, 2)
        assertEquals(CalibrationPhase.DAILY_CALIBRATION, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 3 parses to INITIALIZATION`() {
        val packet = makePacket(0, 0, 0, 0, 3)
        assertEquals(CalibrationPhase.INITIALIZATION, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 4 parses to SUSPICIOUS`() {
        val packet = makePacket(0, 0, 0, 0, 4)
        assertEquals(CalibrationPhase.SUSPICIOUS, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 5 parses to UNKNOWN`() {
        val packet = makePacket(0, 0, 0, 0, 5)
        assertEquals(CalibrationPhase.UNKNOWN, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 6 parses to DEBUG`() {
        val packet = makePacket(0, 0, 0, 0, 6)
        assertEquals(CalibrationPhase.DEBUG, packet.parseResponse()?.phase)
    }

    @Test
    fun `value 7 parses to DROPOUT`() {
        val packet = makePacket(0, 0, 0, 0, 7)
        assertEquals(CalibrationPhase.DROPOUT, packet.parseResponse()?.phase)
    }

    @Test
    fun `unknown value falls back to UNKNOWN`() {
        val packet = makePacket(0, 0, 0, 0, 99)
        assertEquals(CalibrationPhase.UNKNOWN, packet.parseResponse()?.phase)
    }

    @Test
    fun `annotation uses SingleByte command id`() {
        val annotation = GetCalibrationPhasePacket().getAnnotation()!!
        assertEquals(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, annotation.requestId)
    }

    @Test
    fun `annotation uses SingleByte response id`() {
        val annotation = GetCalibrationPhasePacket().getAnnotation()!!
        assertEquals(EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId, annotation.responseId)
    }
}

class GetCalibrationReadinessPacketTest {

    private fun makePacket(vararg bytes: Int): GetCalibrationReadinessPacket {
        val packet = GetCalibrationReadinessPacket()
        packet.appendData(bytes.map { it.toUByte() }.toUByteArray())
        return packet
    }

    @Test
    fun `empty receivedData returns null`() {
        val packet = GetCalibrationReadinessPacket()
        assertNull(packet.parseResponse())
    }

    @Test
    fun `value 0 parses to READY`() {
        val packet = makePacket(0, 0, 0, 0, 0)
        assertEquals(CalibrationReadiness.READY, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 1 parses to NOT_ENOUGH_DATA`() {
        val packet = makePacket(0, 0, 0, 0, 1)
        assertEquals(CalibrationReadiness.NOT_ENOUGH_DATA, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 2 parses to GLUCOSE_TOO_HIGH`() {
        val packet = makePacket(0, 0, 0, 0, 2)
        assertEquals(CalibrationReadiness.GLUCOSE_TOO_HIGH, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 3 parses to TOO_SOON`() {
        val packet = makePacket(0, 0, 0, 0, 3)
        assertEquals(CalibrationReadiness.TOO_SOON, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 4 parses to DROPOUT_PHASE`() {
        val packet = makePacket(0, 0, 0, 0, 4)
        assertEquals(CalibrationReadiness.DROPOUT_PHASE, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 5 parses to SENSOR_EOL`() {
        val packet = makePacket(0, 0, 0, 0, 5)
        assertEquals(CalibrationReadiness.SENSOR_EOL, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 6 parses to NO_SENSOR_LINKED`() {
        val packet = makePacket(0, 0, 0, 0, 6)
        assertEquals(CalibrationReadiness.NO_SENSOR_LINKED, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 7 parses to UNSUPPORTED_MODE`() {
        val packet = makePacket(0, 0, 0, 0, 7)
        assertEquals(CalibrationReadiness.UNSUPPORTED_MODE, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 8 parses to CALIBRATING`() {
        val packet = makePacket(0, 0, 0, 0, 8)
        assertEquals(CalibrationReadiness.CALIBRATING, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 9 parses to LED_DISCONNECT_DETECTED`() {
        val packet = makePacket(0, 0, 0, 0, 9)
        assertEquals(CalibrationReadiness.LED_DISCONNECT_DETECTED, packet.parseResponse()?.readiness)
    }

    @Test
    fun `value 10 parses to TRANSMITTER_EOL`() {
        val packet = makePacket(0, 0, 0, 0, 10)
        assertEquals(CalibrationReadiness.TRANSMITTER_EOL, packet.parseResponse()?.readiness)
    }

    @Test
    fun `unknown value falls back to UNKNOWN`() {
        val packet = makePacket(0, 0, 0, 0, 99)
        assertEquals(CalibrationReadiness.UNKNOWN, packet.parseResponse()?.readiness)
    }

    @Test
    fun `annotation uses SingleByte command id`() {
        val annotation = GetCalibrationReadinessPacket().getAnnotation()!!
        assertEquals(EversenseE3Packets.ReadSingleByteSerialFlashRegisterCommandId, annotation.requestId)
    }

    @Test
    fun `annotation uses SingleByte response id`() {
        val annotation = GetCalibrationReadinessPacket().getAnnotation()!!
        assertEquals(EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId, annotation.responseId)
    }
}
