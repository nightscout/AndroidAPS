package app.aaps.pump.medtronic.data.dto

import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.PumpBolusType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for Medtronic DTO classes
 */
class MedtronicDTOUTest : MedtronicTestBase() {


    @BeforeEach
    fun setup() {
        super.initializeCommonMocks()
    }

    // BolusDTO Tests
    @Test
    fun `test BolusDTO with normal bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 5.0,
            deliveredAmount = 5.0,
            duration = 0
        )
        bolusDTO.bolusType = PumpBolusType.Normal

        assertThat(bolusDTO.requestedAmount).isWithin(0.01).of(5.0)
        assertThat(bolusDTO.deliveredAmount).isWithin(0.01).of(5.0)
        assertThat(bolusDTO.duration).isEqualTo(0)
        assertThat(bolusDTO.bolusType).isEqualTo(PumpBolusType.Normal)
        assertThat(bolusDTO.value).isEqualTo("5.00")
    }

    @Test
    fun `test BolusDTO with extended bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 8.0,
            deliveredAmount = 8.0,
            duration = 90 // 90 minutes = 1:30
        )
        bolusDTO.bolusType = PumpBolusType.Extended

        assertThat(bolusDTO.bolusType).isEqualTo(PumpBolusType.Extended)
        assertThat(bolusDTO.value).contains("AMOUNT_SQUARE=8.00")
        assertThat(bolusDTO.value).contains("DURATION=01:30")
    }

    @Test
    fun `test BolusDTO with multiwave bolus`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 10.0,
            deliveredAmount = 6.0,
            duration = 120 // 2 hours
        )
        bolusDTO.bolusType = PumpBolusType.Multiwave
        bolusDTO.immediateAmount = 4.0

        assertThat(bolusDTO.immediateAmount).isWithin(0.01).of(4.0)
        assertThat(bolusDTO.value).contains("AMOUNT=4.00")
        assertThat(bolusDTO.value).contains("AMOUNT_SQUARE=6.00")
        assertThat(bolusDTO.value).contains("DURATION=02:00")
    }

    @Test
    fun `test BolusDTO displayable value formatting`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 8.0,
            deliveredAmount = 8.0,
            duration = 60
        )
        bolusDTO.bolusType = PumpBolusType.Extended

        val displayable = bolusDTO.displayableValue

        assertThat(displayable).contains("Amount Square:")
        assertThat(displayable).contains("Duration:")
        assertThat(displayable).doesNotContain("AMOUNT_SQUARE=")
        assertThat(displayable).doesNotContain("DURATION=")
    }

    @Test
    fun `test BolusDTO toString`() {
        val bolusDTO = BolusDTO(
            atechDateTime = 1514766900000L,
            requestedAmount = 5.0,
            deliveredAmount = 5.0
        )
        bolusDTO.bolusType = PumpBolusType.Audio

        assertThat(bolusDTO.toString()).contains("BolusDTO")
        assertThat(bolusDTO.toString()).contains("Audio")
    }

    // TempBasalPair Tests
    @Test
    fun `test TempBasalPair with absolute rate from single byte`() {
        // Rate byte 0x50 = 80, 80 * 0.025 = 2.0 U/hr
        // Time byte 0x04 = 4, 4 * 30 = 120 minutes
        val tempBasal = TempBasalPair(0x50.toByte(), 4, isPercent = false)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(2.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(120)
        assertThat(tempBasal.isPercent).isFalse()
        assertThat(tempBasal.description).contains("Rate: 2.000 U")
        assertThat(tempBasal.description).contains("Duration: 120 min")
    }

    @Test
    fun `test TempBasalPair with percent rate`() {
        // Rate byte 0x4B = 75% (signed byte range)
        // Time byte 0x02 = 2, 2 * 30 = 60 minutes
        val tempBasal = TempBasalPair(0x4B.toByte(), 2, isPercent = true)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(75.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(60)
        assertThat(tempBasal.isPercent).isTrue()
        assertThat(tempBasal.description).contains("Rate: 75%")
    }

    @Test
    fun `test TempBasalPair with two byte rate`() {
        // For 40-stroke pumps: rate from two bytes
        val tempBasal = TempBasalPair(0x32.toByte(), 0x00.toByte(), 6, isPercent = false)

        assertThat(tempBasal.insulinRate).isWithin(0.01).of(1.25) // 0x0032 = 50, 50 * 0.025 = 1.25
        assertThat(tempBasal.durationMinutes).isEqualTo(180) // 6 * 30 = 180 minutes
        assertThat(tempBasal.isPercent).isFalse()
    }

    @Test
    fun `test TempBasalPair from pump response with absolute rate`() {
        // Response: [type, percent_value, stroke_hi, stroke_lo, duration_hi, duration_lo]
        // Type 0x00 = absolute, strokes 0x0050 = 80, 80/40 = 2.0 U/hr, duration 0x0078 = 120 min
        val response = ByteUtil.createByteArrayFromString("00 00 00 50 00 78")

        val tempBasal = TempBasalPair(aapsLogger, response)

        assertThat(tempBasal.isPercent).isFalse()
        assertThat(tempBasal.insulinRate).isWithin(0.01).of(2.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `test TempBasalPair from pump response with percent rate`() {
        // Response: [type, percent_value, stroke_hi, stroke_lo, duration]
        // Type 0x01 = percent, value 120%, duration 60 min
        val response = ByteUtil.createByteArrayFromString("01 78 00 00 3C")

        val tempBasal = TempBasalPair(aapsLogger, response)

        assertThat(tempBasal.isPercent).isTrue()
        assertThat(tempBasal.insulinRate).isWithin(0.01).of(120.0)
        assertThat(tempBasal.durationMinutes).isEqualTo(60)
    }

    @Test
    fun `test TempBasalPair cancel TBR detection`() {
        val cancelTBR = TempBasalPair(0.0, false, 0)

        assertThat(cancelTBR.isCancelTBR).isTrue()
        assertThat(cancelTBR.isZeroTBR).isFalse()
        assertThat(cancelTBR.description).isEqualTo("Cancel TBR")
    }

    @Test
    fun `test TempBasalPair zero TBR detection`() {
        val zeroTBR = TempBasalPair(0.0, false, 30)

        assertThat(zeroTBR.isZeroTBR).isTrue()
        assertThat(zeroTBR.isCancelTBR).isFalse()
    }

    @Test
    fun `test TempBasalPair toString`() {
        val tempBasal = TempBasalPair(1.5, false, 90)

        val str = tempBasal.toString()

        assertThat(str).contains("TempBasalPair")
        assertThat(str).contains("Rate=1.5")
        assertThat(str).contains("DurationMinutes=90")
        assertThat(str).contains("IsPercent=false")
    }

    // BatteryStatusDTO Tests
    @Test
    fun `test BatteryStatusDTO with normal status and voltage`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal
        batteryStatus.voltage = 1.5
        batteryStatus.extendedDataReceived = true

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)
        assertThat(batteryStatus.voltage).isWithin(0.01).of(1.5)
        assertThat(batteryStatus.extendedDataReceived).isTrue()
    }

    @Test
    fun `test BatteryStatusDTO calculated percent for alkaline battery`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.335 // Mid-range for alkaline
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        // Alkaline: lowVoltage=1.20, highVoltage=1.47
        // Expected: (1.335 - 1.20) / (1.47 - 1.20) = 0.135 / 0.27 = 0.5 = 50%
        assertThat(percent).isEqualTo(50)
    }

    @Test
    fun `test BatteryStatusDTO calculated percent for lithium battery`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.59
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Lithium)

        // Lithium: lowVoltage=1.22, highVoltage=1.64
        // Expected: (1.59 - 1.22) / (1.64 - 1.22) = 0.37 / 0.42 = 0.88 = 88%
        assertThat(percent).isAtLeast(87)
        assertThat(percent).isAtMost(89)
    }

    @Test
    fun `test BatteryStatusDTO percent clamped to 1-100 range`() {
        val batteryStatus = BatteryStatusDTO()

        // Test voltage below range (should return 1%)
        batteryStatus.voltage = 0.5
        assertThat(batteryStatus.getCalculatedPercent(BatteryType.Alkaline)).isEqualTo(1)

        // Test voltage above range (should return 100%)
        batteryStatus.voltage = 2.0
        assertThat(batteryStatus.getCalculatedPercent(BatteryType.Alkaline)).isEqualTo(100)
    }

    @Test
    fun `test BatteryStatusDTO with null voltage returns default percent`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = null
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        assertThat(percent).isEqualTo(70) // Default for Normal status
    }

    @Test
    fun `test BatteryStatusDTO with low status and null voltage`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = null
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low

        val percent = batteryStatus.getCalculatedPercent(BatteryType.Alkaline)

        assertThat(percent).isEqualTo(18) // Default for Low status
    }

    @Test
    fun `test BatteryStatusDTO with None battery type returns default`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.5
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val percent = batteryStatus.getCalculatedPercent(BatteryType.None)

        assertThat(percent).isEqualTo(70) // Default when battery type is None
    }

    @Test
    fun `test BatteryStatusDTO toString includes all battery types`() {
        val batteryStatus = BatteryStatusDTO()
        batteryStatus.voltage = 1.4
        batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal

        val str = batteryStatus.toString()

        assertThat(str).contains("BatteryStatusDTO")
        assertThat(str).contains("voltage=1.40")
        assertThat(str).contains("alkaline=")
        assertThat(str).contains("lithium=")
        assertThat(str).contains("niZn=")
        assertThat(str).contains("nimh=")
    }

    @Test
    fun `test BatteryStatusDTO with all battery status types`() {
        val normalStatus = BatteryStatusDTO()
        normalStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal
        assertThat(normalStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)

        val lowStatus = BatteryStatusDTO()
        lowStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low
        assertThat(lowStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Low)

        val unknownStatus = BatteryStatusDTO()
        unknownStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown
        assertThat(unknownStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Unknown)
    }

    // ClockDTO Tests
    @Test
    fun `test ClockDTO creation and properties`() {
        val localTime = org.joda.time.LocalDateTime(2019, 1, 30, 14, 35, 20)
        val pumpTime = org.joda.time.LocalDateTime(2019, 1, 30, 14, 30, 15)

        val clockDTO = ClockDTO(localTime, pumpTime)

        assertThat(clockDTO.localDeviceTime).isEqualTo(localTime)
        assertThat(clockDTO.pumpTime).isEqualTo(pumpTime)
        assertThat(clockDTO.timeDifference).isEqualTo(0) // Default value
    }

    @Test
    fun `test ClockDTO with time difference`() {
        val localTime = org.joda.time.LocalDateTime(2019, 1, 30, 14, 35, 20)
        val pumpTime = org.joda.time.LocalDateTime(2019, 1, 30, 14, 30, 15)

        val clockDTO = ClockDTO(localTime, pumpTime)
        clockDTO.timeDifference = 305 // 5 minutes and 5 seconds

        assertThat(clockDTO.timeDifference).isEqualTo(305)
    }

    // BasalProfileEntry Tests
    @Test
    fun `test BasalProfileEntry default constructor`() {
        val entry = BasalProfileEntry()

        assertThat(entry.rate).isWithin(0.01).of(-9.999E6)
        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(0))
        assertThat(entry.startTime_raw).isEqualTo(0xFF.toByte())
    }

    @Test
    fun `test BasalProfileEntry with rate and time`() {
        // Create entry for 1.25 U/hr starting at 06:00
        val entry = BasalProfileEntry(1.25, 6, 0)

        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(6, 0))
        assertThat(entry.startTime_raw).isEqualTo(12.toByte()) // 6 hours * 2 = 12 (30-min intervals)
    }

    @Test
    fun `test BasalProfileEntry with half hour interval`() {
        // Create entry for 0.75 U/hr starting at 08:30
        val entry = BasalProfileEntry(0.75, 8, 30)

        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(8, 30))
        assertThat(entry.startTime_raw).isEqualTo(17.toByte()) // (8 * 2) + 1 = 17
    }

    @Test
    fun `test BasalProfileEntry from strokes`() {
        // 50 strokes * 0.025 = 1.25 U/hr, interval 12 = 06:00
        val entry = BasalProfileEntry(aapsLogger, 50, 12)

        assertThat(entry.rate).isWithin(0.01).of(1.25)
        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(6, 0))
        assertThat(entry.startTime_raw).isEqualTo(12.toByte())
    }

    @Test
    fun `test BasalProfileEntry from byte with even interval`() {
        // 40 strokes (as byte) * 0.025 = 1.0 U/hr, interval 0 = 00:00
        val entry = BasalProfileEntry(40.toByte(), 0)

        assertThat(entry.rate).isWithin(0.01).of(1.0)
        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(0, 0))
    }

    @Test
    fun `test BasalProfileEntry from byte with odd interval`() {
        // 30 strokes * 0.025 = 0.75 U/hr, interval 23 = 11:30
        val entry = BasalProfileEntry(30.toByte(), 23)

        assertThat(entry.rate).isWithin(0.01).of(0.75)
        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(11, 30))
    }

    @Test
    fun `test BasalProfileEntry time calculation for midnight`() {
        val entry = BasalProfileEntry(1.0, 0, 0)

        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(0, 0))
        assertThat(entry.startTime_raw).isEqualTo(0.toByte())
    }

    @Test
    fun `test BasalProfileEntry time calculation for noon`() {
        val entry = BasalProfileEntry(1.5, 12, 0)

        assertThat(entry.startTime).isEqualTo(org.joda.time.LocalTime(12, 0))
        assertThat(entry.startTime_raw).isEqualTo(24.toByte()) // 12 * 2 = 24
    }
}
