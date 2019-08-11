package info.nightscout.androidaps.plugins.pump.omnipod;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.AcknowledgeAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.CancelDeliveryAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.DeactivatePodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.GetPodInfoAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.GetStatusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.InsertCannulaAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.PairAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.PrimeAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetTempBasalAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.InsertCannulaService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PairService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.SetTempBasalService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfo;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalScheduleMapper;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.SP;

public class OmnipodManager {
    private final OmnipodCommunicationService communicationService;
    private PodSessionState podState;

    public OmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        this.communicationService = communicationService;
        this.podState = podState;
    }

    public OmnipodManager(OmnipodCommunicationService communicationService) {
        this(communicationService, null);
    }

    public OmnipodCommunicationService getCommunicationService() {
        return communicationService;
    }

    public <T extends PodInfo> T getPodInfo(PodInfoType podInfoType) {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        PodInfoResponse podInfoResponse = communicationService.executeAction(new GetPodInfoAction(podState, podInfoType));
        return podInfoResponse.getPodInfo();
    }

    public StatusResponse getStatus() {
        if (podState == null) {
            throw new IllegalStateException("Pod should be paired first");
        }
        return communicationService.executeAction(new GetStatusAction(podState));
    }

    public void acknowledgeAlerts() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new AcknowledgeAlertsAction(podState, podState.getActiveAlerts()));
    }

    public void pairAndPrime() {
        if (podState == null) {
            podState = communicationService.executeAction(new PairAction(new PairService()));
        }
        if (podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            communicationService.executeAction(new PrimeAction(new PrimeService(), podState));

            executeDelayed(() -> {
                StatusResponse delayedStatusResponse = communicationService.executeAction(new GetStatusAction(podState));
                PrimeAction.updatePrimingStatus(podState, delayedStatusResponse);
            }, OmnipodConst.POD_PRIME_DURATION);
        } else {
            throw new IllegalStateException("Illegal setup state: " + podState.getSetupProgress().name());
        }
    }

    public void insertCannula(Profile profile) {
        if (podState == null || podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            throw new IllegalArgumentException("Pod should be paired and primed first");
        } else if (podState.getSetupProgress().isAfter(SetupProgress.CANNULA_INSERTING)) {
            throw new IllegalStateException("Illegal setup state: " + podState.getSetupProgress().name());
        }

        communicationService.executeAction(new InsertCannulaAction(new InsertCannulaService(), podState,
                BasalScheduleMapper.mapProfileToBasalSchedule(profile)));

        executeDelayed(() -> {
            StatusResponse delayedStatusResponse = communicationService.executeAction(new GetStatusAction(podState));
            InsertCannulaAction.updateCannulaInsertionStatus(podState, delayedStatusResponse);
        }, OmnipodConst.POD_CANNULA_INSERTION_DURATION);
    }

    public void setBasalSchedule(BasalSchedule basalSchedule, boolean confidenceReminder) {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new SetBasalScheduleAction(podState, basalSchedule,
                confidenceReminder, podState.getScheduleOffset(), true));
    }

    public void setTempBasal(double rate, Duration duration) {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new SetTempBasalAction(new SetTempBasalService(),
                podState, rate, duration, true, true));
    }

    public void cancelTempBasal() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.TEMP_BASAL, true));
    }

    public void bolus(double units) {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new BolusAction(podState, units, true, true));
    }

    public void cancelBolus() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.BOLUS, true));
    }

    public void suspendDelivery() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), true));
    }

    public void resumeDelivery() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                true, podState.getScheduleOffset(), true));
    }

    public void setTime() {
        if (!isInitialized()) {
            throw new IllegalStateException("Pod should be initialized first");
        }
        // Suspend delivery
        communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), false));

        // Joda seems to cache the default time zone, so we use the JVM's
        DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
        podState.setTimeZone(DateTimeZone.getDefault());

        // Resume delivery
        communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                true, podState.getScheduleOffset(), true));
    }

    public DateTime getTime() {
        return podState.getTime();
    }

    public void deactivatePod() {
        if (podState == null) {
            throw new IllegalStateException("Pod should be paired first");
        }
        communicationService.executeAction(new DeactivatePodAction(podState, true));
        resetPodState();
    }

    public boolean isInitialized() {
        return podState != null && podState.getSetupProgress() == SetupProgress.COMPLETED;
    }

    public String getPodStateAsString() {
        return podState == null ? "null" : podState.toString();
    }

    public void resetPodState() {
        podState = null;
        SP.remove(OmnipodConst.Prefs.PodState);
    }

    private void executeDelayed(Runnable r, Duration timeout) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.schedule(r, timeout.getMillis(), TimeUnit.MILLISECONDS);
    }
}