package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.Date;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
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

@Singleton
public class MedtronicUIPostprocessor {

    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final MedtronicUtil medtronicUtil;
    private final MedtronicPumpStatus medtronicPumpStatus;
    private final MedtronicPumpPlugin medtronicPumpPlugin;

    @Inject
    public MedtronicUIPostprocessor(
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            MedtronicUtil medtronicUtil,
            MedtronicPumpStatus medtronicPumpStatus,
            MedtronicPumpPlugin medtronicPumpPlugin) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.medtronicUtil = medtronicUtil;
        this.medtronicPumpStatus = medtronicPumpStatus;
        this.medtronicPumpPlugin = medtronicPumpPlugin;
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    void postProcessData(MedtronicUITask uiTask) {

        switch (uiTask.commandType) {

            case SetBasalProfileSTD: {
                Boolean response = (Boolean) uiTask.returnData;

                if (response) {
                    BasalProfile basalProfile = (BasalProfile) uiTask.getParameter(0);

                    medtronicPumpStatus.basalsByHour = basalProfile.getProfilesByHour(medtronicPumpPlugin.getPumpDescription().pumpType);
                }
            }
            break;

            case GetBasalProfileSTD: {
                BasalProfile basalProfile = (BasalProfile) uiTask.returnData;

                try {
                    Double[] profilesByHour = basalProfile.getProfilesByHour(medtronicPumpPlugin.getPumpDescription().pumpType);

                    if (profilesByHour != null) {
                        medtronicPumpStatus.basalsByHour = profilesByHour;
                        medtronicPumpStatus.basalProfileStatus = BasalProfileStatus.ProfileOK;
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
                medtronicPumpStatus.lastBolusAmount = uiTask.getDoubleFromParameters(0);
                medtronicPumpStatus.lastBolusTime = new Date();
            }
            break;

            case GetRemainingInsulin: {
                medtronicPumpStatus.reservoirRemainingUnits = (Float) uiTask.returnData;
            }
            break;

            case CancelTBR: {
                medtronicPumpStatus.tempBasalStart = null;
                medtronicPumpStatus.tempBasalAmount = null;
                medtronicPumpStatus.tempBasalLength = null;
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

                medtronicPumpStatus.batteryRemaining = batteryStatusDTO.getCalculatedPercent(medtronicPumpStatus.batteryType);

                if (batteryStatusDTO.voltage != null) {
                    medtronicPumpStatus.batteryVoltage = batteryStatusDTO.voltage;
                }

                aapsLogger.debug(LTag.PUMP, "BatteryStatus: {}", batteryStatusDTO.toString());

            }
            break;

            case PumpModel: {
                if (medtronicPumpStatus.medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
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
                break;
                //aapsLogger.error(LTag.PUMP, "Post-processing not implemented for {}.", uiTask.commandType.name());

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

        PumpSettingDTO checkValue;

        medtronicPumpPlugin.getRileyLinkService().verifyConfiguration();

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

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), medtronicPumpStatus.maxBolus)) {
            aapsLogger.error("Wrong Max Bolus set on Pump (current={}, required={}).", checkValue.value, medtronicPumpStatus.maxBolus);
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, resourceHelper, rxBus, medtronicPumpStatus.maxBolus);
        }

        checkValue = settings.get("PCFG_MAX_BASAL");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), medtronicPumpStatus.maxBasal)) {
            aapsLogger.error("Wrong Max Basal set on Pump (current={}, required={}).", checkValue.value, medtronicPumpStatus.maxBasal);
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, resourceHelper, rxBus, medtronicPumpStatus.maxBasal);
        }

    }
}
