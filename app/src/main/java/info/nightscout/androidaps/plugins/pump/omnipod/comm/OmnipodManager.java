package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.logging.L;
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
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateChangedHandler;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.Single;

public class OmnipodManager {
    private static final int ACTION_VERIFICATION_TRIES = 3;

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    protected final OmnipodCommunicationService communicationService;
    private final PodStateChangedHandler podStateChangedHandler;
    protected PodSessionState podState;

    public OmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState,
                          PodStateChangedHandler podStateChangedHandler) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        this.communicationService = communicationService;
        if(podState != null) {
            podState.setStateChangedHandler(podStateChangedHandler);
        }
        this.podState = podState;
        this.podStateChangedHandler = podStateChangedHandler;
    }

    public OmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        this(communicationService, podState, null);
    }

    // After priming should have been finished, the pod state is verified.
    // The result of that verification is passed to the SetupActionResultHandler
    public synchronized void pairAndPrime(SetupActionResultHandler resultHandler) {
        if (podState == null) {
            podState = communicationService.executeAction(
                    new PairAction(new PairService(), podStateChangedHandler));
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
    public synchronized void insertCannula(BasalSchedule basalSchedule, SetupActionResultHandler resultHandler) {
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

    public synchronized StatusResponse getPodStatus() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, null);
        }

        return communicationService.executeAction(new GetStatusAction(podState));
    }

    public synchronized PodInfoResponse getPodInfo(PodInfoType podInfoType) {
        assertReadyForDelivery();

        return communicationService.executeAction(new GetPodInfoAction(podState, podInfoType));
    }

    public synchronized void acknowledgeAlerts() {
        assertReadyForDelivery();

        communicationService.executeAction(new AcknowledgeAlertsAction(podState, podState.getActiveAlerts()));
    }

    public synchronized void setBasalSchedule(BasalSchedule schedule) {
        assertReadyForDelivery();

        communicationService.executeAction(new SetBasalScheduleAction(podState, schedule,
                false, podState.getScheduleOffset(), true));
    }

    public synchronized void setTemporaryBasal(TempBasalPair tempBasalPair) {
        assertReadyForDelivery();

        communicationService.executeAction(new SetTempBasalAction(new SetTempBasalService(),
                podState, tempBasalPair.getInsulinRate(), Duration.standardMinutes(tempBasalPair.getDurationMinutes()),
                true, true));
    }

    public synchronized void cancelTemporaryBasal() {
        assertReadyForDelivery();

        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.TEMP_BASAL, true));
    }

    public Single<StatusResponse> bolus(Double units) {
        assertReadyForDelivery();

        try {
            communicationService.executeAction(new BolusAction(podState, units, true, true));
        } catch (Exception ex) {
            if (isCertainFailure(ex)) {
                throw ex;
            } else {
                CommandVerificationResult verificationResult = verifyCommand();
                switch (verificationResult) {
                    case CERTAIN_FAILURE:
                        if (ex instanceof OmnipodException) {
                            ((OmnipodException) ex).setCertainFailure(true);
                            throw ex;
                        } else {
                            OmnipodException newException = new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex);
                            newException.setCertainFailure(true);
                            throw newException;
                        }
                    case UNCERTAIN_FAILURE:
                        throw ex;
                    case SUCCESS:
                        // Ignore original exception
                        break;
                }
            }
        }

        return Single.create(emitter -> executeDelayed(() -> {
            try {
                StatusResponse statusResponse = getPodStatus();
                if (statusResponse.getDeliveryStatus().isBolusing()) {
                    emitter.onError(new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus()));
                } else {
                    emitter.onSuccess(statusResponse);
                }
            } catch (Exception ex) {
                emitter.onError(ex);
            }
        }, calculateBolusDuration(units, OmnipodConst.POD_BOLUS_DELIVERY_RATE)));
    }

    public synchronized void cancelBolus() {
        assertReadyForDelivery();
        communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.BOLUS, true));
    }

    public synchronized void suspendDelivery() {
        assertReadyForDelivery();

        communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), true));
    }

    public synchronized void resumeDelivery() {
        assertReadyForDelivery();

        communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                true, podState.getScheduleOffset(), true));
    }

    // If this command fails, it it possible that delivery has been suspended
    public synchronized void setTime() {
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

    public synchronized void deactivatePod() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, null);
        }

        communicationService.executeAction(new DeactivatePodAction(podState, true));
        resetPodState();
    }

    public synchronized void resetPodState() {
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

    private void executeDelayed(Runnable r, Duration timeout) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.schedule(r, timeout.getMillis(), TimeUnit.MILLISECONDS);
    }

    private void assertReadyForDelivery() {
        if (!isReadyForDelivery()) {
            throw new IllegalSetupProgressException(SetupProgress.COMPLETED, podState == null ? null : podState.getSetupProgress());
        }
    }

    private void verifySetupAction(StatusResponseHandler setupActionResponseHandler, SetupProgress expectedSetupProgress, SetupActionResultHandler resultHandler) {
        SetupActionResult result = null;
        for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
            try {
                StatusResponse delayedStatusResponse = communicationService.executeAction(new GetStatusAction(podState));
                setupActionResponseHandler.handle(delayedStatusResponse);

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

    // Only works for commands which contain nonce resyncable message blocks
    private CommandVerificationResult verifyCommand() {
        if (isLoggingEnabled()) {
            LOG.warn("Verifying command by using cancel none command to verify nonce");
        }
        try {
            communicationService.sendCommand(StatusResponse.class, podState,
                    new CancelDeliveryCommand(podState.getCurrentNonce(), BeepType.NO_BEEP, DeliveryType.NONE), false);
        } catch (NonceOutOfSyncException ex) {
            if (isLoggingEnabled()) {
                LOG.info("Command resolved to FAILURE (CERTAIN_FAILURE)");
            }
            return CommandVerificationResult.CERTAIN_FAILURE;
        } catch (Exception ex) {
            if (isLoggingEnabled()) {
                LOG.error("Command unresolved (UNCERTAIN_FAILURE)");
            }
            return CommandVerificationResult.UNCERTAIN_FAILURE;
        }

        if (isLoggingEnabled()) {
            if (isLoggingEnabled()) {
                LOG.info("Command status resolved to SUCCESS");
            }
        }
        return CommandVerificationResult.SUCCESS;
    }

    private boolean isLoggingEnabled() {
        return L.isEnabled(L.PUMP);
    }

    public static Duration calculateBolusDuration(double units) {
        return calculateBolusDuration(units, OmnipodConst.POD_BOLUS_DELIVERY_RATE);
    }

    private static Duration calculateBolusDuration(double units, double deliveryRate) {
        return Duration.standardSeconds((long) Math.ceil(units / deliveryRate));
    }

    public static boolean isCertainFailure(Exception ex) {
        return ex instanceof OmnipodException && ((OmnipodException) ex).isCertainFailure();
    }

    private enum CommandVerificationResult {
        SUCCESS,
        CERTAIN_FAILURE,
        UNCERTAIN_FAILURE
    }
}
