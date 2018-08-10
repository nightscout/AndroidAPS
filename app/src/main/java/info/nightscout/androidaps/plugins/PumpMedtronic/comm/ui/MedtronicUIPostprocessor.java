package info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui;

import java.util.Date;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.PumpSettingDTO;
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

        switch (uiTask.commandType) {

            case GetBasalProfileSTD: {
                BasalProfile basalProfile = (BasalProfile)uiTask.returnData;
                pumpStatus.basalsByHour = basalProfile.getProfilesByHour();
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
                    sendNotification(R.string.medtronic_error_pump_type_set_differs_from_detected, Notification.NORMAL);
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
                LOG.warn("Post-processing not implemented for {}.", uiTask.commandType.name());

        }

    }


    private void processTime(MedtronicUITask uiTask) {

        LocalDateTime ldt = (LocalDateTime)uiTask.returnData;

        Date d1 = ldt.toDate();

        long currentTimeMillis = System.currentTimeMillis();
        long diff = Math.abs(d1.getTime() - currentTimeMillis);

        LOG.warn("Pump Time: " + ldt + ", DeviceTime=" + d1 + //
            // ", epoch: " + d1.getTime() + ", current: " + currentTimeMillis + //
            ", diff: " + diff / 1000 + " s");

        if (diff >= 10 * 60 * 1000) {
            LOG.debug("Pump clock needs update, pump time: " + ldt + " (" + ldt + ")");
            sendNotification(R.string.combo_notification_check_time_date, Notification.URGENT);
        } else if (diff >= 4 * 60 * 1000) {
            LOG.debug("Pump clock needs update, pump time: " + ldt + " (" + ldt + ")");
            sendNotification(R.string.combo_notification_check_time_date, Notification.NORMAL);
        }

    }


    private void sendNotification(int resourceId, int notificationUrgencyType) {
        Notification notification = new Notification(Notification.MEDTRONIC_PUMP_ALARM, MainApp.gs(resourceId),
            notificationUrgencyType);
        MainApp.bus().post(new EventNewNotification(notification));
    }


    private void sendNotification(int resourceId, int notificationUrgencyType, Object... parameters) {
        Notification notification = new Notification(Notification.MEDTRONIC_PUMP_ALARM, MainApp.gs(resourceId,
            parameters), notificationUrgencyType);
        MainApp.bus().post(new EventNewNotification(notification));
    }


    private void postProcessSettings(MedtronicUITask uiTask) {
        Map<String, PumpSettingDTO> settings = (Map<String, PumpSettingDTO>)uiTask.returnData;

        MedtronicUtil.setSettings(settings);

        PumpSettingDTO checkValue = null;

        // check profile
        if (!"Yes".equals(settings.get("PCFG_BASAL_PROFILES_ENABLED").value)) {
            LOG.error("Basal profiles are not enabled on pump.");
            sendNotification(R.string.medtronic_error_pump_basal_profiles_not_enabled, Notification.URGENT);

        } else {
            checkValue = settings.get("PCFG_ACTIVE_BASAL_PROFILE");

            if (!"STD".equals(checkValue.value)) {
                LOG.error("Basal profile set on pump is incorrect (must be STD).");
                sendNotification(R.string.medtronic_error_pump_incorrect_basal_profile_selected, Notification.URGENT);
            }
        }

        // TBR

        checkValue = settings.get("PCFG_TEMP_BASAL_TYPE");

        if (!"Units".equals(checkValue.value)) {
            LOG.error("Wrong TBR type set on pump (must be Absolute).");
            sendNotification(R.string.medtronic_error_pump_wrong_tbr_type_set, Notification.URGENT);
        }

        // MAXes

        checkValue = settings.get("PCFG_MAX_BOLUS");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), pumpStatus.maxBolus)) {
            LOG.error("Wrong Max Bolus set on Pump (must be {}).", pumpStatus.maxBolus);
            sendNotification(R.string.medtronic_error_pump_wrong_max_bolus_set, Notification.NORMAL,
                pumpStatus.maxBolus);
        }

        checkValue = settings.get("PCFG_MAX_BASAL");

        if (!MedtronicUtil.isSame(Double.parseDouble(checkValue.value), pumpStatus.maxBasal)) {
            LOG.error("Wrong Max Basal set on Pump (must be {}).", pumpStatus.maxBasal);
            sendNotification(R.string.medtronic_error_pump_wrong_max_basal_set, Notification.NORMAL,
                pumpStatus.maxBasal);
        }

    }

}
