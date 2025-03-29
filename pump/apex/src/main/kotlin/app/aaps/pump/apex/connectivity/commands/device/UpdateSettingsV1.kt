package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.connectivity.ProtocolVersion
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength
import app.aaps.pump.apex.connectivity.commands.pump.AlarmType
import app.aaps.pump.apex.connectivity.commands.pump.BolusDeliverySpeed
import app.aaps.pump.apex.connectivity.commands.pump.Language
import app.aaps.pump.apex.connectivity.commands.pump.ScreenBrightness
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.utils.asShortAsByteArray

/** Update system settings.
 *
 * * [lockKeys] - Lock pump keyboard after some time
 * * [limitTDD] - Limit total daily dose amount
 * * [language] - System language
 * * [bolusSpeed] - Bolus speed
 * * [alarmType] - Pump alarms type
 * * [alarmLength] - Pump alarms length
 * * [screenBrightness] - Screen brightness
 * * [lowReservoirThreshold] - Threshold in U for low reservoir warning
 * * [lowReservoirDurationThreshold] - Threshold in steps of 30 minutes for low reservoir warning (appears when reservoir will be empty in less than N minutes)
 * * [enableAdvancedBolus] - Enable Dual and Extended bolus
 * * [screenDisableDuration] - Screen disable duration in 0.1 second steps
 * * [maxTDD] - TDD alarm threshold
 * * [maxBasalRate] - Maximum basal rate and TBR value in 0.025U steps
 * * [maxSingleBolus] - Maximum single bolus in 0.025U steps
 */
class UpdateSettingsV1(
    info: ApexDeviceInfo,
    val lockKeys: Boolean,
    val limitTDD: Boolean,
    val language: Language,
    val bolusSpeed: BolusDeliverySpeed,
    val alarmType: AlarmType,
    val alarmLength: AlarmLength,
    val screenBrightness: ScreenBrightness,
    val lowReservoirThreshold: Int,
    val lowReservoirDurationThreshold: Int,
    val enableAdvancedBolus: Boolean,
    val screenDisableDuration: Int,
    val maxTDD: Int,
    val maxBasalRate: Int,
    val maxSingleBolus: Int,
    val enableGlucoseReminder: Boolean = false,
    val enableAutoSuspend: Boolean = false,
    val lockPump: Boolean = false,
) : BaseValueCommand(info) {
    override val valueId = 0x32
    override val isWrite = true

    override val maxProto = ProtocolVersion.PROTO_4_10

    override val additionalData: ByteArray
        get() {
            var functionFlags = 0
            var bolusFlags = 0

            if (bolusSpeed == BolusDeliverySpeed.Low) functionFlags = functionFlags or FunctionFlags.LowBolusSpeed.raw
            if (language == Language.English) functionFlags = functionFlags or FunctionFlags.EnglishLanguage.raw
            if (limitTDD) functionFlags = functionFlags or FunctionFlags.TDDLimit.raw
            if (lockKeys) functionFlags = functionFlags or FunctionFlags.KeyboardLock.raw
            if (enableAutoSuspend) functionFlags = functionFlags or FunctionFlags.AutoSuspend.raw
            if (lockPump) functionFlags = functionFlags or FunctionFlags.LockPump.raw

            if (enableAdvancedBolus) bolusFlags = bolusFlags or BolusFlags.AdvancedBolus.raw
            if (enableGlucoseReminder) bolusFlags = bolusFlags or BolusFlags.BGReminder.raw

            return byteArrayOf(
                functionFlags.toByte(),
                alarmType.raw,
                screenBrightness.raw,
                1, // unknown
                lowReservoirThreshold.toByte(),
                lowReservoirDurationThreshold.toByte(),
                bolusFlags.toByte(),
                alarmLength.raw
            ) + screenDisableDuration.asShortAsByteArray() +
                maxTDD.asShortAsByteArray() +
                maxBasalRate.asShortAsByteArray() +
                maxSingleBolus.asShortAsByteArray()
        }

    private enum class FunctionFlags(val raw: Int) {
        LowBolusSpeed(1 shl 0),
        KeyboardLock(1 shl 1),
        AutoSuspend(1 shl 2),
        LockPump(1 shl 4),
        TDDLimit(1 shl 5),
        EnglishLanguage( 1 shl 6),
    }

    private enum class BolusFlags(val raw: Int) {
        AdvancedBolus(1 shl 0),
        BGReminder(1 shl 1),
    }

    override fun toString(): String = "UpdateSettingsV1(maxTDD = $maxTDD, maxBolus = $maxSingleBolus, maxBasal = $maxBasalRate, bolusSpeed = ${bolusSpeed.name}, ...)"
}