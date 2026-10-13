package app.aaps.pump.medtronic.comm

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.data.dto.BatteryStatusDTO
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/**
 * Created by andy on 1/30/19.
 * Updated to JUnit 5 / Kotlin
 */
class MedtronicConverterUTest : MedtronicTestBase() {


    lateinit var converter: MedtronicConverter

    @BeforeEach
    fun setup() {
        super.initializeCommonMocks()
        converter = MedtronicConverter(aapsLogger, medtronicUtil)
    }

    @Test
    fun `test decodeModel with valid 554 pump`() {
        val data = ByteUtil.createByteArrayFromString("03 35 35 34")

        val model = converter.decodeModel(data)

        assertThat(model).isEqualTo(MedtronicDeviceType.Medtronic_554_Veo)
    }

    @Test
    fun `test decodeModel with valid 722 pump`() {
        val data = ByteUtil.createByteArrayFromString("03 37 32 32")

        val model = converter.decodeModel(data)

        assertThat(model).isEqualTo(MedtronicDeviceType.Medtronic_722)
    }

    @Test
    fun `test decodeModel with valid 523 pump`() {
        val data = ByteUtil.createByteArrayFromString("03 35 32 33")

        val model = converter.decodeModel(data)

        assertThat(model).isEqualTo(MedtronicDeviceType.Medtronic_523_Revel)
    }

    @Test
    fun `test decodeModel with short data returns Unknown`() {
        val data = ByteUtil.createByteArrayFromString("03 16")

        val model = converter.decodeModel(data)

        assertThat(model).isEqualTo(MedtronicDeviceType.Unknown_Device)
    }

    @Test
    fun `test decodeBatteryStatus with normal status`() {
        val data = ByteUtil.createByteArrayFromString("00 00 7C")

        val batteryStatus = converter.decodeBatteryStatus(data)

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)
        assertThat(batteryStatus.voltage).isWithin(0.01).of(1.24) // 0x007C = 124, 124/100 = 1.24
        assertThat(batteryStatus.extendedDataReceived).isTrue()
    }

    @Test
    fun `test decodeBatteryStatus with low battery`() {
        val data = ByteUtil.createByteArrayFromString("01 00 50")

        val batteryStatus = converter.decodeBatteryStatus(data)

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Low)
        assertThat(batteryStatus.voltage).isWithin(0.01).of(0.80) // 0x0050 = 80, 80/100 = 0.80
    }

    @Test
    fun `test decodeBatteryStatus with 2-byte voltage`() {
        val data = ByteUtil.createByteArrayFromString("00 7C")

        val batteryStatus = converter.decodeBatteryStatus(data)

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)
        assertThat(batteryStatus.voltage).isWithin(0.01).of(1.24) // 124/100 = 1.24
        assertThat(batteryStatus.extendedDataReceived).isTrue()
    }

    @Test
    fun `test decodeBatteryStatus with only status byte`() {
        val data = ByteUtil.createByteArrayFromString("00")

        val batteryStatus = converter.decodeBatteryStatus(data)

        assertThat(batteryStatus.batteryStatusType).isEqualTo(BatteryStatusDTO.BatteryStatusType.Normal)
        assertThat(batteryStatus.extendedDataReceived).isFalse()
    }

    @Test
    fun `test decodeRemainingInsulin with 10 stroke pump`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_522_722)
        val data = ByteUtil.createByteArrayFromString("00 32") // 0x0032 = 50

        val remaining = converter.decodeRemainingInsulin(data)

        assertThat(remaining).isWithin(0.01).of(5.0) // 50 / 10 = 5.0
    }

    @Test
    fun `test decodeRemainingInsulin with 40 stroke pump`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_554_Veo)
        val data = ByteUtil.createByteArrayFromString("00 00 00 C8") // Skip 2 bytes, 0x00C8 = 200

        val remaining = converter.decodeRemainingInsulin(data)

        assertThat(remaining).isWithin(0.01).of(5.0) // 200 / 40 = 5.0
    }

    @Test
    fun `test decodeTime with valid pump time`() {
        // Time: 14:35:20, Date: 2019-01-30 (year offset from 1984)
        val data = ByteUtil.createByteArrayFromString("0E 23 14 00 23 01 1E")

        val dateTime = converter.decodeTime(data)

        assertThat(dateTime).isNotNull()
        assertThat(dateTime!!.hourOfDay).isEqualTo(14)
        assertThat(dateTime.minuteOfHour).isEqualTo(35)
        assertThat(dateTime.secondOfMinute).isEqualTo(20)
        assertThat(dateTime.year).isEqualTo(2019) // (0x23 & 0x3F) + 1984 = 35 + 1984 = 2019
        assertThat(dateTime.monthOfYear).isEqualTo(1)
        assertThat(dateTime.dayOfMonth).isEqualTo(30)
    }

    @Test
    fun `test decodeTime with short data returns null`() {
        val data = ByteUtil.createByteArrayFromString("0E 23 14")

        val dateTime = converter.decodeTime(data)

        assertThat(dateTime).isNull()
    }

    @Test
    fun `test decodeTime with invalid date returns null`() {
        // Invalid month (13)
        val data = ByteUtil.createByteArrayFromString("0E 23 14 00 23 0D 1E")

        val dateTime = converter.decodeTime(data)

        assertThat(dateTime).isNull()
    }

    @Test
    fun `test decodeSettings with 522 pump configuration`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        // Sample settings data for Medtronic 522/722
        val data = ByteUtil.createByteArrayFromString(
            "08 " +      // [0] Auto-off timeout: 8 hours
            "02 " +      // [1] Alarm beep volume: 2
            "01 " +      // [2] Audio bolus enabled: Yes
            "0A " +      // [3] Audio bolus step: 10 (1.0U)
            "01 " +      // [4] Variable bolus enabled: Yes
            "32 " +      // [5] Max bolus: 50 (5.0U)
            "00 28 " +   // [6-7] Max basal: 40 (1.0U/hr for 522/722)
            "01 " +      // [8] Clock mode: 24h
            "00 " +      // [9] Insulin concentration: 100
            "01 " +      // [10] Basal profiles enabled: Yes
            "00 " +      // [11] Active basal profile: STD
            "01 " +      // [12] RF enabled: Yes
            "00 " +      // [13] Block enabled: No
            "00 " +      // [14] Temp basal type: Units
            "00 " +      // [15] Temp basal percent
            "01 " +      // [16] Paradigm link enabled: Yes
            "00 " +      // [17] Insulin action type: Fast
            "00 " +      // [18] Reservoir warning type: Units
            "0A " +      // [19] Reservoir warning point: 10U
            "00 " +      // [20] Keypad locked: No
            "01 " +      // [21] Bolus scroll step: 1
            "00 " +      // [22] Capture event: No
            "01 " +      // [23] Other device enabled: Yes
            "00"         // [24] Other device paired: No
        )

        val settings = converter.decodeSettings(data)

        assertThat(settings).isNotEmpty()
        assertThat(settings["PCFG_AUTOOFF_TIMEOUT"]?.value).isEqualTo("8")
        assertThat(settings["PCFG_AUDIO_BOLUS_ENABLED"]?.value).isEqualTo("Yes")
        assertThat(settings["PCFG_MAX_BOLUS"]?.value).isEqualTo("5.0")
        assertThat(settings["PCFG_MAX_BASAL"]?.value).isEqualTo("1.0")
        assertThat(settings["CFG_BASE_CLOCK_MODE"]?.value).isEqualTo("24h")
        assertThat(settings["PCFG_BASAL_PROFILES_ENABLED"]?.value).isEqualTo("Yes")
        assertThat(settings["PCFG_ACTIVE_BASAL_PROFILE"]?.value).isEqualTo("STD")
        assertThat(settings["PCFG_TEMP_BASAL_TYPE"]?.value).isEqualTo("Units")
    }

    @Test
    fun `test decodeSettings with percent temp basal type`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        val data = ByteUtil.createByteArrayFromString(
            "08 02 01 0A 01 32 00 28 01 00 01 00 01 00 " +
            "01 " +      // [14] Temp basal type: Percent (1 = percent)
            "78 " +      // [15] Temp basal percent: 120%
            "01 00 00 0A 00 01 00 01 00"
        )

        val settings = converter.decodeSettings(data)

        assertThat(settings["PCFG_TEMP_BASAL_TYPE"]?.value).isEqualTo("Percent")
        assertThat(settings["PCFG_TEMP_BASAL_PERCENT"]?.value).isEqualTo("120")
    }

    @Test
    fun `test decodeSettings with alarm mode silent`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        val data = ByteUtil.createByteArrayFromString(
            "08 " +      // [0] Auto-off timeout
            "04 " +      // [1] Alarm mode: 4 = Silent
            "01 0A 01 32 00 28 01 00 01 00 01 00 00 00 01 00 00 0A 00 01 00 01 00"
        )

        val settings = converter.decodeSettings(data)

        assertThat(settings["PCFG_ALARM_MODE"]?.value).isEqualTo("Silent")
        // PCFG_ALARM_BEEP_VOLUME should not be set when mode is silent
        assertThat(settings.containsKey("PCFG_ALARM_BEEP_VOLUME")).isFalse()
    }

    @Test
    fun `test decodeBasalProfile with valid data`() {
        val pumpType = PumpType.MEDTRONIC_522_722
        val data = ByteUtil.createByteArrayFromString(
            "32 00 00 " +  // Entry 1: rate=0x0032, start=00:00
            "2C 00 0C " +  // Entry 2: rate=0x002C, start=06:00
            "00 00 00"     // Terminator
        )

        val basalProfile = converter.decodeBasalProfile(pumpType, data)

        assertThat(basalProfile).isNotNull()
        assertThat(basalProfile!!.verify(pumpType)).isTrue()
    }

    @Test
    fun `test decodeBasalProfile with invalid data returns null`() {
        val pumpType = PumpType.MEDTRONIC_522_722
        // Create invalid data with time interval that would cause exception (99 = 49.5 hours, invalid)
        val data = ByteUtil.createByteArrayFromString(
            "32 00 63 " +  // rate=0x0032, start=0x63 (99 intervals = invalid time)
            "00 00 00"
        )

        val basalProfile = converter.decodeBasalProfile(pumpType, data)

        // Should return null for invalid profile that throws exception during parsing
        assertThat(basalProfile).isNull()
    }

    @Test
    fun `test decodeSettingsLoop for simple configuration`() {
        whenever(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_522_722)

        val data = ByteUtil.createByteArrayFromString(
            "08 02 01 0A 01 32 00 28 01 00 " +
            "01 " +      // [10] Basal profiles enabled: Yes
            "01 " +      // [11] Active basal profile: A
            "01 00 00 00 01 00 00 0A 00"
        )

        val settings = converter.decodeSettingsLoop(data)

        assertThat(settings).isNotEmpty()
        assertThat(settings["PCFG_ACTIVE_BASAL_PROFILE"]?.value).isEqualTo("A")
        assertThat(settings["PCFG_BASAL_PROFILES_ENABLED"]?.value).isEqualTo("Yes")
    }
}
