package info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui;

import static info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil.sendNotification;

import java.util.Date;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

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

        LocalDateTime ldt = (LocalDateTime)uiTask.returnData;

        Date d1 = ldt.toDate();

        long currentTimeMillis = System.currentTimeMillis();
        long diff = Math.abs(d1.getTime() - currentTimeMillis);

        LOG.debug("Pump Time: " + ldt + ", DeviceTime=" + d1 + //
            ", diff: " + diff / 1000 + " s");

        if (diff >= 10 * 60 * 1000) {
            LOG.warn("Pump clock needs update, pump time: " + ldt + " (" + ldt + ")");
            sendNotification(MedtronicNotificationType.PumpWrongTimeUrgent);
        } else if (diff >= 4 * 60 * 1000) {
            LOG.warn("Pump clock needs update, pump time: " + ldt + " (" + ldt + ")");
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
            LOG.error("Wrong Max Bolus set on Pump (must be {}).", pumpStatus.maxBolus);
            sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, pumpStatus.maxBolus);
        }

        checkValue = settings.get("PCFG_MAX_BASAL");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), pumpStatus.maxBasal)) {
            LOG.error("Wrong Max Basal set on Pump (must be {}).", pumpStatus.maxBasal);
            sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, pumpStatus.maxBasal);
        }

    }

}
