package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.AcknowledgeAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.AssignAddressAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.CancelDeliveryAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.DeactivatePodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.GetPodInfoAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.GetStatusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.InsertCannulaAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.PrimeAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetTempBasalAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetupPodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.InsertCannulaService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateChangedHandler;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.SingleSubject;

public class OmnipodManager {
    private static final int ACTION_VERIFICATION_TRIES = 3;

    protected final OmnipodCommunicationManager communicationService;
    private final PodStateChangedHandler podStateChangedHandler;
    protected PodSessionState podState;

    private ActiveBolusData activeBolusData;
    private final Object bolusDataMutex = new Object();

    private AAPSLogger aapsLogger;
    private SP sp;

    public OmnipodManager(AAPSLogger aapsLogger,
                          SP sp,
                          OmnipodCommunicationManager communicationService,
                          PodSessionState podState,
                          PodStateChangedHandler podStateChangedHandler) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.communicationService = communicationService;
        if (podState != null) {
            podState.setStateChangedHandler(podStateChangedHandler);
        }
        this.podState = podState;
        this.podStateChangedHandler = podStateChangedHandler;
    }

    public synchronized Single<SetupActionResult> pairAndPrime(int address) {
        logStartingCommandExecution("pairAndPrime");

        try {
            if (podState == null || podState.getSetupProgress().isBefore(SetupProgress.POD_CONFIGURED)) {
                // Always send both 0x07 and 0x03 on retries
                podState = communicationService.executeAction(
                        new AssignAddressAction(podStateChangedHandler, address));

                communicationService.executeAction(new SetupPodAction(podState));
            } else if (SetupProgress.PRIMING.isBefore(podState.getSetupProgress())) {
                throw new IllegalSetupProgressException(SetupProgress.POD_CONFIGURED, podState.getSetupProgress());
            }

            communicationService.executeAction(new PrimeAction(new PrimeService(), podState));
        } finally {
            logCommandExecutionFinished("pairAndPrime");
        }

        long delayInSeconds = calculateBolusDuration(OmnipodConst.POD_PRIME_BOLUS_UNITS, OmnipodConst.POD_PRIMING_DELIVERY_RATE).getStandardSeconds();

        return Single.timer(delayInSeconds, TimeUnit.SECONDS) //
                .map(o -> verifySetupAction(statusResponse ->
                        PrimeAction.updatePrimingStatus(podState, statusResponse, aapsLogger), SetupProgress.PRIMING_FINISHED)) //
                .observeOn(Schedulers.io());
    }

    public synchronized Single<SetupActionResult> insertCannula(BasalSchedule basalSchedule) {
        if (podState == null || podState.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, podState == null ? null : podState.getSetupProgress());
        } else if (podState.getSetupProgress().isAfter(SetupProgress.CANNULA_INSERTING)) {
            throw new IllegalSetupProgressException(SetupProgress.CANNULA_INSERTING, podState.getSetupProgress());
        }

        logStartingCommandExecution("insertCannula [basalSchedule=" + basalSchedule + "]");

        try {
            communicationService.executeAction(new InsertCannulaAction(new InsertCannulaService(), podState, basalSchedule));
        } finally {
            logCommandExecutionFinished("insertCannula");
        }

        long delayInSeconds = calculateBolusDuration(OmnipodConst.POD_CANNULA_INSERTION_BOLUS_UNITS, OmnipodConst.POD_CANNULA_INSERTION_DELIVERY_RATE).getStandardSeconds();

        return Single.timer(delayInSeconds, TimeUnit.SECONDS) //
                .map(o -> verifySetupAction(statusResponse ->
                        InsertCannulaAction.updateCannulaInsertionStatus(podState, statusResponse, aapsLogger), SetupProgress.COMPLETED)) //
                .observeOn(Schedulers.io());
    }

    public synchronized StatusResponse getPodStatus() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, null);
        }

        logStartingCommandExecution("getPodStatus");

        try {
            return communicationService.executeAction(new GetStatusAction(podState));
        } finally {
            logCommandExecutionFinished("getPodStatus");
        }
    }

    public synchronized PodInfoResponse getPodInfo(PodInfoType podInfoType) {
        assertReadyForDelivery();

        logStartingCommandExecution("getPodInfo");

        try {
            return communicationService.executeAction(new GetPodInfoAction(podState, podInfoType));
        } finally {
            logCommandExecutionFinished("getPodInfo");
        }
    }

    public synchronized StatusResponse acknowledgeAlerts() {
        assertReadyForDelivery();

        logStartingCommandExecution("acknowledgeAlerts");

        try {
            return executeAndVerify(() -> communicationService.executeAction(new AcknowledgeAlertsAction(podState, podState.getActiveAlerts())));
        } finally {
            logCommandExecutionFinished("acknowledgeAlerts");
        }
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized StatusResponse setBasalSchedule(BasalSchedule schedule, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("setBasalSchedule [basalSchedule=" + schedule + ", acknowledgementBeep=" + acknowledgementBeep + "]");

        try {
            cancelDelivery(EnumSet.allOf(DeliveryType.class), acknowledgementBeep);
        } catch (Exception ex) {
            logCommandExecutionFinished("setBasalSchedule");
            throw ex;
        }

        try {
            try {
                return executeAndVerify(() -> communicationService.executeAction(new SetBasalScheduleAction(podState, schedule,
                        false, podState.getScheduleOffset(), acknowledgementBeep)));
            } catch (OmnipodException ex) {
                // Treat all exceptions as uncertain failures, because all delivery has been suspended here.
                // Setting this to an uncertain failure will enable for the user to get an appropriate warning
                ex.setCertainFailure(false);
                throw ex;
            }
        } finally {
            logCommandExecutionFinished("setBasalSchedule");
        }
    }

    // CAUTION: cancels temp basal and then sets new temp basal. An OmnipodException[certainFailure=false] indicates that the pod might have cancelled the previous temp basal, but did not set a new temp basal
    public synchronized StatusResponse setTemporaryBasal(double rate, Duration duration, boolean acknowledgementBeep, boolean completionBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("setTemporaryBasal [rate=" + rate + ", duration=" + duration + ", acknowledgementBeep=" + acknowledgementBeep + ", completionBeep=" + completionBeep + "]");

        try {
            cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
        } catch (Exception ex) {
            logCommandExecutionFinished("setTemporaryBasal");
            throw ex;
        }

        try {
            return executeAndVerify(() -> communicationService.executeAction(new SetTempBasalAction(
                    podState, rate, duration,
                    acknowledgementBeep, completionBeep)));
        } catch (OmnipodException ex) {
            // Treat all exceptions as uncertain failures, because all delivery has been suspended here.
            // Setting this to an uncertain failure will enable for the user to get an appropriate warning
            ex.setCertainFailure(false);
            throw ex;
        } finally {
            logCommandExecutionFinished("setTemporaryBasal");
        }
    }

    public synchronized void cancelTemporaryBasal(boolean acknowledgementBeep) {
        cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
    }

    private synchronized StatusResponse cancelDelivery(EnumSet<DeliveryType> deliveryTypes, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("cancelDelivery [deliveryTypes=" + deliveryTypes + ", acknowledgementBeep=" + acknowledgementBeep + "]");

        try {
            return executeAndVerify(() -> {
                StatusResponse statusResponse = communicationService.executeAction(new CancelDeliveryAction(podState, deliveryTypes, acknowledgementBeep));
                aapsLogger.info(LTag.PUMPBTCOMM, "Status response after cancel delivery[types={}]: {}", deliveryTypes.toString(), statusResponse.toString());
                return statusResponse;
            });
        } finally {
            logCommandExecutionFinished("cancelDelivery");
        }
    }

    // Returns a SingleSubject that returns when the bolus has finished.
    // When a bolus is cancelled, it will return after cancellation and report the estimated units delivered
    // Only throws OmnipodException[certainFailure=false]
    public synchronized BolusCommandResult bolus(Double units, boolean acknowledgementBeep, boolean completionBeep, BolusProgressIndicationConsumer progressIndicationConsumer) {
        assertReadyForDelivery();

        logStartingCommandExecution("bolus [units=" + units + ", acknowledgementBeep=" + acknowledgementBeep + ", completionBeep=" + completionBeep + "]");

        CommandDeliveryStatus commandDeliveryStatus = CommandDeliveryStatus.SUCCESS;

        try {
            executeAndVerify(() -> communicationService.executeAction(new BolusAction(podState, units, acknowledgementBeep, completionBeep)));
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                throw ex;
            }

            // Catch uncertain exceptions as we still want to report bolus progress indication
            aapsLogger.error(LTag.PUMPBTCOMM, "Caught exception[certainFailure=false] in bolus", ex);
            commandDeliveryStatus = CommandDeliveryStatus.UNCERTAIN_FAILURE;
        } finally {
            logCommandExecutionFinished("bolus");
        }

        DateTime startDate = DateTime.now().minus(OmnipodConst.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);

        CompositeDisposable disposables = new CompositeDisposable();
        Duration bolusDuration = calculateBolusDuration(units, OmnipodConst.POD_BOLUS_DELIVERY_RATE);
        Duration estimatedRemainingBolusDuration = bolusDuration.minus(OmnipodConst.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);

        if (progressIndicationConsumer != null) {
            int numberOfProgressReports = Math.max(20, Math.min(100, (int) Math.ceil(units) * 10));
            long progressReportInterval = estimatedRemainingBolusDuration.getMillis() / numberOfProgressReports;

            disposables.add(Flowable.intervalRange(0, numberOfProgressReports + 1, 0, progressReportInterval, TimeUnit.MILLISECONDS) //
                    .observeOn(Schedulers.io()) //
                    .subscribe(count -> {
                        int percentage = (int) ((double) count / numberOfProgressReports * 100);
                        double estimatedUnitsDelivered = activeBolusData == null ? 0 : activeBolusData.estimateUnitsDelivered();
                        progressIndicationConsumer.accept(estimatedUnitsDelivered, percentage);
                    }));
        }

        SingleSubject<BolusDeliveryResult> bolusCompletionSubject = SingleSubject.create();

        synchronized (bolusDataMutex) {
            activeBolusData = new ActiveBolusData(units, startDate, bolusCompletionSubject, disposables);
        }

        disposables.add(Completable.complete() //
                .delay(estimatedRemainingBolusDuration.getMillis() + 250, TimeUnit.MILLISECONDS) //
                .observeOn(Schedulers.io()) //
                .doOnComplete(() -> {
                    synchronized (bolusDataMutex) {
                        double unitsNotDelivered = 0.0d;

                        for (int i = 0; i < ACTION_VERIFICATION_TRIES; i++) {
                            try {
                                // Retrieve a status response in order to update the pod state
                                StatusResponse statusResponse = getPodStatus();
                                if (statusResponse.getDeliveryStatus().isBolusing()) {
                                    throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
                                } else {
                                    break;
                                }
                            } catch (PodFaultException ex) {
                                // Substract units not delivered in case of a Pod failure
                                unitsNotDelivered = ex.getFaultEvent().getInsulinNotDelivered();

                                aapsLogger.debug(LTag.PUMPBTCOMM, "Caught PodFaultException in bolus completion verification", ex);
                                break;
                            } catch (Exception ex) {
                                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in bolus completion verification", ex);
                            }
                        }

                        if (hasActiveBolus()) {
                            activeBolusData.bolusCompletionSubject.onSuccess(new BolusDeliveryResult(units - unitsNotDelivered));
                            activeBolusData = null;
                        }
                    }
                })
                .subscribe());

        return new BolusCommandResult(commandDeliveryStatus, bolusCompletionSubject);
    }

    public synchronized void cancelBolus(boolean acknowledgementBeep) {
        assertReadyForDelivery();

        synchronized (bolusDataMutex) {
            if (activeBolusData == null) {
                throw new IllegalDeliveryStatusException(DeliveryStatus.BOLUS_IN_PROGRESS, podState.getLastDeliveryStatus());
            }

            logStartingCommandExecution("cancelBolus [acknowledgementBeep=" + acknowledgementBeep + "]");

            try {
                StatusResponse statusResponse = cancelDelivery(EnumSet.of(DeliveryType.BOLUS), acknowledgementBeep);
                discardActiveBolusData(statusResponse.getInsulinNotDelivered());
            } catch (PodFaultException ex) {
                discardActiveBolusData(ex.getFaultEvent().getInsulinNotDelivered());
                throw ex;
            } finally {
                logCommandExecutionFinished("cancelBolus");
            }
        }
    }

    private void discardActiveBolusData(double unitsNotDelivered) {
        synchronized (bolusDataMutex) {
            activeBolusData.getDisposables().dispose();
            activeBolusData.getBolusCompletionSubject().onSuccess(new BolusDeliveryResult(activeBolusData.getUnits() - unitsNotDelivered));
            activeBolusData = null;
        }
    }

    public synchronized void suspendDelivery(boolean acknowledgementBeep) {
        cancelDelivery(EnumSet.allOf(DeliveryType.class), acknowledgementBeep);
    }

    // Same as setting basal schedule, but without suspending delivery first
    public synchronized StatusResponse resumeDelivery(boolean acknowledgementBeep) {
        assertReadyForDelivery();
        logStartingCommandExecution("resumeDelivery");

        try {
            return executeAndVerify(() -> communicationService.executeAction(new SetBasalScheduleAction(podState, podState.getBasalSchedule(),
                    false, podState.getScheduleOffset(), acknowledgementBeep)));
        } finally {
            logCommandExecutionFinished("resumeDelivery");
        }
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized void setTime(boolean acknowledgementBeeps) {
        assertReadyForDelivery();

        logStartingCommandExecution("setTime [acknowledgementBeeps=" + acknowledgementBeeps + "]");

        try {
            cancelDelivery(EnumSet.allOf(DeliveryType.class), acknowledgementBeeps);
        } catch (Exception ex) {
            logCommandExecutionFinished("setTime");
            throw ex;
        }

        DateTimeZone oldTimeZone = podState.getTimeZone();

        try {
            // Joda seems to cache the default time zone, so we use the JVM's
            DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
            podState.setTimeZone(DateTimeZone.getDefault());

            setBasalSchedule(podState.getBasalSchedule(), acknowledgementBeeps);
        } catch (OmnipodException ex) {
            // Treat all exceptions as uncertain failures, because all delivery has been suspended here.
            // Setting this to an uncertain failure will enable for the user to get an appropriate warning
            podState.setTimeZone(oldTimeZone);
            ex.setCertainFailure(false);
            throw ex;
        } finally {
            logCommandExecutionFinished("setTime");
        }
    }

    public synchronized void deactivatePod() {
        if (podState == null) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, null);
        }

        logStartingCommandExecution("deactivatePod");

        // Try to get pulse log for diagnostics
        // FIXME replace by storing to file
        try {
            PodInfoResponse podInfoResponse = communicationService.executeAction(new GetPodInfoAction(podState, PodInfoType.RECENT_PULSE_LOG));
            PodInfoRecentPulseLog pulseLogInfo = podInfoResponse.getPodInfo();
            aapsLogger.info(LTag.PUMPBTCOMM, "Retrieved pulse log from the pod: {}", pulseLogInfo.toString());
        } catch (Exception ex) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to retrieve pulse log from the pod", ex);
        }

        try {
            // Always send acknowledgement beeps here. Matches the PDM's behavior
            communicationService.executeAction(new DeactivatePodAction(podState, true));
        } catch (PodFaultException ex) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Ignoring PodFaultException in deactivatePod", ex);
        } finally {
            logCommandExecutionFinished("deactivatePod");
        }

        resetPodState(false);
    }

    public void resetPodState(boolean forcedByUser) {
        aapsLogger.warn(LTag.PUMPBTCOMM, "resetPodState has been called. forcedByUser={}", forcedByUser);
        podState = null;
        sp.remove(OmnipodConst.Prefs.PodState);
    }

    public OmnipodCommunicationManager getCommunicationService() {
        return communicationService;
    }

    public DateTime getTime() {
        return podState.getTime();
    }

    public boolean isReadyForDelivery() {
        return podState != null && podState.getSetupProgress() == SetupProgress.COMPLETED;
    }

    public boolean hasActiveBolus() {
        synchronized (bolusDataMutex) {
            return activeBolusData != null;
        }
    }

    // FIXME this is dirty, we should not expose the original pod state
    public PodSessionState getPodState() {
        return this.podState;
    }

    public String getPodStateAsString() {
        return podState == null ? "null" : podState.toString();
    }

    // Only works for commands with nonce resyncable message blocks
    // FIXME method is too big, needs refactoring
    private StatusResponse executeAndVerify(VerifiableAction runnable) {
        try {
            return runnable.run();
        } catch (Exception originalException) {
            if (isCertainFailure(originalException)) {
                throw originalException;
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Caught exception in executeAndVerify. Verifying command by using cancel none command to verify nonce", originalException);

                try {
                    logStartingCommandExecution("verifyCommand");
                    StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podState,
                            new CancelDeliveryCommand(podState.getCurrentNonce(), BeepType.NO_BEEP, DeliveryType.NONE), false);
                    aapsLogger.info(LTag.PUMPBTCOMM, "Command status resolved to SUCCESS. Status response after cancelDelivery[types=DeliveryType.NONE]: {}", statusResponse);

                    return statusResponse;
                } catch (NonceOutOfSyncException verificationException) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Command resolved to FAILURE (CERTAIN_FAILURE)", verificationException);

                    if (originalException instanceof OmnipodException) {
                        ((OmnipodException) originalException).setCertainFailure(true);
                        throw originalException;
                    } else {
                        OmnipodException newException = new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, originalException);
                        newException.setCertainFailure(true);
                        throw newException;
                    }
                } catch (Exception verificationException) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Command unresolved (UNCERTAIN_FAILURE)", verificationException);
                    throw originalException;
                } finally {
                    logCommandExecutionFinished("verifyCommand");
                }
            }
        }
    }

    private void assertReadyForDelivery() {
        if (!isReadyForDelivery()) {
            throw new IllegalSetupProgressException(SetupProgress.COMPLETED, podState == null ? null : podState.getSetupProgress());
        }
    }

    private SetupActionResult verifySetupAction(StatusResponseConsumer setupActionResponseHandler, SetupProgress expectedSetupProgress) {
        SetupActionResult result = null;
        for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
            try {
                StatusResponse delayedStatusResponse = communicationService.executeAction(new GetStatusAction(podState));
                setupActionResponseHandler.accept(delayedStatusResponse);

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
        return result;
    }

    private void logStartingCommandExecution(String action) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Starting command execution for action: " + action);
    }

    private void logCommandExecutionFinished(String action) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Command execution finished for action: " + action);
    }

    private static Duration calculateBolusDuration(double units, double deliveryRate) {
        return Duration.standardSeconds((long) Math.ceil(units / deliveryRate));
    }

    public static Duration calculateBolusDuration(double units) {
        return calculateBolusDuration(units, OmnipodConst.POD_BOLUS_DELIVERY_RATE);
    }

    public static boolean isCertainFailure(Exception ex) {
        return ex instanceof OmnipodException && ((OmnipodException) ex).isCertainFailure();
    }

    public static int generateRandomAddress() {
        // Create random address with 20 bits to match PDM, could easily use 24 bits instead
        return 0x1f000000 | (new Random().nextInt() & 0x000fffff);
    }

    public static boolean isValidAddress(int address) {
        return (0x1f000000 | (address & 0x000fffff)) == address;
    }

    public static class BolusCommandResult {
        private final CommandDeliveryStatus commandDeliveryStatus;
        private final SingleSubject<BolusDeliveryResult> deliveryResultSubject;

        public BolusCommandResult(CommandDeliveryStatus commandDeliveryStatus, SingleSubject<BolusDeliveryResult> deliveryResultSubject) {
            this.commandDeliveryStatus = commandDeliveryStatus;
            this.deliveryResultSubject = deliveryResultSubject;
        }

        public CommandDeliveryStatus getCommandDeliveryStatus() {
            return commandDeliveryStatus;
        }

        public SingleSubject<BolusDeliveryResult> getDeliveryResultSubject() {
            return deliveryResultSubject;
        }
    }

    public static class BolusDeliveryResult {
        private final double unitsDelivered;

        public BolusDeliveryResult(double unitsDelivered) {
            this.unitsDelivered = unitsDelivered;
        }

        public double getUnitsDelivered() {
            return unitsDelivered;
        }
    }

    public enum CommandDeliveryStatus {
        SUCCESS,
        CERTAIN_FAILURE,
        UNCERTAIN_FAILURE
    }

    // TODO replace with Consumer when our min API level >= 24
    @FunctionalInterface
    private interface StatusResponseConsumer {
        void accept(StatusResponse statusResponse);
    }

    private static class ActiveBolusData {
        private final double units;
        private volatile DateTime startDate;
        private volatile SingleSubject<BolusDeliveryResult> bolusCompletionSubject;
        private volatile CompositeDisposable disposables;

        private ActiveBolusData(double units, DateTime startDate, SingleSubject<BolusDeliveryResult> bolusCompletionSubject, CompositeDisposable disposables) {
            this.units = units;
            this.startDate = startDate;
            this.bolusCompletionSubject = bolusCompletionSubject;
            this.disposables = disposables;
        }

        public double getUnits() {
            return units;
        }

        public DateTime getStartDate() {
            return startDate;
        }

        public CompositeDisposable getDisposables() {
            return disposables;
        }

        public SingleSubject<BolusDeliveryResult> getBolusCompletionSubject() {
            return bolusCompletionSubject;
        }

        public double estimateUnitsDelivered() {
            long elapsedMillis = new Duration(startDate, DateTime.now()).getMillis();
            long totalDurationMillis = (long) (units / OmnipodConst.POD_BOLUS_DELIVERY_RATE * 1000);
            double factor = (double) elapsedMillis / totalDurationMillis;
            double estimatedUnits = Math.min(1D, factor) * units;

            int roundingDivisor = (int) (1 / OmnipodConst.POD_PULSE_SIZE);
            return (double) Math.round(estimatedUnits * roundingDivisor) / roundingDivisor;
        }
    }

    // Could be replaced with Supplier<StatusResponse> when min API level >= 24
    @FunctionalInterface
    private interface VerifiableAction {
        StatusResponse run();
    }
}
