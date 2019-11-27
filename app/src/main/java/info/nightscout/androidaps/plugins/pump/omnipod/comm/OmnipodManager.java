package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
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
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalScheduleMapper;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.SP;

public class OmnipodManager {
    private static final int SETUP_ACTION_VERIFICATION_TRIES = 3;

    protected final OmnipodCommunicationService communicationService;
    protected PodSessionState podState;

    public OmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        this.communicationService = communicationService;
        this.podState = podState;
    }

    // Returns a PumpEnactResult which describes whether or not all commands have been sent successfully
    // After priming should have finished (55 seconds), the pod state is verified.
    // The result of that verification is passed to the SetupActionResultHandler
    public PumpEnactResult pairAndPrime(SetupActionResultHandler resultHandler) {
        try {
            if (podState == null) {
                podState = communicationService.executeAction(new PairAction(new PairService()));
            }
            if (podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
                communicationService.executeAction(new PrimeAction(new PrimeService(), podState));

                executeDelayed(() -> verifySetupAction(statusResponse -> PrimeAction.updatePrimingStatus(podState, statusResponse), //
                        SetupProgress.PRIMING_FINISHED, resultHandler), //
                        OmnipodConst.POD_PRIME_DURATION);
            } else {
                // TODO use string resource
                return new PumpEnactResult().success(false).enacted(false).comment("Illegal setup state: " + podState.getSetupProgress().name());
            }
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    // Returns a PumpEnactResult which describes whether or not all commands have been sent successfully
    // After inserting the cannula should have finished (10 seconds), the pod state is verified.
    // The result of that verification is passed to the SetupActionResultHandler
    public PumpEnactResult insertCannula(Profile profile, SetupActionResultHandler resultHandler) {
        if (podState == null || podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            // TODO use string resource
            return new PumpEnactResult().success(false).enacted(false).comment("Pod should be paired and primed first");
        } else if (podState.getSetupProgress().isAfter(SetupProgress.CANNULA_INSERTING)) {
            // TODO use string resource
            return new PumpEnactResult().success(false).enacted(false).comment("Illegal setup state: " + podState.getSetupProgress().name());
        }

        try {
            communicationService.executeAction(new InsertCannulaAction(new InsertCannulaService(), podState,
                    BasalScheduleMapper.mapProfileToBasalSchedule(profile)));

            executeDelayed(() -> verifySetupAction(statusResponse -> InsertCannulaAction.updateCannulaInsertionStatus(podState, statusResponse), //
                    SetupProgress.COMPLETED, resultHandler),
                    OmnipodConst.POD_CANNULA_INSERTION_DURATION);
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult cancelBolus() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.BOLUS, true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult getPodStatus() {
        if (podState == null) {
            // TODO use string resource
            return new PumpEnactResult().success(false).enacted(false).comment("Pod should be paired and primed first");
        }

        try {
            // TODO how can we return the status response? Also refer to TODO in interface
            StatusResponse statusResponse = communicationService.executeAction(new GetStatusAction(podState));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult deactivatePod() {
        if (podState == null) {
            // TODO use string resource
            return new PumpEnactResult().success(false).enacted(false).comment("Pod should be paired and primed first");
        }

        try {
            communicationService.executeAction(new DeactivatePodAction(podState, true));
            resetPodState();
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult setBasalProfile(Profile basalProfile) {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new SetBasalScheduleAction(podState,
                    BasalScheduleMapper.mapProfileToBasalSchedule(basalProfile),
                    false, podState.getScheduleOffset(), true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult resetPodState() {
        podState = null;
        SP.remove(OmnipodConst.Prefs.PodState);

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult bolus(Double units) {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new BolusAction(podState, units, true, true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        // TODO calculate bolus duration
        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new SetTempBasalAction(new SetTempBasalService(),
                    podState, tempBasalPair.getInsulinRate(), Duration.standardMinutes(tempBasalPair.getDurationMinutes()),
                    true, true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult cancelTemporaryBasal() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.TEMP_BASAL, true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult acknowledgeAlerts() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new AcknowledgeAlertsAction(podState, podState.getActiveAlerts()));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }
        return new PumpEnactResult().success(true).enacted(true);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult getPodInfo(PodInfoType podInfoType) {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            // TODO how can we return the PodInfo response?
            PodInfoResponse podInfoResponse = communicationService.executeAction(new GetPodInfoAction(podState, podInfoType));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult suspendDelivery() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult resumeDelivery() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                    true, podState.getScheduleOffset(), true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult setTime() {
        if (!isInitialized()) {
            return createNotInitializedResult();
        }

        try {
            // Suspend delivery
            communicationService.executeAction(new CancelDeliveryAction(podState, EnumSet.allOf(DeliveryType.class), false));

            // Joda seems to cache the default time zone, so we use the JVM's
            DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
            podState.setTimeZone(DateTimeZone.getDefault());

            // Resume delivery
            communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                    true, podState.getScheduleOffset(), true));
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public OmnipodCommunicationService getCommunicationService() {
        return communicationService;
    }

    public DateTime getTime() {
        return podState.getTime();
    }

    public boolean isInitialized() {
        return podState != null && podState.getSetupProgress() == SetupProgress.COMPLETED;
    }

    public String getPodStateAsString() {
        return podState == null ? "null" : podState.toString();
    }

    private void executeDelayed(Runnable r, Duration timeout) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.schedule(r, timeout.getMillis(), TimeUnit.MILLISECONDS);
    }

    private PumpEnactResult createNotInitializedResult() {
        // TODO use string resource
        return new PumpEnactResult().success(false).enacted(false).comment("Pod should be initialized first");
    }

    public PodSessionState getPodState() {
        return this.podState;
    }

    private void verifySetupAction(StatusResponseHandler statusResponseHandler, SetupProgress expectedSetupProgress, SetupActionResultHandler resultHandler) {
        SetupActionResult result = null;
        for (int i = 0; SETUP_ACTION_VERIFICATION_TRIES > i; i++) {
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

    @FunctionalInterface
    private interface StatusResponseHandler {
        void handle(StatusResponse podState);
    }
}