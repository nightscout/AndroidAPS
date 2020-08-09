package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodAcknowledgeAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AapsPodStateManager extends PodStateManager {
    private final AAPSLogger aapsLogger;
    private final SP sp;
    private final OmnipodPumpStatus omnipodPumpStatus;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;

    @Inject
    public AapsPodStateManager(AAPSLogger aapsLogger, SP sp, OmnipodPumpStatus omnipodPumpStatus,
                               RxBusWrapper rxBus, ResourceHelper resourceHelper) {
        super(aapsLogger);

        if (aapsLogger == null) {
            throw new IllegalArgumentException("aapsLogger can not be null");
        }
        if (sp == null) {
            throw new IllegalArgumentException("sp can not be null");
        }
        if (omnipodPumpStatus == null) {
            throw new IllegalArgumentException("omnipodPumpStatus can not be null");
        }
        if (rxBus == null) {
            throw new IllegalArgumentException("rxBus can not be null");
        }
        if (resourceHelper == null) {
            throw new IllegalArgumentException("resourceHelper can not be null");
        }

        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.omnipodPumpStatus = omnipodPumpStatus;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
    }

    @Override
    protected String readPodState() {
        return sp.getString(OmnipodConst.Prefs.PodState, "");
    }

    @Override
    protected void storePodState(String podState) {
        sp.putString(OmnipodConst.Prefs.PodState, podState);
    }

    @Override
    protected void notifyPodStateChanged() {
        if (!hasState()) {
            omnipodPumpStatus.ackAlertsText = null;
            omnipodPumpStatus.ackAlertsAvailable = false;
            omnipodPumpStatus.lastBolusTime = null;
            omnipodPumpStatus.lastBolusAmount = null;
            omnipodPumpStatus.reservoirRemainingUnits = 0.0;
            omnipodPumpStatus.pumpStatusType = PumpStatusType.Suspended;
            sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
            sendEvent(new EventOmnipodPumpValuesChanged());
            sendEvent(new EventRefreshOverview("Omnipod Pump", false));
        } else {
            // Update active alerts
            if (hasActiveAlerts()) {
                List<String> alerts = getTranslatedActiveAlerts();
                String alertsText = TextUtils.join("\n", alerts);

                if (!omnipodPumpStatus.ackAlertsAvailable || !alertsText.equals(omnipodPumpStatus.ackAlertsText)) {
                    omnipodPumpStatus.ackAlertsAvailable = true;
                    omnipodPumpStatus.ackAlertsText = TextUtils.join("\n", alerts);

                    sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
                }
            } else {
                if (omnipodPumpStatus.ackAlertsAvailable || StringUtils.isNotEmpty(omnipodPumpStatus.ackAlertsText)) {
                    omnipodPumpStatus.ackAlertsText = null;
                    omnipodPumpStatus.ackAlertsAvailable = false;
                    sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
                }
            }

            Date lastBolusStartTime = getLastBolusStartTime() == null ? null : getLastBolusStartTime().toDate();
            Double lastBolusAmount = getLastBolusAmount();

            // Update other info: last bolus, units remaining, suspended
            if (Objects.equals(lastBolusStartTime, omnipodPumpStatus.lastBolusTime) //
                    || !Objects.equals(lastBolusAmount, omnipodPumpStatus.lastBolusAmount) //
                    || !isReservoirStatusUpToDate(omnipodPumpStatus, getReservoirLevel())
                    || isSuspended() != PumpStatusType.Suspended.equals(omnipodPumpStatus.pumpStatusType)) {
                omnipodPumpStatus.lastBolusTime = lastBolusStartTime;
                omnipodPumpStatus.lastBolusAmount = lastBolusAmount;
                omnipodPumpStatus.reservoirRemainingUnits = getReservoirLevel() == null ? 75.0 : getReservoirLevel();
                omnipodPumpStatus.pumpStatusType = isSuspended() ? PumpStatusType.Suspended : PumpStatusType.Running;
                sendEvent(new EventOmnipodPumpValuesChanged());

                if (isSuspended() != PumpStatusType.Suspended.equals(omnipodPumpStatus.pumpStatusType)) {
                    sendEvent(new EventRefreshOverview("Omnipod Pump", false));
                }
            }
        }
        rxBus.send(new EventOmnipodDeviceStatusChange(this));
    }

    private List<String> getTranslatedActiveAlerts() {
        List<String> translatedAlerts = new ArrayList<>();
        AlertSet activeAlerts = getActiveAlerts();

        for (AlertSlot alertSlot : activeAlerts.getAlertSlots()) {
            translatedAlerts.add(translateAlertType(getConfiguredAlertType(alertSlot)));
        }
        return translatedAlerts;
    }


    private String translateAlertType(AlertType alertType) {
        if (alertType == null) {
            return getStringResource(R.string.omnipod_alert_unknown_alert);
        }
        switch (alertType) {
            case FINISH_PAIRING_REMINDER:
                return getStringResource(R.string.omnipod_alert_finish_pairing_reminder);
            case FINISH_SETUP_REMINDER:
                return getStringResource(R.string.omnipod_alert_finish_setup_reminder_reminder);
            case EXPIRATION_ALERT:
                return getStringResource(R.string.omnipod_alert_expiration);
            case EXPIRATION_ADVISORY_ALERT:
                return getStringResource(R.string.omnipod_alert_expiration_advisory);
            case SHUTDOWN_IMMINENT_ALARM:
                return getStringResource(R.string.omnipod_alert_shutdown_imminent);
            case LOW_RESERVOIR_ALERT:
                return getStringResource(R.string.omnipod_alert_low_reservoir);
            default:
                return alertType.name();
        }
    }

    private String getStringResource(int id, Object... args) {
        return resourceHelper.gs(id, args);
    }

    private static boolean isReservoirStatusUpToDate(OmnipodPumpStatus pumpStatus, Double unitsRemaining) {
        double expectedUnitsRemaining = unitsRemaining == null ? 75.0 : unitsRemaining;
        return Math.abs(expectedUnitsRemaining - pumpStatus.reservoirRemainingUnits) < 0.000001;
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }
}
