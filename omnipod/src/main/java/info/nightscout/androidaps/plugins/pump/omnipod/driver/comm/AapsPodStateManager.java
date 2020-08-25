package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AapsPodStateManager extends PodStateManager {
    private final AAPSLogger aapsLogger;
    private final SP sp;
    private final OmnipodPumpStatus omnipodPumpStatus;
    private final RxBusWrapper rxBus;

    @Inject
    public AapsPodStateManager(AAPSLogger aapsLogger, SP sp, OmnipodPumpStatus omnipodPumpStatus,
                               RxBusWrapper rxBus) {
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

        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.omnipodPumpStatus = omnipodPumpStatus;
        this.rxBus = rxBus;
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
        if (!hasPodState()) {
            omnipodPumpStatus.lastBolusTime = null;
            omnipodPumpStatus.lastBolusAmount = null;
            omnipodPumpStatus.reservoirRemainingUnits = 0.0;
            // TODO this does not seem to set the pump status to suspended anymore
            //  Also, verify that AAPS is aware that no insulin is delivered anymore at this point
            omnipodPumpStatus.pumpStatusType = PumpStatusType.Suspended;
            sendEvent(new EventRefreshOverview("Omnipod Pump", false));
        } else {
            Date lastBolusStartTime = getLastBolusStartTime() == null ? null : getLastBolusStartTime().toDate();
            Double lastBolusAmount = getLastBolusAmount();

            // Update other info: last bolus, units remaining, suspended
            boolean suspended = isSuspended() || !isPodRunning();
            if (Objects.equals(lastBolusStartTime, omnipodPumpStatus.lastBolusTime) //
                    || !Objects.equals(lastBolusAmount, omnipodPumpStatus.lastBolusAmount) //
                    || !isReservoirStatusUpToDate(omnipodPumpStatus, getReservoirLevel())
                    || suspended != PumpStatusType.Suspended.equals(omnipodPumpStatus.pumpStatusType)) {
                omnipodPumpStatus.lastBolusTime = lastBolusStartTime;
                omnipodPumpStatus.lastBolusAmount = lastBolusAmount;
                omnipodPumpStatus.reservoirRemainingUnits = getReservoirLevel() == null ? 75.0 : getReservoirLevel();

                boolean sendRefreshOverviewEvent = suspended != PumpStatusType.Suspended.equals(omnipodPumpStatus.pumpStatusType);
                // TODO this does not seem to set the pump status to suspended anymore
                //  Also, verify that AAPS is aware that no insulin is delivered anymore at this point
                omnipodPumpStatus.pumpStatusType = suspended ? PumpStatusType.Suspended : PumpStatusType.Running;

                if (sendRefreshOverviewEvent) {
                    sendEvent(new EventRefreshOverview("Omnipod Pump", false));
                }
            }
        }
        sendEvent(new EventOmnipodPumpValuesChanged());
    }

    private static boolean isReservoirStatusUpToDate(OmnipodPumpStatus pumpStatus, Double unitsRemaining) {
        double expectedUnitsRemaining = unitsRemaining == null ? 75.0 : unitsRemaining;
        return Math.abs(expectedUnitsRemaining - pumpStatus.reservoirRemainingUnits) < 0.000001;
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }
}
