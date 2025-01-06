package app.aaps.pump.medtronic.comm.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.data.dto.BasalProfile
import app.aaps.pump.medtronic.data.dto.BatteryStatusDTO
import app.aaps.pump.medtronic.data.dto.ClockDTO
import app.aaps.pump.medtronic.data.dto.PumpSettingDTO
import app.aaps.pump.medtronic.defs.BasalProfileStatus
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.defs.MedtronicNotificationType
import app.aaps.pump.medtronic.defs.MedtronicUIResponseType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 6/15/18.
 */
@Singleton
class MedtronicUIPostprocessor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val medtronicPumpPlugin: MedtronicPumpPlugin
) {

    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    fun postProcessData(uiTask: MedtronicUITask) {
        when (uiTask.commandType) {
            MedtronicCommandType.SetBasalProfileSTD  -> {
                val response = uiTask.result as Boolean?
                if (response != null && response) {
                    val basalProfile = uiTask.getParameter(0) as BasalProfile
                    aapsLogger.debug("D: basal profile returned after set: $basalProfile")

                    medtronicPumpStatus.basalsByHour = basalProfile.getProfilesByHour(medtronicPumpPlugin.pumpDescription.pumpType)
                }
            }

            MedtronicCommandType.GetBasalProfileSTD  -> {
                val basalProfile = uiTask.result as BasalProfile?

                //aapsLogger.debug("D: basal profile on read: " + basalProfile);
                try {
                    if (basalProfile != null) {
                        val profilesByHour = basalProfile.getProfilesByHour(medtronicPumpPlugin.pumpDescription.pumpType)
                        if (!BasalProfile.isBasalProfileByHourUndefined(profilesByHour)) {
                            medtronicPumpStatus.basalsByHour = profilesByHour
                            medtronicPumpStatus.basalProfileStatus = BasalProfileStatus.ProfileOK
                            //aapsLogger.debug("D: basal profile on read: basalsByHour: " +  BasalProfile.getProfilesByHourToString(medtronicPumpStatus.basalsByHour));
                        } else {
                            uiTask.responseType = MedtronicUIResponseType.Error
                            uiTask.errorDescription = "No profile found."
                            aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Basal Profile was NOT valid. [%s]", basalProfile.basalProfileToStringError()))
                        }
                    }
                } catch (ex: Exception) {
                    aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Basal Profile was returned, but was invalid. [%s]", basalProfile?.basalProfileToStringError()))
                    uiTask.responseType = MedtronicUIResponseType.Error
                    uiTask.errorDescription = "No profile found."
                }
            }

            MedtronicCommandType.SetBolus            -> {
                medtronicPumpStatus.lastBolusAmount = uiTask.getDoubleFromParameters(0)
                medtronicPumpStatus.lastBolusTime = Date()
            }

            MedtronicCommandType.GetRemainingInsulin -> {
                medtronicPumpStatus.reservoirRemainingUnits = uiTask.result as Double
            }

            MedtronicCommandType.CancelTBR           -> {
                medtronicPumpStatus.tempBasalStart = null
                medtronicPumpStatus.tempBasalAmount = null
                medtronicPumpStatus.tempBasalLength = null
            }

            MedtronicCommandType.GetRealTimeClock    -> {
                processTime(uiTask)
            }

            MedtronicCommandType.SetRealTimeClock    -> {
                val response = uiTask.result as Boolean
                aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "New time was %s set.", if (response) "" else "NOT"))
                if (response) {
                    medtronicUtil.pumpTime?.timeDifference = 0
                }
            }

            MedtronicCommandType.GetBatteryStatus    -> {
                val batteryStatusDTO = uiTask.result as BatteryStatusDTO?
                if (batteryStatusDTO != null) {
                    medtronicPumpStatus.batteryRemaining = batteryStatusDTO.getCalculatedPercent(medtronicPumpStatus.batteryType)
                    if (batteryStatusDTO.voltage != null) {
                        medtronicPumpStatus.batteryVoltage = batteryStatusDTO.voltage
                    }
                    aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "BatteryStatus: %s", batteryStatusDTO.toString()))
                } else {
                    medtronicPumpStatus.batteryVoltage = null
                }
            }

            MedtronicCommandType.PumpModel           -> {
                if (medtronicPumpStatus.medtronicDeviceType !== medtronicUtil.medtronicPumpModel) {
                    aapsLogger.warn(LTag.PUMP, "Configured pump is different then pump detected !")
                    medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, rh)
                }
            }

            MedtronicCommandType.Settings_512,
            MedtronicCommandType.Settings            -> {
                postProcessSettings(uiTask)
            }

            else                                     -> {
            }
        }
    }

    private fun processTime(uiTask: MedtronicUITask) {
        val clockDTO = uiTask.result as ClockDTO?
        if (clockDTO != null) {
            val dur = Duration(
                clockDTO.pumpTime.toDateTime(DateTimeZone.UTC),
                clockDTO.localDeviceTime.toDateTime(DateTimeZone.UTC)
            )
            clockDTO.timeDifference = dur.standardSeconds.toInt()
            medtronicUtil.pumpTime = clockDTO
            aapsLogger.debug(
                LTag.PUMP, "Pump Time: " + clockDTO.localDeviceTime + ", DeviceTime=" + clockDTO.pumpTime +  //
                    ", diff: " + dur.standardSeconds + " s"
            )
        } else {
            aapsLogger.debug(LTag.PUMP, "Problem with returned data: " + medtronicUtil.gsonInstance.toJson(uiTask.result))
        }
    }

    private fun postProcessSettings(uiTask: MedtronicUITask) {
        @Suppress("UNCHECKED_CAST") val settings = uiTask.result as? Map<String, PumpSettingDTO> ?: return

        medtronicUtil.settings = settings
        var checkValue: PumpSettingDTO?
        medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()

        // check profile
        if (settings.containsKey("PCFG_BASAL_PROFILES_ENABLED") && settings.containsKey("PCFG_ACTIVE_BASAL_PROFILE")) {
            checkValue = settings["PCFG_BASAL_PROFILES_ENABLED"]
            if ("Yes" != checkValue?.value) {
                aapsLogger.error(LTag.PUMP, "Basal profiles are not enabled on pump.")
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpBasalProfilesNotEnabled, rh)
            } else {
                checkValue = settings["PCFG_ACTIVE_BASAL_PROFILE"]
                if ("STD" != checkValue?.value) {
                    aapsLogger.error("Basal profile set on pump is incorrect (must be STD).")
                    medtronicUtil.sendNotification(MedtronicNotificationType.PumpIncorrectBasalProfileSelected, rh)
                }
            }
        }

        // TBR
        if (settings.containsKey("PCFG_TEMP_BASAL_TYPE")) {
            if ("Units" != settings["PCFG_TEMP_BASAL_TYPE"]?.value) {
                aapsLogger.error("Wrong TBR type set on pump (must be Absolute).")
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongTBRTypeSet, rh)
            }
        }

        // MAXes
        if (settings.containsKey("PCFG_MAX_BOLUS")) {
            checkValue = settings["PCFG_MAX_BOLUS"]
            if (!MedtronicUtil.isSame(checkValue?.value?.toDouble(), medtronicPumpStatus.maxBolus)) {
                aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Wrong Max Bolus set on Pump (current=%s, required=%.2f).", checkValue?.value, medtronicPumpStatus.maxBolus))
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, rh, medtronicPumpStatus.maxBolus)
            }
        }

        if (settings.containsKey("PCFG_MAX_BASAL")) {
            checkValue = settings["PCFG_MAX_BASAL"]
            if (!MedtronicUtil.isSame(checkValue?.value?.toDouble(), medtronicPumpStatus.maxBasal)) {
                aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Wrong Max Basal set on Pump (current=%s, required=%.2f).", checkValue?.value, medtronicPumpStatus.maxBasal))
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, rh, medtronicPumpStatus.maxBasal)
            }
        }
    }

}