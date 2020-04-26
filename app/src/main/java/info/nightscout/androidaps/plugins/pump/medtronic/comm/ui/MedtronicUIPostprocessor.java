package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.Date;
import java.util.Map;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;


/**
 * Created by andy on 6/15/18.
 */

class MedtronicUIPostprocessor {

    @NotNull private final AAPSLogger aapsLogger;
    @NotNull private final RxBusWrapper rxBus;
    @NotNull private final ResourceHelper resourceHelper;
    @NotNull private final MedtronicUtil medtronicUtil;

    public MedtronicUIPostprocessor(AAPSLogger aapsLogger, RxBusWrapper rxBus, ResourceHelper resourceHelper, MedtronicUtil medtronicUtil) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.medtronicUtil = medtronicUtil;
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(MedtronicUITask uiTask) {

        switch (uiTask.commandType) {

            case SetBasalProfileSTD: {
                Boolean response = (Boolean) uiTask.returnData;

                if (response) {
                    BasalProfile basalProfile = (BasalProfile) uiTask.getParameter(0);

                    medtronicUtil.getPumpStatus().basalsByHour = basalProfile.getProfilesByHour();
                }
            }
            break;

            case GetBasalProfileSTD: {
                BasalProfile basalProfile = (BasalProfile) uiTask.returnData;

                try {
                    Double[] profilesByHour = basalProfile.getProfilesByHour();

                    if (profilesByHour != null) {
                        medtronicUtil.getPumpStatus().basalsByHour = profilesByHour;
                        medtronicUtil.getPumpStatus().basalProfileStatus = BasalProfileStatus.ProfileOK;
                    } else {
                        uiTask.responseType = MedtronicUIResponseType.Error;
                        uiTask.errorDescription = "No profile found.";
                        aapsLogger.error("Basal Profile was NOT valid. [{}]", basalProfile.basalProfileToStringError());
                    }
                } catch (Exception ex) {
                    aapsLogger.error("Basal Profile was returned, but was invalid. [{}]", basalProfile.basalProfileToStringError());
                    uiTask.responseType = MedtronicUIResponseType.Error;
                    uiTask.errorDescription = "No profile found.";
                }
            }
            break;

            case SetBolus: {
                medtronicUtil.getPumpStatus().lastBolusAmount = uiTask.getDoubleFromParameters(0);
                medtronicUtil.getPumpStatus().lastBolusTime = new Date();
            }
            break;

            case GetRemainingInsulin: {
                medtronicUtil.getPumpStatus().reservoirRemainingUnits = (Float) uiTask.returnData;
            }
            break;

            case CancelTBR: {
                medtronicUtil.getPumpStatus().tempBasalStart = null;
                medtronicUtil.getPumpStatus().tempBasalAmount = null;
                medtronicUtil.getPumpStatus().tempBasalLength = null;
            }
            break;

            case GetRealTimeClock: {
                processTime(uiTask);
            }
            break;

            case SetRealTimeClock: {
                boolean response = (Boolean) uiTask.returnData;

                aapsLogger.debug(LTag.PUMP, "New time was {} set.", response ? "" : "NOT");

                if (response) {
                    medtronicUtil.getPumpTime().timeDifference = 0;
                }
            }
            break;


            case GetBatteryStatus: {
                BatteryStatusDTO batteryStatusDTO = (BatteryStatusDTO) uiTask.returnData;

                medtronicUtil.getPumpStatus().batteryRemaining = batteryStatusDTO.getCalculatedPercent(medtronicUtil.getPumpStatus().batteryType);

                if (batteryStatusDTO.voltage != null) {
                    medtronicUtil.getPumpStatus().batteryVoltage = batteryStatusDTO.voltage;
                }

                aapsLogger.debug(LTag.PUMP, "BatteryStatus: {}", batteryStatusDTO.toString());

            }
            break;

            case PumpModel: {
                if (medtronicUtil.getPumpStatus().medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
                    aapsLogger.warn(LTag.PUMP, "Configured pump is different then pump detected !");
                    medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, resourceHelper, rxBus);
                }
            }
            break;

            case Settings_512:
            case Settings: {
                postProcessSettings(uiTask);
            }
            break;

            // no postprocessing

            default:
                aapsLogger.error(LTag.PUMP, "Post-processing not implemented for {}.", uiTask.commandType.name());

        }

    }


    private void processTime(MedtronicUITask uiTask) {

        ClockDTO clockDTO = (ClockDTO) uiTask.returnData;

        Duration dur = new Duration(clockDTO.pumpTime.toDateTime(DateTimeZone.UTC),
                clockDTO.localDeviceTime.toDateTime(DateTimeZone.UTC));

        clockDTO.timeDifference = (int) dur.getStandardSeconds();

        medtronicUtil.setPumpTime(clockDTO);

        aapsLogger.debug(LTag.PUMP, "Pump Time: " + clockDTO.localDeviceTime + ", DeviceTime=" + clockDTO.pumpTime + //
                ", diff: " + dur.getStandardSeconds() + " s");

//        if (dur.getStandardMinutes() >= 10) {
//            if (isLogEnabled())
//                LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
//                        + dur.getStandardSeconds() + " s)");
//            sendNotification(MedtronicNotificationType.PumpWrongTimeUrgent);
//        } else if (dur.getStandardMinutes() >= 4) {
//            if (isLogEnabled())
//                LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
//                        + dur.getStandardSeconds() + " s)");
//            sendNotification(MedtronicNotificationType.PumpWrongTimeNormal);
//        }

    }


    private void postProcessSettings(MedtronicUITask uiTask) {

        Map<String, PumpSettingDTO> settings = (Map<String, PumpSettingDTO>) uiTask.returnData;

        medtronicUtil.setSettings(settings);

        PumpSettingDTO checkValue = null;

        if (medtronicUtil.getPumpStatus() == null) {
            aapsLogger.debug(LTag.PUMP, "Pump Status: was null");
         }

        medtronicUtil.getPumpStatus().verifyConfiguration();

        // check profile
        if (!"Yes".equals(settings.get("PCFG_BASAL_PROFILES_ENABLED").value)) {
            aapsLogger.error(LTag.PUMP, "Basal profiles are not enabled on pump.");
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpBasalProfilesNotEnabled, resourceHelper, rxBus);

        } else {
            checkValue = settings.get("PCFG_ACTIVE_BASAL_PROFILE");

            if (!"STD".equals(checkValue.value)) {
                aapsLogger.error("Basal profile set on pump is incorrect (must be STD).");
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpIncorrectBasalProfileSelected, resourceHelper, rxBus);
            }
        }

        // TBR

        checkValue = settings.get("PCFG_TEMP_BASAL_TYPE");

        if (!"Units".equals(checkValue.value)) {
            aapsLogger.error("Wrong TBR type set on pump (must be Absolute).");
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongTBRTypeSet, resourceHelper, rxBus);
        }

        // MAXes

        checkValue = settings.get("PCFG_MAX_BOLUS");

        if (!medtronicUtil.isSame(Double.parseDouble(checkValue.value), medtronicUtil.getPumpStatus().maxBolus)) {
            aapsLogger.error("Wrong Max Bolus set on Pump (current={}, required={}).", checkValue.value, medtronicUtil.getPumpStatus().maxBolus);
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, resourceHelper, rxBus, medtronicUtil.getPumpStatus().maxBolus);
        }

        checkValue = settings.get("PCFG_MAX_BASAL");

        if (!medtronicUtil.isSame(Double.parseDouble(checkValue.value), medtronicUtil.getPumpStatus().maxBasal)) {
            aapsLogger.error("Wrong Max Basal set on Pump (current={}, required={}).", checkValue.value, medtronicUtil.getPumpStatus().maxBasal);
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, resourceHelper, rxBus, medtronicUtil.getPumpStatus().maxBasal);
        }

    }
}
