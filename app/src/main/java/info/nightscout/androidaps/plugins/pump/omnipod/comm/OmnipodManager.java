package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
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
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.SP;

public class OmnipodManager {
    private static final int ACTION_VERIFICATION_TRIES = 3;

    protected final OmnipodCommunicationService communicationService;
    protected PodSessionState podState;

    public OmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        this.communicationService = communicationService;
        this.podState = podState;
    }

    // After priming should have been finished, the pod state is verified.
    // The result of that verification is passed to the SetupActionResultHandler
    public void pairAndPrime(SetupActionResultHandler resultHandler) {
        if (podState == null) {
            podState = communicationService.executeAction(new PairAction(new PairService()));
        }
        if (podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            communicationService.executeAction(new PrimeAction(new PrimeService(), podState));

            executeDelayed(() -> verifySetupAction(statusResponse -> PrimeAction.updatePrimingStatus(podState, statusResponse), //
                    SetupProgress.PRIMING_FINISHED, resultHandler), //
                    calculateBolusDuration(OmnipodConst.POD_PRIME_BOLUS_UNITS, OmnipodConst.POD_PRIMING_DELIVERY_RATE));
        } else {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, podState.getSetupProgress());
        }
    }

    // After inserting the cannula should have been finished, the pod state is verified.
    // The result of that verification is passed to the SetupActionResultHandler
    public void insertCannula(BasalSchedule basalSchedule, SetupActionResultHandler resultHandler) {
        if (podState == null || podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, podState == null ? null : podState.getSetupProgress());
        } else if (podState.getSetupProgress().isAfter(SetupProgress.CANNULA_INSERTING)) {
            throw new IllegalSetupProgressException(SetupProgress.CANNULA_INSERTING, podState.getSetupProgress());
        }

        communicationService.executeAction(new InsertCannulaAction(new InsertCannulaService(), podState, basalSchedule));

        executeDelayed(() -> verifySetupAction(statusResponse -> InsertCannulaAction.updateCannulaInsertionStatus(podState, statusResponse), //
                SetupProgress.COMPLETED, resultHandler),
                calculateBolusDuration(OmnipodConst.POD_CANNULA_INSERTION_BOLUS_UNITS, OmnipodConst.POD_CANNULA_INSERTION_DELIVERY_RATE));
    }

    public StatusResponse getPodStatus() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, null);
        }

        return communicationService.executeAction(new GetStatusAction(podState));
    }

    public PodInfoResponse getPodInfo(PodInfoType podInfoType) {
        assertReadyForDelivery();

        return communicationService.executeAction(new GetPodInfoAction(podState, podInfoType));
    }

    public void acknowledgeAlerts() {
        assertReadyForDelivery();

        communicationService.executeAction(new AcknowledgeAlertsAction(podState, podState.getActiveAlerts()));
    }

    public void setBasalSchedule(BasalSchedule schedule) {
        assertReadyForDelivery();

        communicationService.executeAction(new SetBasalScheduleAction(podState, schedule,
                false, podState.getScheduleOffset(), true));
    }

    public void setTemporaryBasal(TempBasalPair tempBasalPair) {
        assertReadyForDelivery();

        communicationService.executeAction(new SetTempBasalAction(new SetTempBasalService(),
                podState, tempBasalPair.getInsulinRate(), Duration.standardMinutes(tempBasalPair.getDurationMinutes()),
                true, true));
    }

    public void cancelTemporaryBasal() {
        assertReadyForDelivery();

        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.TEMP_BASAL, true));
    }

    public void bolus(Double units, StatusResponseHandler bolusCompletionHandler) {
        assertReadyForDelivery();

        communicationService.executeAction(new BolusAction(podState, units, true, true));

        if (bolusCompletionHandler != null) {
            executeDelayed(() -> {
                for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
                    StatusResponse statusResponse = null;
                    try {
                        statusResponse = getPodStatus();
                        if (statusResponse.getDeliveryStatus().isBolusing()) {
                            break;
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                    bolusCompletionHandler.handle(statusResponse);
                }
            }, calculateBolusDuration(units, OmnipodConst.POD_BOLUS_DELIVERY_RATE));
        }
    }

    public void cancelBolus() {
        assertReadyForDelivery();
        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.BOLUS, true));
    }

    public void suspendDelivery() {
        assertReadyForDelivery();

        communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), true));
    }

    public void resumeDelivery() {
        assertReadyForDelivery();

        communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                true, podState.getScheduleOffset(), true));
    }

    // If this command fails, it it possible that delivery has been suspended
    public void setTime() {
        assertReadyForDelivery();

        // Suspend delivery
        communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), false));

        // Joda seems to cache the default time zone, so we use the JVM's
        DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
        podState.setTimeZone(DateTimeZone.getDefault());

        // Resume delivery
        StatusResponse statusResponse = communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                true, podState.getScheduleOffset(), true));
    }

    public void deactivatePod() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, null);
        }

        communicationService.executeAction(new DeactivatePodAction(podState, true));
        resetPodState();
    }

    public void resetPodState() {
        podState = null;
        SP.remove(OmnipodConst.Prefs.PodState);
    }

    public OmnipodCommunicationService getCommunicationService() {
        return communicationService;
    }

    public DateTime getTime() {
        return podState.getTime();
    }

    public boolean isReadyForDelivery() {
        return podState != null && podState.getSetupProgress() == SetupProgress.COMPLETED;
    }

    public PodSessionState getPodState() {
        return this.podState;
    }

    public String getPodStateAsString() {
        return podState == null ? "null" : podState.toString();
    }

    private Duration calculateBolusDuration(double units, double deliveryRate) {
        return Duration.standardSeconds((long) Math.ceil(units / deliveryRate));
    }

    private void executeDelayed(Runnable r, Duration timeout) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.schedule(r, timeout.getMillis(), TimeUnit.MILLISECONDS);
    }

    private void assertReadyForDelivery() {
        if (!isReadyForDelivery()) {
            throw new IllegalSetupProgressException(SetupProgress.COMPLETED, podState == null ? null : podState.getSetupProgress());
        }
    }

    private void verifySetupAction(StatusResponseHandler statusResponseHandler, SetupProgress expectedSetupProgress, SetupActionResultHandler resultHandler) {
        SetupActionResult result = null;
        for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
            try {
                StatusResponse delayedStatusResponse = communicationService.executeAction(new GetStatusAction(podState));
                statusResponseHandler.handle(delayedStatusResponse);

                if (podState.getSetupProgress().equals(expectedSetupProgress)) {
                    result = new SetupActionResult(SetupActionResult.ResultType.SUCCESS);
                    break;
                } else {
                    result = new SetupActionResult(SetupActionResult.ResultType.FAILURE) //
                            .setupProgress(podState.getSetupProgress());
                    break;
                }
            } catch (Exception ex) {
                result = new SetupActionResult(SetupActionResult.ResultType.VERIFICATION_FAILURE) //
                        .exception(ex);
            }
        }
        if (resultHandler != null) {
            resultHandler.handle(result);
        }
    }
}
