package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import static info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil.sendNotification;

import java.util.Date;
import java.util.Map;

import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 6/15/18.
 */

public class MedtronicUIPostprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicUIPostprocessor.class);

    MedtronicPumpStatus pumpStatus;


    public MedtronicUIPostprocessor() {
        pumpStatus = MedtronicUtil.getPumpStatus();
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(MedtronicUITask uiTask) {

        // if (!uiTask.haveData()) {
        // LOG.error("Error reading data [{}]: {}", uiTask.commandType, uiTask.errorDescription);
        // return;
        // }

        switch (uiTask.commandType) {

            case SetBasalProfileSTD: {
                Boolean response = (Boolean)uiTask.returnData;

                if (response) {
                    BasalProfile basalProfile = (BasalProfile)uiTask.getParameter(0);

                    pumpStatus.basalsByHour = basalProfile.getProfilesByHour();
                }
            }
                break;

            case GetBasalProfileSTD: {
                BasalProfile basalProfile = (BasalProfile)uiTask.returnData;

                Double[] profilesByHour = basalProfile.getProfilesByHour();

                if (profilesByHour != null) {
                    pumpStatus.basalsByHour = profilesByHour;
                    pumpStatus.basalProfileStatus = BasalProfileStatus.ProfileOK;
                } else {
                    uiTask.responseType = MedtronicUIResponseType.Error;
                    uiTask.errorDescription = "No profile found.";
                }
            }
                break;

            case SetBolus: {
                pumpStatus.lastBolusAmount = uiTask.getDoubleFromParameters(0);
                pumpStatus.lastBolusTime = new Date();
            }
                break;

            case GetRemainingInsulin: {
                pumpStatus.reservoirRemainingUnits = (Float)uiTask.returnData;
            }
                break;

            case CancelTBR: {
                pumpStatus.tempBasalStart = null;
                pumpStatus.tempBasalAmount = null;
                pumpStatus.tempBasalLength = null;
            }
                break;

            case RealTimeClock: {
                processTime(uiTask);
            }
                break;

            case GetBatteryStatus: {
                BatteryStatusDTO batteryStatusDTO = (BatteryStatusDTO)uiTask.returnData;

                if (batteryStatusDTO.batteryStatusType == BatteryStatusDTO.BatteryStatusType.Low)
                    pumpStatus.batteryRemaining = 18;
                else
                    pumpStatus.batteryRemaining = 70;
            }
                break;

            case PumpModel: {
                if (pumpStatus.medtronicDeviceType != MedtronicUtil.getMedtronicPumpModel()) {
                    LOG.warn("Configured pump is different then pump detected !");
                    sendNotification(MedtronicNotificationType.PumpTypeNotSame);
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
                LOG.trace("Post-processing not implemented for {}.", uiTask.commandType.name());

        }

    }


    private void processTime(MedtronicUITask uiTask) {

        ClockDTO clockDTO = (ClockDTO)uiTask.returnData;

        Duration dur = new Duration(clockDTO.localDeviceTime.toDateTime(DateTimeZone.UTC),
            clockDTO.pumpTime.toDateTime(DateTimeZone.UTC));

        clockDTO.timeDifference = (int)dur.getStandardSeconds();

        MedtronicUtil.setPumpTime(clockDTO);

        LOG.debug("Pump Time: " + clockDTO.localDeviceTime + ", DeviceTime=" + clockDTO.pumpTime + //
            ", diff: " + dur.getStandardSeconds() + " s");

        if (dur.getStandardMinutes() >= 10) {
            LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
                + dur.getStandardSeconds() + " s)");
            sendNotification(MedtronicNotificationType.PumpWrongTimeUrgent);
        } else if (dur.getStandardMinutes() >= 4) {
            LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
                + dur.getStandardSeconds() + " s)");
            sendNotification(MedtronicNotificationType.PumpWrongTimeNormal);
        }

    }


    private void postProcessSettings(MedtronicUITask uiTask) {

        Map<String, PumpSettingDTO> settings = (Map<String, PumpSettingDTO>)uiTask.returnData;

        MedtronicUtil.setSettings(settings);

        PumpSettingDTO checkValue = null;

        if (pumpStatus == null) {
            LOG.debug("Pump Status: was null");
            pumpStatus = MedtronicUtil.getPumpStatus();
            LOG.debug("Pump Status: " + this.pumpStatus);
        }

        this.pumpStatus.verifyConfiguration();

        // check profile
        if (!"Yes".equals(settings.get("PCFG_BASAL_PROFILES_ENABLED").value)) {
            LOG.error("Basal profiles are not enabled on pump.");
            sendNotification(MedtronicNotificationType.PumpBasalProfilesNotEnabled);

        } else {
            checkValue = settings.get("PCFG_ACTIVE_BASAL_PROFILE");

            if (!"STD".equals(checkValue.value)) {
                LOG.error("Basal profile set on pump is incorrect (must be STD).");
                sendNotification(MedtronicNotificationType.PumpIncorrectBasalProfileSelected);
            }
        }

        // TBR

        checkValue = settings.get("PCFG_TEMP_BASAL_TYPE");

        if (!"Units".equals(checkValue.value)) {
            LOG.error("Wrong TBR type set on pump (must be Absolute).");
            sendNotification(MedtronicNotificationType.PumpWrongTBRTypeSet);
        }

        // MAXes

        checkValue = settings.get("PCFG_MAX_BOLUS");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), pumpStatus.maxBolus)) {
            LOG.error("Wrong Max Bolus set on Pump (current={}, required={}).", checkValue.value, pumpStatus.maxBolus);
            sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, pumpStatus.maxBolus);
        }

        checkValue = settings.get("PCFG_MAX_BASAL");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), pumpStatus.maxBasal)) {
            LOG.error("Wrong Max Basal set on Pump (current={}, required={}).", checkValue.value, pumpStatus.maxBasal);
            sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, pumpStatus.maxBasal);
        }

    }

}
