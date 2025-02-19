package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.connectivity.commands.device.UpdateSettingsV1
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.getUnsignedInt
import app.aaps.pump.apex.utils.getUnsignedShort
import app.aaps.pump.apex.utils.hexAsDecToDec
import app.aaps.pump.apex.utils.toBoolean
import org.joda.time.DateTime

class StatusV1(command: PumpCommand): PumpObjectModel() {
    /** Pump approximate battery level */
    val batteryLevel = BatteryLevel.entries.find { it.raw == command.objectData[2] }

    /** Alarm type */
    val alarmType = AlarmType.entries.find { it.raw == command.objectData[3] }

    /** Bolus delivery speed */
    val deliverySpeed = BolusDeliverySpeed.entries.find { it.raw == command.objectData[4] }

    /** Screen brightness */
    val brightness = ScreenBrightness.entries.find { it.raw == command.objectData[5] }

    private val bolusFlags = command.objectData[6].toUByte().toInt()
    private enum class BolusFlags(val raw: Int) {
        AdvancedBolusEnabled(1 shl 1)
    }

    /** Are dual and extended bolus types enabled? */
    val advancedBolusEnabled = (bolusFlags and BolusFlags.AdvancedBolusEnabled.raw) == 1

    /** Keys lock enabled? */
    val keyboardLockEnabled = command.objectData[7].toBoolean()

    /** Pump auto-suspend enabled? */
    val autoSuspendEnabled = command.objectData[8].toBoolean()

    /** Time for auto-suspend to trigger, in 30 minute steps */
    val autoSuspendDuration = command.objectData[9].toUByte().toInt()

    /** Low reservoir alarm threshold in 1U steps */
    val lowReservoirThreshold = command.objectData[10].toUByte().toInt()

    /** Low reservoir alarm (triggered by time left) threshold in 30 minute steps */
    val lowReservoirTimeLeftThreshold = command.objectData[11].toUByte().toInt()

    // Byte 10, 11 unknown

    /** Current basal pattern index */
    val currentBasalPattern = command.objectData[14].toUByte().toInt()

    /** Is TDD limit enabled? */
    val totalDailyDoseLimitEnabled = command.objectData[15].toBoolean()

    /** Screen disable timeout, in 0.1s steps */
    val screenTimeout = getUnsignedShort(command.objectData, 16)

    /** Current TDD */
    val totalDailyDose = getUnsignedInt(command.objectData, 18)

    /** TDD alarm threshold */
    val maxTDD = getUnsignedInt(command.objectData, 22)

    /** Maximum basal rate in 0.025U steps */
    val maxBasal = getUnsignedShort(command.objectData, 26)

    /** Maximum bolus in 0.025U steps */
    val maxBolus = getUnsignedShort(command.objectData, 28)

    // Byte 29-45 unknown

    /** System date and time */
    val dateTime = DateTime(
        command.objectData[46].toUByte().toInt() + 2000, // year
        command.objectData[47].toUByte().toInt(), // month
        command.objectData[48].toUByte().toInt(), // day
        command.objectData[49].toUByte().toInt(), // hour
        command.objectData[50].toUByte().toInt(), // minute
        command.objectData[51].toUByte().toInt(), // second
    )

    /** System language */
    val language = Language.entries.find { it.raw == command.objectData[52] }

    /** Is temporary basal active? */
    val isTemporaryBasalActive = command.objectData[53].toBoolean()

    /** Reservoir level, last 3 numbers are decimals */
    val reservoir = getUnsignedInt(command.objectData, 54)

    /** Current alarms list */
    val alarms = buildList {
        for (i in 0..<9) {
            val raw = getUnsignedShort(command.objectData, 58 + 2 * i)
            if (raw != 0) add(Alarm.entries.find { it.raw == raw } ?: Alarm.Unknown)
        }
    }

    /** Current basal rate in 0.025U steps */
    val currentBasalRate = getUnsignedShort(command.objectData, 78)

    /** Current basal rate end time */
    val currentBasalEndHour = command.objectData[80].toUByte().toInt()
    /** Current basal rate end time */
    val currentBasalEndMinute = command.objectData[81].toUByte().toInt()

    /** TBR if present */
    val temporaryBasalRate = getUnsignedShort(command.objectData, 82)

    /** Is TBR absolute? */
    val temporaryBasalRateIsAbsolute = command.objectData[84].toBoolean()

    /** TBR duration, in 1 minute steps */
    val temporaryBasalRateDuration = getUnsignedShort(command.objectData, 86)

    /** TBR elapsed time, in 1 minute steps */
    val temporaryBasalRateElapsed = getUnsignedShort(command.objectData, 88)

    fun toUpdateSettingsV1(
        info: ApexDeviceInfo,
        alarmLength: AlarmLength,
        lockKeys: Boolean? = null,
        limitTDD: Boolean? = null,
        language: Language? = null,
        bolusSpeed: BolusDeliverySpeed? = null,
        alarmType: AlarmType? = null,
        screenBrightness: ScreenBrightness? = null,
        lowReservoirThreshold: Int? = null,
        lowReservoirDurationThreshold: Int? = null,
        enableAdvancedBolus: Boolean? = null,
        screenDisableDuration: Int? = null,
        maxTDD: Int? = null,
        maxBasalRate: Int? = null,
        maxSingleBolus: Int? = null,
    ): UpdateSettingsV1 {
        return UpdateSettingsV1(
            info,
            lockKeys ?: keyboardLockEnabled,
            limitTDD ?: totalDailyDoseLimitEnabled,
            language ?: this.language!!,
            bolusSpeed ?: deliverySpeed!!,
            alarmType ?: this.alarmType!!,
            alarmLength,
            screenBrightness ?: this.brightness!!,
            lowReservoirThreshold ?: this.lowReservoirThreshold,
            lowReservoirDurationThreshold ?: this.lowReservoirTimeLeftThreshold,
            enableAdvancedBolus ?: this.advancedBolusEnabled,
            screenDisableDuration ?: this.screenTimeout,
            maxTDD ?: this.maxTDD,
            maxBasalRate ?: this.maxBasal,
            maxSingleBolus ?: this.maxBolus,
        )
    }
}