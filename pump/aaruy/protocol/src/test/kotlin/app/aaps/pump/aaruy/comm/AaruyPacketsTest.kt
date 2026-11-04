package app.aaps.pump.aaruy.comm

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** The wire format, written out by hand. It does not use the vendor AAR, so it stays when the AAR goes. */
class AaruyPacketsTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun decrypt(frame: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aaruyFrameKey(), "AES"))
        return cipher.doFinal(frame, 3, frame.size - 3)
    }

    @Test
    fun readFrameLayout() {
        val frame = AaruyFrame.buildRead(0x0006, 0x0102)
        assertEquals(0x55.toByte(), frame[0])
        val length = ByteBuffer.wrap(frame, 1, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        assertEquals(frame.size - 3, length)
        assertEquals(0, length % 16)
        // length 5, command 0 read, parameter 6, sequence 0x0102
        assertArrayEquals(bytes(5, 0, 0, 6, 0, 2, 1), decrypt(frame))
    }

    @Test
    fun writeFrameLayout() {
        val frame = AaruyFrame.buildWrite(-3, 7, bytes(0xAA, 0xBB, 0xCC))
        // length 5 + 3, command 1 write, parameter -3, sequence 7, payload
        assertArrayEquals(bytes(8, 0, 1, 0xFD, 0xFF, 7, 0, 0xAA, 0xBB, 0xCC), decrypt(frame))
    }

    @Test
    fun longPayloadTakesMoreBlocks() {
        val payload = ByteArray(100) { it.toByte() }
        val frame = AaruyFrame.buildWrite(14, 1, payload)
        assertEquals(3 + 112, frame.size)
        assertArrayEquals(payload, AaruyFrame.parse(frame)!!.payload)
    }

    @Test
    fun parseGivesParameterAndPayload() {
        val message = AaruyFrame.parse(AaruyFrame.buildWrite(-3, 7, bytes(1, 2)))
        assertNotNull(message)
        assertEquals((-3).toShort(), message!!.paramId)
        assertArrayEquals(bytes(1, 2), message.payload)
        assertEquals(0, AaruyFrame.parse(AaruyFrame.buildRead(9, 1))!!.payload.size)
    }

    @Test
    fun frameThatIsNotFullBlocksIsRefused() {
        val frame = AaruyFrame.buildRead(6, 1)
        assertNull(AaruyFrame.parse(frame.copyOf(frame.size - 1)))
    }

    @Test
    fun controlPacketDependsOnTheProtocolVersion() {
        val packet = ControlPacket(0x01020304, 9, 1)
        assertArrayEquals(bytes(4, 3, 2, 1, 9, 1, 0, 0), packet.toBytes(1))
        assertArrayEquals(bytes(4, 3, 2, 1, 9), packet.toBytes(0))
    }

    @Test
    fun commandPacketsAreLittleEndian() {
        assertArrayEquals(bytes(1, 0, 0, 0, 2, 0xDC, 0x05, 0x1E, 0), TempBasalControlPacket(1, 2, 1500, 30).toBytes())
        assertArrayEquals(bytes(1, 0, 0, 0, 3, 0), BasalControlPacket(1, 3).toBytes())
        assertArrayEquals(bytes(5, 0, 0, 0, 1, 2, 0, 0), HistoryRequestPacket(5, 1, 2).toBytes())
    }

    @Test
    fun bolusSettingHasTotalNowAndDelayInTheirPlaces() {
        val data = BolusSettingPacket().apply {
            bolus = 1500
            bolusNow = 1000
            bolusDelayTime = 30
        }.toBytes()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, data.size)
        assertEquals(1500.toShort(), buffer.getShort(4))
        assertEquals(0.toShort(), buffer.getShort(6))
        assertEquals(0.toShort(), buffer.getShort(8))
        assertEquals(0.toShort(), buffer.getShort(10))
        assertEquals(1000.toShort(), buffer.getShort(12))
        assertEquals(30.toShort(), buffer.getShort(14))
    }

    @Test
    fun basicParamSurvivesARoundTrip() {
        val data = BasicParamPacket().apply {
            settingTime = 77
            precision = 50
            maxBasal = 3000
            maxBolus = 25000
            lowAlert = 15
            pumpType = 4
            timeZoneCorrection = 1
        }.toBytes()
        assertEquals(32, data.size)
        assertEquals(15.toShort(), ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(20))
        val parsed = BasicParamPacket.parse(data)
        assertEquals(77, parsed.settingTime)
        assertEquals(50.toShort(), parsed.precision)
        assertEquals(3000.toShort(), parsed.maxBasal)
        assertEquals(25000, parsed.maxBolus.unsigned())
        assertEquals(15.toShort(), parsed.lowAlert)
        assertEquals(4.toByte(), parsed.pumpType)
        assertEquals(1.toByte(), parsed.timeZoneCorrection)
    }

    @Test
    fun basalProfileSurvivesARoundTrip() {
        val areas = listOf(bytes(0, 0xF4, 0x01), bytes(24, 0x13, 0x01))
        val info = BasalInfoPacket().apply {
            injectPerDay = 12300
            segment = 2
            totalArea = 2
            areaBytes = areas
        }.toBytes()
        assertArrayEquals(bytes(0x0C, 0x30, 2, 2, 0, 0xF4, 0x01, 24, 0x13, 0x01), info)
        val parsed = BasalInfoPacket.parse(info)
        assertEquals(24.toByte(), parsed.areas!![1].startTime)
        assertEquals(275.toShort(), parsed.areas!![1].inject)
    }

    @Test
    fun statusOnBasal() {
        val status = CurrentStatusPacket.parse(bytes(1, 0, 0, 0, 0x96, 0, 1, 3, 0xF4, 0x01, 10, 0, 20, 0))
        assertEquals(150.toShort(), status.remainAmount)
        assertEquals(3.toByte(), status.basal!!.index)
        assertEquals(500.toShort(), status.basal!!.curValue)
        assertEquals(10.toShort(), status.basal!!.remainTime)
        assertEquals(20.toShort(), status.basal!!.runningTime)
        assertEquals(0.toByte(), status.basal!!.basalType)
        assertNull(status.bolus)
    }

    @Test
    fun statusOnBolusWithType() {
        val status = CurrentStatusPacket.parse(bytes(1, 0, 0, 0, 0x96, 0, 3, 2, 0x96, 0, 0, 0, 5, 0, 2))
        assertEquals(2.toByte(), status.bolus!!.state)
        assertEquals(150.toShort(), status.bolus!!.curBolusNow)
        assertEquals(5.toShort(), status.bolus!!.remainTime)
        assertEquals(2.toByte(), status.bolus!!.bolusType)
        assertNull(status.basal)
    }

    @Test
    fun statusPausedAndStopped() {
        for (running in listOf(7, 8)) {
            val status = CurrentStatusPacket.parse(bytes(1, 0, 0, 0, 0x96, 0, running, 1, 9, 0, 0, 0))
            assertEquals(1.toByte(), status.pause!!.index)
            assertEquals(9, status.pause!!.pauseOrStopTime)
        }
    }

    @Test
    fun statusThatEndsEarlyKeepsWhatWasRead() {
        val status = CurrentStatusPacket.parse(bytes(1, 0, 0, 0, 0x96, 0, 1, 3))
        assertEquals(150.toShort(), status.remainAmount)
        assertEquals(1.toByte(), status.runningStatus)
        assertNull(status.basal)
    }

    @Test
    fun errorThatEndsEarlyKeepsWhatWasRead() {
        val error = ErrorInfoPacket.parse(bytes(5))
        assertEquals(5, error.errorType)
        assertEquals(0, error.error)
        assertEquals(129, ErrorInfoPacket.parse(bytes(5, 0x81)).error)
    }

    @Test
    fun historyLogUnits() {
        val log = HistoryLogPacket.parse(
            bytes(1, 0, 1, 0, 1) + bytes(9, 0, 0, 0, 2, 0x64, 0, 30, 0, 0xC8, 0, 2)
        )
        assertEquals(1.toShort(), log.count)
        val unit = log.units.single()
        assertEquals(9, unit.time)
        assertEquals(2.toByte(), unit.type)
        assertEquals(100.toShort(), unit.data)
        assertEquals(30.toShort(), unit.injectTime)
        assertEquals(200.toShort(), unit.delayBolus)
        assertEquals(2.toByte(), unit.reserved)
    }

    @Test
    fun historyLogWithAPartialUnitKeepsTheHeader() {
        val log = HistoryLogPacket.parse(bytes(3, 0, 1, 0, 1, 9, 0, 0))
        assertEquals(3.toShort(), log.count)
        assertEquals(0, log.units.size)
    }

    @Test
    fun dailyHistoryUnits() {
        val daily = HistoryDailyPacket.parse(bytes(1, 7, 0, 0xFF, 0x00, 0x64, 0))
        assertEquals(1.toByte(), daily.count)
        assertEquals(7.toShort(), daily.units.single().date)
        assertEquals(255.toShort(), daily.units.single().dailyTotal)
        assertEquals(100.toShort(), daily.units.single().dailyBolus)
    }

    @Test
    fun reportDeviceSurvivesARoundTrip() {
        val data = ReportDevicePacket().apply {
            settingTime = 1
            deviceType = 6
            version = 1
            serial = 50
        }.toBytes()
        assertArrayEquals(bytes(1, 0, 0, 0, 6, 1, 50, 0), data)
        assertEquals(50.toShort(), ReportDevicePacket.parse(data).serial)
    }

    @Test
    fun valuesAboveTheSignedRangeStayPositive() {
        assertEquals(50000, AaruyByteReader(bytes(0x50, 0xC3)).u16().unsigned())
        assertEquals(200, 200.toByte().unsigned())
        assertEquals(4294967295L, AaruyByteReader(bytes(0xFF, 0xFF, 0xFF, 0xFF)).u32().unsigned())
    }

    @Test
    fun thousandthsAreCutAndNotRounded() {
        assertEquals(1500.toShort(), 1.5.toThousandths())
        assertEquals(1000.toShort(), 1.0009.toThousandths())
    }

    @Test
    fun floatingPointNoiseDoesNotLoseAThousandth() {
        val wrong = ArrayList<String>()
        for (steps in 1..1000) {
            val expected = (steps * 25).toShort()
            val computed = steps * 0.025
            val fromText = "${steps * 25 / 1000}.${(steps * 25 % 1000).toString().padStart(3, '0')}".toDouble()
            if (computed.toThousandths() != expected) wrong.add("computed $computed")
            if (fromText.toThousandths() != expected) wrong.add("text $fromText")
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
