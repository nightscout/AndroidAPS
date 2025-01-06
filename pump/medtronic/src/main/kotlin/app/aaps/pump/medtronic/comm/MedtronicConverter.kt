package app.aaps.pump.medtronic.comm

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.StringUtil
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.data.dto.BasalProfile
import app.aaps.pump.medtronic.data.dto.BatteryStatusDTO
import app.aaps.pump.medtronic.data.dto.PumpSettingDTO
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.PumpConfigurationGroup
import app.aaps.pump.medtronic.util.MedtronicUtil
import org.joda.time.IllegalFieldValueException
import org.joda.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 5/9/18.
 * High level decoder for data returned through MedtroniUIComm
 */
@Singleton
class MedtronicConverter @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val medtronicUtil: MedtronicUtil
) {

    fun decodeBasalProfile(pumpType: PumpType, rawContent: ByteArray): BasalProfile? {
        val basalProfile = BasalProfile(aapsLogger, rawContent)
        return if (basalProfile.verify(pumpType)) basalProfile else null
    }

    fun decodeModel(rawContent: ByteArray): MedtronicDeviceType {
        if (rawContent.size < 4) {
            aapsLogger.warn(LTag.PUMPCOMM, "Error reading PumpModel, returning Unknown_Device")
            return MedtronicDeviceType.Unknown_Device
        }
        val rawModel = StringUtil.fromBytes(ByteUtil.substring(rawContent, 1, 3))
        val pumpModel = MedtronicDeviceType.getByDescription(rawModel)
        aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "PumpModel: [raw=%s, resolved=%s]", rawModel, pumpModel.name))
        if (pumpModel != MedtronicDeviceType.Unknown_Device) {
            if (!medtronicUtil.isModelSet) {
                medtronicUtil.medtronicPumpModel = pumpModel
                medtronicUtil.isModelSet = true
            }
        }
        return pumpModel
    }

    fun decodeBatteryStatus(rawData: ByteArray): BatteryStatusDTO {
        // 00 7C 00 00
        val batteryStatus = BatteryStatusDTO()
        val status = rawData[0].toInt()
        if (status == 0) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Normal
        } else if (status == 1) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Low
        } else if (status == 2) {
            batteryStatus.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown
        }
        if (rawData.size > 1) {

            // if response in 3 bytes then we add additional information
            var d = if (rawData.size == 2) {
                rawData[1] * 1.0 / 100.0
            } else {
                ByteUtil.toInt(rawData[1], rawData[2]) * 1.0 / 100.0
            } //= null
            batteryStatus.voltage = d
            batteryStatus.extendedDataReceived = true
        }
        return batteryStatus
    }

    fun decodeRemainingInsulin(rawData: ByteArray): Double {
        var startIdx = 0
        val pumpModel = medtronicUtil.medtronicPumpModel
        val strokes = pumpModel.bolusStrokes //?: 10
        if (strokes == 40) {
            startIdx = 2
        }
        if (rawData.size == 2 && strokes == 40) {
            aapsLogger.error(LTag.PUMPCOMM, "It seems configuration is not correct, detected model $pumpModel should have length bigger than 2, but it doesn't (data: $rawData)")
            startIdx = 0
        }
        val reqLength = startIdx + 1
        val value: Double =
            if (reqLength >= rawData.size) {
                rawData[startIdx] / (1.0 * strokes)
            } else {
                ByteUtil.toInt(rawData[startIdx], rawData[startIdx + 1]) / (1.0 * strokes)
            }
        aapsLogger.debug(LTag.PUMPCOMM, "Remaining insulin: $value")
        return value
    }

    fun decodeTime(rawContent: ByteArray): LocalDateTime? {
        if (rawContent.size < 7) {
            aapsLogger.error(LTag.PUMPCOMM, "decodeTime: Byte array too short")
            return null
        }
        val hours = ByteUtil.asUINT8(rawContent[0])
        val minutes = ByteUtil.asUINT8(rawContent[1])
        val seconds = ByteUtil.asUINT8(rawContent[2])
        val year = (ByteUtil.asUINT8(rawContent[4]) and 0x3f) + 1984
        val month = ByteUtil.asUINT8(rawContent[5])
        val day = ByteUtil.asUINT8(rawContent[6])
        return try {
            LocalDateTime(year, month, day, hours, minutes, seconds)
        } catch (_: IllegalFieldValueException) {
            aapsLogger.error(
                LTag.PUMPCOMM, String.format(
                    Locale.ENGLISH, "decodeTime: Failed to parse pump time value: year=%d, month=%d, hours=%d, minutes=%d, seconds=%d",
                    year, month, day, hours, minutes, seconds
                )
            )
            null
        }
    }

    fun decodeSettingsLoop(rd: ByteArray): Map<String, PumpSettingDTO> {
        val map: MutableMap<String, PumpSettingDTO> = HashMap()
        addSettingToMap("PCFG_MAX_BOLUS", "" + decodeMaxBolus(rd), PumpConfigurationGroup.Bolus, map)
        addSettingToMap(
            "PCFG_MAX_BASAL", ""
                + decodeBasalInsulin(
                ByteUtil.makeUnsignedShort(
                    rd[settingIndexMaxBasal].toInt(),
                    rd[settingIndexMaxBasal + 1].toInt()
                )
            ), PumpConfigurationGroup.Basal, map
        )
        addSettingToMap(
            "CFG_BASE_CLOCK_MODE", if (rd[settingIndexTimeDisplayFormat].toInt() == 0) "12h" else "24h",
            PumpConfigurationGroup.General, map
        )
        addSettingToMap("PCFG_BASAL_PROFILES_ENABLED", parseResultEnable(rd[10].toInt()), PumpConfigurationGroup.Basal, map)
        if (rd[10].toInt() == 1) {
            val patt: String
            patt = when (rd[11].toInt()) {
                0    -> "STD"
                1    -> "A"
                2    -> "B"
                else -> "???"
            }
            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", patt, PumpConfigurationGroup.Basal, map)
        } else {
            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", "STD", PumpConfigurationGroup.Basal, map)
        }
        addSettingToMap("PCFG_TEMP_BASAL_TYPE", if (rd[14].toInt() != 0) "Percent" else "Units", PumpConfigurationGroup.Basal, map)
        return map
    }

    private fun decodeSettings512(rd: ByteArray): MutableMap<String, PumpSettingDTO> {
        val map: MutableMap<String, PumpSettingDTO> = HashMap()
        addSettingToMap("PCFG_AUTOOFF_TIMEOUT", "" + rd[0], PumpConfigurationGroup.General, map)
        if (rd[1].toInt() == 4) {
            addSettingToMap("PCFG_ALARM_MODE", "Silent", PumpConfigurationGroup.Sound, map)
        } else {
            addSettingToMap("PCFG_ALARM_MODE", "Normal", PumpConfigurationGroup.Sound, map)
            addSettingToMap("PCFG_ALARM_BEEP_VOLUME", "" + rd[1], PumpConfigurationGroup.Sound, map)
        }
        addSettingToMap("PCFG_AUDIO_BOLUS_ENABLED", parseResultEnable(rd[2].toInt()), PumpConfigurationGroup.Bolus, map)
        if (rd[2].toInt() == 1) {
            addSettingToMap(
                "PCFG_AUDIO_BOLUS_STEP_SIZE", "" + decodeBolusInsulin(ByteUtil.asUINT8(rd[3])),
                PumpConfigurationGroup.Bolus, map
            )
        }
        addSettingToMap("PCFG_VARIABLE_BOLUS_ENABLED", parseResultEnable(rd[4].toInt()), PumpConfigurationGroup.Bolus, map)
        addSettingToMap("PCFG_MAX_BOLUS", "" + decodeMaxBolus(rd), PumpConfigurationGroup.Bolus, map)
        addSettingToMap(
            "PCFG_MAX_BASAL", ""
                + decodeBasalInsulin(
                ByteUtil.makeUnsignedShort(
                    rd[settingIndexMaxBasal].toInt(),
                    rd[settingIndexMaxBasal + 1].toInt()
                )
            ), PumpConfigurationGroup.Basal, map
        )
        addSettingToMap(
            "CFG_BASE_CLOCK_MODE", if (rd[settingIndexTimeDisplayFormat].toInt() == 0) "12h" else "24h",
            PumpConfigurationGroup.General, map
        )
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)) {
            addSettingToMap(
                "PCFG_INSULIN_CONCENTRATION", "" + if (rd[9].toInt() == 0) 50 else 100, PumpConfigurationGroup.Insulin,
                map
            )
            //            LOG.debug("Insulin concentration: " + rd[9]);
        } else {
            addSettingToMap(
                "PCFG_INSULIN_CONCENTRATION", "" + if (rd[9].toInt() != 0) 50 else 100, PumpConfigurationGroup.Insulin,
                map
            )
            //            LOG.debug("Insulin concentration: " + rd[9]);
        }
        addSettingToMap("PCFG_BASAL_PROFILES_ENABLED", parseResultEnable(rd[10].toInt()), PumpConfigurationGroup.Basal, map)
        if (rd[10].toInt() == 1) {
            val patt: String
            patt = when (rd[11].toInt()) {
                0    -> "STD"
                1    -> "A"
                2    -> "B"
                else -> "???"
            }
            addSettingToMap("PCFG_ACTIVE_BASAL_PROFILE", patt, PumpConfigurationGroup.Basal, map)
        }
        addSettingToMap("CFG_MM_RF_ENABLED", parseResultEnable(rd[12].toInt()), PumpConfigurationGroup.General, map)
        addSettingToMap("CFG_MM_BLOCK_ENABLED", parseResultEnable(rd[13].toInt()), PumpConfigurationGroup.General, map)
        addSettingToMap("PCFG_TEMP_BASAL_TYPE", if (rd[14].toInt() != 0) "Percent" else "Units", PumpConfigurationGroup.Basal, map)
        if (rd[14].toInt() == 1) {
            addSettingToMap("PCFG_TEMP_BASAL_PERCENT", "" + rd[15], PumpConfigurationGroup.Basal, map)
        }
        addSettingToMap("CFG_PARADIGM_LINK_ENABLE", parseResultEnable(rd[16].toInt()), PumpConfigurationGroup.General, map)
        decodeInsulinActionSetting(rd, map)
        return map
    }

    private fun addSettingToMap(key: String, value: String, group: PumpConfigurationGroup, map: MutableMap<String, PumpSettingDTO>) {
        map[key] = PumpSettingDTO(key, value, group)
    }

    fun decodeSettings(rd: ByteArray): Map<String, PumpSettingDTO> {
        val map = decodeSettings512(rd)
        addSettingToMap(
            "PCFG_MM_RESERVOIR_WARNING_TYPE_TIME",
            if (rd[18].toInt() != 0) "PCFG_MM_RESERVOIR_WARNING_TYPE_TIME" else "PCFG_MM_RESERVOIR_WARNING_TYPE_UNITS",
            PumpConfigurationGroup.Other,
            map
        )
        addSettingToMap(
            "PCFG_MM_SRESERVOIR_WARNING_POINT", "" + ByteUtil.asUINT8(rd[19]),
            PumpConfigurationGroup.Other, map
        )
        addSettingToMap("CFG_MM_KEYPAD_LOCKED", parseResultEnable(rd[20].toInt()), PumpConfigurationGroup.Other, map)
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)) {
            addSettingToMap("PCFG_BOLUS_SCROLL_STEP_SIZE", "" + rd[21], PumpConfigurationGroup.Bolus, map)
            addSettingToMap("PCFG_CAPTURE_EVENT_ENABLE", parseResultEnable(rd[22].toInt()), PumpConfigurationGroup.Other, map)
            addSettingToMap("PCFG_OTHER_DEVICE_ENABLE", parseResultEnable(rd[23].toInt()), PumpConfigurationGroup.Other, map)
            addSettingToMap(
                "PCFG_OTHER_DEVICE_PAIRED_STATE", parseResultEnable(rd[24].toInt()), PumpConfigurationGroup.Other,
                map
            )
        }
        return map
    }

    private fun parseResultEnable(i: Int): String {
        return when (i) {
            0    -> "No"
            1    -> "Yes"
            else -> "???"
        }
    }

    private fun getStrokesPerUnit(isBasal: Boolean): Float {
        return if (isBasal) 40.0f else 10.0f // pumpModel.getBolusStrokes();
    }

    // 512
    private fun decodeInsulinActionSetting(ai: ByteArray, map: MutableMap<String, PumpSettingDTO>) {
        if (MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_512_712)) {
            addSettingToMap(
                "PCFG_INSULIN_ACTION_TYPE", if (ai[17].toInt() != 0) "Regular" else "Fast",
                PumpConfigurationGroup.Insulin, map
            )
        } else {
            val i = ai[17].toInt()
            val s: String
            s = if (i == 0 || i == 1) {
                if (ai[17].toInt() != 0) "Regular" else "Fast"
            } else {
                if (i == 15) "Unset" else "Curve: $i"
            }
            addSettingToMap("PCFG_INSULIN_ACTION_TYPE", s, PumpConfigurationGroup.Insulin, map)
        }
    }

    private fun decodeBasalInsulin(i: Int): Double {
        return i.toDouble() / getStrokesPerUnit(true).toDouble()
    }

    private fun decodeBolusInsulin(i: Int): Double {
        return i.toDouble() / getStrokesPerUnit(false).toDouble()
    }

    private val settingIndexMaxBasal: Int
        get() = if (is523orHigher()) 7 else 6

    private val settingIndexTimeDisplayFormat: Int
        get() = if (is523orHigher()) 9 else 8

    private fun decodeMaxBolus(ai: ByteArray): Double {
        return if (is523orHigher())
            decodeBolusInsulin(ByteUtil.toInt(ai[5], ai[6]))
        else
            decodeBolusInsulin(ByteUtil.asUINT8(ai[5]))
    }

    private fun is523orHigher(): Boolean {
        return MedtronicDeviceType.isSameDevice(medtronicUtil.medtronicPumpModel, MedtronicDeviceType.Medtronic_523andHigher)
    }

}