package info.nightscout.androidaps.plugins.pump.omnipod.driver.manager;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.AcknowledgeAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.AssignAddressAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.CancelDeliveryAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.ConfigureBeepAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.DeactivatePodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.GetPodInfoAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.GetStatusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.InsertCannulaAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.PrimeAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetTempBasalAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetupPodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommandFailedAfterChangingDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalActivationProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodProgressStatusVerificationFailedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PrecedingCommandFailedUncertainlyException;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.SingleSubject;

public class OmnipodManager {
    private static final int ACTION_VERIFICATION_TRIES = 1;

    private final OmnipodRileyLinkCommunicationManager communicationService;
    private final PodStateManager podStateManager;

    private ActiveBolusData activeBolusData;
    private SingleSubject<Boolean> bolusCommandExecutionSubject;

    private final Object bolusDataMutex = new Object();

    private final AAPSLogger aapsLogger;

    public OmnipodManager(AAPSLogger aapsLogger,
                          OmnipodRileyLinkCommunicationManager communicationService,
                          PodStateManager podStateManager) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod State Manager can not be null");
        }
        this.aapsLogger = aapsLogger;
        this.communicationService = communicationService;

        this.podStateManager = podStateManager;
    }

    public synchronized Single<Boolean> pairAndPrime() {
        if (podStateManager.isPodInitialized()) {
            if (podStateManager.getActivationProgress().isAfter(ActivationProgress.PRIMING)) {
                return Single.just(true);
            }
            if (podStateManager.getActivationProgress().needsPrimingVerification()) {
                return Single.fromCallable(() -> verifyPodProgressStatus(PodProgressStatus.PRIMING_COMPLETED, ActivationProgress.PRIMING_COMPLETED));
            }
        }

        // Always send both 0x07 and 0x03 on retries
        if (podStateManager.getActivationProgress().isBefore(ActivationProgress.PAIRING_COMPLETED)) {
            communicationService.executeAction(
                    new AssignAddressAction(podStateManager, aapsLogger));

            communicationService.executeAction(new SetupPodAction(podStateManager, aapsLogger));
        }

        communicationService.executeAction(new PrimeAction(new PrimeService(), podStateManager));

        long delayInMillis = calculateEstimatedBolusDuration(DateTime.now().minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION), OmnipodConstants.POD_PRIME_BOLUS_UNITS, OmnipodConstants.POD_PRIMING_DELIVERY_RATE).getMillis();

        return Single.timer(delayInMillis, TimeUnit.MILLISECONDS) //
                .map(o -> verifyPodProgressStatus(PodProgressStatus.PRIMING_COMPLETED, ActivationProgress.PRIMING_COMPLETED)) //
                .subscribeOn(Schedulers.io());
    }

    public synchronized Single<Boolean> insertCannula(
            BasalSchedule basalSchedule, Duration expirationReminderTimeBeforeShutdown, Integer lowReservoirAlertUnits) {
        if (podStateManager.getActivationProgress().isBefore(ActivationProgress.PRIMING_COMPLETED)) {
            throw new IllegalActivationProgressException(ActivationProgress.PRIMING_COMPLETED, podStateManager.getActivationProgress());
        }

        if (podStateManager.isPodInitialized()) {
            if (podStateManager.getActivationProgress().isCompleted()) {
                return Single.just(true);
            }
            if (podStateManager.getActivationProgress().needsCannulaInsertionVerification()) {
                return Single.fromCallable(() -> verifyPodProgressStatus(PodProgressStatus.ABOVE_FIFTY_UNITS, ActivationProgress.COMPLETED));
            }
        }

        communicationService.executeAction(new InsertCannulaAction(podStateManager, basalSchedule, expirationReminderTimeBeforeShutdown, lowReservoirAlertUnits));

        long delayInMillis = calculateEstimatedBolusDuration(DateTime.now().minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION), OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS, OmnipodConstants.POD_CANNULA_INSERTION_DELIVERY_RATE).getMillis();

        return Single.timer(delayInMillis, TimeUnit.MILLISECONDS) //
                .map(o -> verifyPodProgressStatus(PodProgressStatus.ABOVE_FIFTY_UNITS, ActivationProgress.COMPLETED)) //
                .subscribeOn(Schedulers.io());
    }

    public synchronized StatusResponse getPodStatus() {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }

        return communicationService.executeAction(new GetStatusAction(podStateManager));

    }

    public synchronized PodInfoResponse getPodInfo(PodInfoType podInfoType) {
        assertReadyForDelivery();

        return communicationService.executeAction(new GetPodInfoAction(podStateManager, podInfoType));
    }

    public synchronized StatusResponse configureAlerts(List<AlertConfiguration> alertConfigurations) {
        assertReadyForDelivery();

        StatusResponse statusResponse = executeAndVerify(() -> communicationService.executeAction(new ConfigureAlertsAction(podStateManager, alertConfigurations)));
        ConfigureAlertsAction.updateConfiguredAlerts(podStateManager, alertConfigurations);
        return statusResponse;

    }

    public synchronized StatusResponse acknowledgeAlerts() {
        assertReadyForDelivery();

        return executeAndVerify(() -> communicationService.executeAction(new AcknowledgeAlertsAction(podStateManager, podStateManager.getActiveAlerts())));
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized void setBasalSchedule(BasalSchedule schedule, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        if (!podStateManager.isBasalCertain()) {
            try {
                getPodStatus();
            } catch (OmnipodException ex) {
                ex.setCertainFailure(true);
                throw ex;
            }
        }

        boolean wasSuspended = podStateManager.isSuspended();
        if (!wasSuspended) {
            try {
                suspendDelivery(acknowledgementBeep);
            } catch (OmnipodException ex) {
                if (ex.isCertainFailure()) {
                    throw ex;
                }

                // Uncertain failure
                throw new PrecedingCommandFailedUncertainlyException(ex);
            }
        }

        BasalSchedule oldBasalSchedule = podStateManager.getBasalSchedule();

        try {
            podStateManager.setBasalSchedule(schedule);
            podStateManager.setBasalCertain(false);
            executeAndVerify(() -> communicationService.executeAction(new SetBasalScheduleAction(podStateManager, schedule,
                    false, podStateManager.getScheduleOffset(), acknowledgementBeep)));
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                podStateManager.setBasalSchedule(oldBasalSchedule);
                podStateManager.setBasalCertain(true);
                if (!wasSuspended) {
                    throw new CommandFailedAfterChangingDeliveryStatusException("Suspending delivery succeeded but setting the new basal schedule did not", ex);
                }
                throw ex;
            }

            // Uncertain failure
            throw ex;
        }
    }

    // CAUTION: cancels temp basal and then sets new temp basal. An OmnipodException[certainFailure=false] indicates that the pod might have cancelled the previous temp basal, but did not set a new temp basal
    public synchronized void setTemporaryBasal(double rate, Duration duration, boolean acknowledgementBeep, boolean completionBeep) {
        assertReadyForDelivery();

        if (!podStateManager.isTempBasalCertain() || !podStateManager.isBasalCertain()) {
            try {
                getPodStatus();
            } catch (OmnipodException ex) {
                ex.setCertainFailure(true);
                throw ex;
            }
        }

        if (podStateManager.isSuspended()) {
            throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, DeliveryStatus.SUSPENDED);
        }

        boolean cancelCurrentTbr = podStateManager.isTempBasalRunning();

        if (cancelCurrentTbr) {
            try {
                cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
            } catch (OmnipodException ex) {
                if (ex.isCertainFailure()) {
                    throw ex;
                }

                // Uncertain failure
                throw new PrecedingCommandFailedUncertainlyException(ex);
            }
        }

        try {
            podStateManager.setTempBasal(DateTime.now().plus(OmnipodConstants.AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION), rate, duration);
            podStateManager.setTempBasalCertain(false);
            executeAndVerify(() -> communicationService.executeAction(new SetTempBasalAction(
                    podStateManager, rate, duration, acknowledgementBeep, completionBeep)));
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                podStateManager.clearTempBasal();
                podStateManager.setTempBasalCertain(true);
                if (cancelCurrentTbr) {
                    throw new CommandFailedAfterChangingDeliveryStatusException("Failed to set new TBR while cancelling old TBR succeeded", ex);
                }
                throw ex;
            }

            // Uncertain failure
            throw ex;
        }
    }

    public synchronized void cancelTemporaryBasal(boolean acknowledgementBeep) {
        cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
    }

    private synchronized StatusResponse cancelDelivery(EnumSet<DeliveryType> deliveryTypes, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        if (!podStateManager.isTempBasalCertain() || !podStateManager.isBasalCertain()) {
            try {
                getPodStatus();
            } catch (OmnipodException ex) {
                ex.setCertainFailure(true);
                throw ex;
            }
        }

        if (deliveryTypes.contains(DeliveryType.BASAL)) {
            podStateManager.setBasalCertain(false);
        }
        if (deliveryTypes.contains(DeliveryType.TEMP_BASAL)) {
            podStateManager.setTempBasalCertain(false);
        }

        try {
            return executeAndVerify(() -> {
                StatusResponse statusResponse;
                statusResponse = communicationService.executeAction(new CancelDeliveryAction(podStateManager, deliveryTypes, acknowledgementBeep));

                aapsLogger.info(LTag.PUMPCOMM, "Status response after cancel delivery[types={}]: {}", deliveryTypes.toString(), statusResponse.toString());
                return statusResponse;
            });
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                if (deliveryTypes.contains(DeliveryType.BASAL)) {
                    podStateManager.setBasalCertain(true);
                }
                if (deliveryTypes.contains(DeliveryType.TEMP_BASAL)) {
                    podStateManager.setTempBasalCertain(true);
                }
            }

            throw ex;
        }
    }

    // Returns a SingleSubject that returns when the bolus has finished.
    // When a bolus is cancelled, it will return after cancellation and report the estimated units delivered
    // Only throws OmnipodException[certainFailure=false]
    public synchronized BolusCommandResult bolus(Double units, boolean acknowledgementBeep, boolean completionBeep, BiConsumer<Double, Integer> progressIndicationConsumer) {
        assertReadyForDelivery();

        if (!podStateManager.isBasalCertain()) {
            try {
                getPodStatus();
            } catch (OmnipodException ex) {
                ex.setCertainFailure(true);
                throw ex;
            }
        }

        if (podStateManager.isSuspended()) {
            throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, DeliveryStatus.SUSPENDED);
        }

        bolusCommandExecutionSubject = SingleSubject.create();

        CommandDeliveryStatus commandDeliveryStatus = CommandDeliveryStatus.SUCCESS;

        try {
            executeAndVerify(() -> communicationService.executeAction(new BolusAction(podStateManager, units, acknowledgementBeep, completionBeep)));
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                bolusCommandExecutionSubject.onSuccess(false);
                bolusCommandExecutionSubject = null;
                throw ex;
            }

            // Catch uncertain exceptions as we still want to report bolus progress indication
            aapsLogger.error(LTag.PUMPCOMM, "Caught exception[certainFailure=false] in bolus", ex);
            commandDeliveryStatus = CommandDeliveryStatus.UNCERTAIN_FAILURE;
        }

        DateTime estimatedBolusStartDate = DateTime.now().minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);
        Duration estimatedBolusDuration = calculateEstimatedBolusDuration(estimatedBolusStartDate, units, OmnipodConstants.POD_BOLUS_DELIVERY_RATE);
        Duration estimatedRemainingBolusDuration = estimatedBolusDuration.minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);

        podStateManager.setLastBolus(estimatedBolusStartDate, units, estimatedBolusDuration, commandDeliveryStatus == CommandDeliveryStatus.SUCCESS);

        CompositeDisposable disposables = new CompositeDisposable();

        if (progressIndicationConsumer != null) {

            long numberOfProgressReports = Math.max(10, Math.min(100, estimatedRemainingBolusDuration.getStandardSeconds()));
            long progressReportInterval = estimatedRemainingBolusDuration.getMillis() / numberOfProgressReports;

            disposables.add(Flowable.intervalRange(0, numberOfProgressReports + 1, 0, progressReportInterval, TimeUnit.MILLISECONDS) //
                    .subscribeOn(Schedulers.io()) //
                    .subscribe(count -> {
                        int percentage = (int) ((double) count / numberOfProgressReports * 100);
                        double estimatedUnitsDelivered = activeBolusData == null ? 0 : activeBolusData.estimateUnitsDelivered();
                        progressIndicationConsumer.accept(estimatedUnitsDelivered, percentage);
                    }));
        }

        SingleSubject<BolusDeliveryResult> bolusCompletionSubject = SingleSubject.create();

        synchronized (bolusDataMutex) {
            activeBolusData = new ActiveBolusData(units, estimatedBolusStartDate, commandDeliveryStatus, bolusCompletionSubject, disposables);
        }

        // Return successful command execution AFTER storing activeBolusData
        //  Otherwise, hasActiveBolus() would return false and the caller would not cancel the bolus.
        bolusCommandExecutionSubject.onSuccess(true);
        bolusCommandExecutionSubject = null;

        disposables.add(Completable.complete() //
                .delay(estimatedRemainingBolusDuration.getMillis(), TimeUnit.MILLISECONDS) //
                .subscribeOn(Schedulers.io()) //
                .doOnComplete(() -> {
                    synchronized (bolusDataMutex) {
                        double bolusNotDelivered = 0.0d;

                        for (int i = 0; i < ACTION_VERIFICATION_TRIES; i++) {
                            try {
                                // Retrieve a status response in order to update the pod state
                                StatusResponse statusResponse = getPodStatus();
                                if (statusResponse.getDeliveryStatus().isBolusing()) {
                                    throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
                                }
                                break;
                            } catch (PodFaultException ex) {
                                // Subtract units not delivered in case of a Pod failure
                                bolusNotDelivered = ex.getDetailedStatus().getBolusNotDelivered();

                                aapsLogger.debug(LTag.PUMPCOMM, "Caught PodFaultException in bolus completion verification", ex);
                                break;
                            } catch (Exception ex) {
                                aapsLogger.debug(LTag.PUMPCOMM, "Ignoring exception in bolus completion verification", ex);
                            }
                        }

                        if (hasActiveBolus()) {
                            activeBolusData.bolusCompletionSubject.onSuccess(new BolusDeliveryResult(units - bolusNotDelivered));
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
                throw new IllegalDeliveryStatusException(DeliveryStatus.BOLUS_IN_PROGRESS, podStateManager.getLastDeliveryStatus());
            }

            try {
                StatusResponse statusResponse = cancelDelivery(EnumSet.of(DeliveryType.BOLUS), acknowledgementBeep);
                discardActiveBolusData(statusResponse.getBolusNotDelivered());
            } catch (PodFaultException ex) {
                discardActiveBolusData(ex.getDetailedStatus().getBolusNotDelivered());
                throw ex;
            }
        }
    }

    private void discardActiveBolusData(double bolusNotDelivered) {
        synchronized (bolusDataMutex) {
            double unitsDelivered = activeBolusData.getUnits() - bolusNotDelivered;
            podStateManager.setLastBolus(activeBolusData.getStartDate(), unitsDelivered, new Duration(activeBolusData.getStartDate(), DateTime.now()), activeBolusData.getCommandDeliveryStatus() == CommandDeliveryStatus.SUCCESS);
            activeBolusData.getDisposables().dispose();
            activeBolusData.getBolusCompletionSubject().onSuccess(new BolusDeliveryResult(unitsDelivered));
            activeBolusData = null;
        }
    }

    public synchronized void suspendDelivery(boolean acknowledgementBeep) {
        assertReadyForDelivery();

        cancelDelivery(EnumSet.allOf(DeliveryType.class), acknowledgementBeep);
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized void setTime(boolean acknowledgementBeeps) {
        assertReadyForDelivery();

        DateTimeZone oldTimeZone = podStateManager.getTimeZone();

        try {
            // Joda seems to cache the default time zone, so we use the JVM's
            DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
            podStateManager.setTimeZone(DateTimeZone.getDefault());

            setBasalSchedule(podStateManager.getBasalSchedule(), acknowledgementBeeps);
        } catch (OmnipodException ex) {
            podStateManager.setTimeZone(oldTimeZone);
            throw ex;
        }

        podStateManager.updateActivatedAt();
    }

    public synchronized void deactivatePod() {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }

        // Try to get pulse log for diagnostics
        try {
            PodInfoResponse podInfoResponse = communicationService.executeAction(new GetPodInfoAction(podStateManager, PodInfoType.RECENT_PULSE_LOG));
            PodInfoRecentPulseLog pulseLogInfo = (PodInfoRecentPulseLog) podInfoResponse.getPodInfo();
            aapsLogger.info(LTag.PUMPCOMM, "Read pulse log from the pod: {}", pulseLogInfo.toString());
        } catch (Exception ex) {
            aapsLogger.warn(LTag.PUMPCOMM, "Failed to read pulse log", ex);
        }

        try {
            // Always send acknowledgement beeps here. Matches the PDM's behavior
            communicationService.executeAction(new DeactivatePodAction(podStateManager, true));
        } catch (PodFaultException ex) {
            aapsLogger.info(LTag.PUMPCOMM, "Ignoring PodFaultException in deactivatePod", ex);
        }

        podStateManager.discardState();
    }

    public synchronized void configureBeeps(BeepConfigType beepType, boolean basalCompletionBeep, Duration basalIntervalBeep,
                                            boolean tempBasalCompletionBeep, Duration tempBasalIntervalBeep,
                                            boolean bolusCompletionBeep, Duration bolusIntervalBeep) {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }
        communicationService.executeAction(new ConfigureBeepAction(
                podStateManager, beepType, basalCompletionBeep,
                basalIntervalBeep, tempBasalCompletionBeep, tempBasalIntervalBeep,
                bolusCompletionBeep, bolusIntervalBeep));
    }

    public synchronized void playTestBeep(BeepConfigType beepType) {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }
        communicationService.executeAction(new ConfigureBeepAction(podStateManager, beepType));
    }

    public OmnipodRileyLinkCommunicationManager getCommunicationService() {
        return communicationService;
    }

    public DateTime getTime() {
        return podStateManager.getTime();
    }

    public boolean hasActiveBolus() {
        synchronized (bolusDataMutex) {
            return activeBolusData != null;
        }
    }

    public SingleSubject<Boolean> getBolusCommandExecutionSubject() {
        return bolusCommandExecutionSubject;
    }

    private boolean isPodRunning() {
        return podStateManager.isPodRunning();
    }

    // Only works for commands with nonce resyncable message blocks
    private StatusResponse executeAndVerify(Supplier<StatusResponse> supplier) {
        try {
            return supplier.get();
        } catch (OmnipodException originalException) {
            if (isCertainFailure(originalException)) {
                throw originalException;
            } else {
                aapsLogger.warn(LTag.PUMPCOMM, "Caught exception in executeAndVerify. Verifying command by using cancel none command to verify nonce", originalException);

                try {
                    StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podStateManager,
                            new CancelDeliveryCommand(podStateManager.getCurrentNonce(), BeepType.NO_BEEP, DeliveryType.NONE), false);
                    aapsLogger.info(LTag.PUMPCOMM, "Command status resolved to SUCCESS. Status response after cancelDelivery[types=DeliveryType.NONE]: {}", statusResponse);

                    return statusResponse;
                } catch (NonceOutOfSyncException verificationException) {
                    aapsLogger.info(LTag.PUMPCOMM, "Command resolved to FAILURE (CERTAIN_FAILURE)", verificationException);
                    originalException.setCertainFailure(true);
                    throw originalException;
                } catch (Exception verificationException) {
                    aapsLogger.warn(LTag.PUMPCOMM, "Command unresolved (UNCERTAIN_FAILURE)", verificationException);
                    throw originalException;
                }
            }
        }
    }

    private void assertReadyForDelivery() {
        if (!isPodRunning()) {
            throw new IllegalPodProgressException(PodProgressStatus.ABOVE_FIFTY_UNITS, podStateManager.hasPodState() ? podStateManager.getPodProgressStatus() : null);
        }
    }

    /**
     * @param expectedPodProgressStatus expected Pod progress status
     * @return true if the Pod's progress status matches the expected status, otherwise false
     * @throws PodProgressStatusVerificationFailedException in case reading the Pod status fails
     */
    private boolean verifyPodProgressStatus(PodProgressStatus expectedPodProgressStatus, ActivationProgress activationProgress) {
        Boolean result = null;
        Throwable lastException = null;

        for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
            try {
                StatusResponse statusResponse = getPodStatus();

                if (statusResponse.getPodProgressStatus().equals(expectedPodProgressStatus)) {
                    podStateManager.setActivationProgress(activationProgress);
                    return true;
                } else {
                    result = false;
                }
            } catch (Exception ex) {
                lastException = ex;
            }
        }

        if (result != null) {
            return result;
        }

        final Throwable ex = lastException;

        throw new PodProgressStatusVerificationFailedException(expectedPodProgressStatus, ex);
    }

    private Duration calculateEstimatedBolusDuration(DateTime startTime, double units, double deliveryRateInUnitsPerSecond) {
        if (!podStateManager.isPodActivationCompleted()) {
            // No basal or temp basal is active yet
            return Duration.standardSeconds((long) Math.ceil(units / deliveryRateInUnitsPerSecond));
        }

        double pulseIntervalInSeconds = OmnipodConstants.POD_PULSE_SIZE / deliveryRateInUnitsPerSecond;
        long numberOfPulses = Math.round(units / OmnipodConstants.POD_PULSE_SIZE);
        double totalEstimatedDurationInSeconds = 0D;

        for (int i = 0; numberOfPulses > i; i++) {
            DateTime estimatedTimeAtPulse = startTime.plusMillis((int) (totalEstimatedDurationInSeconds * 1000));
            double effectiveBasalRateAtPulse = podStateManager.getEffectiveBasalRateAt(estimatedTimeAtPulse);
            double effectivePulsesPerHourAtPulse = effectiveBasalRateAtPulse / OmnipodConstants.POD_PULSE_SIZE;
            double effectiveBasalPulsesPerSecondAtPulse = effectivePulsesPerHourAtPulse / 3600;
            double effectiveBasalPulsesPerBolusPulse = pulseIntervalInSeconds * effectiveBasalPulsesPerSecondAtPulse;

            totalEstimatedDurationInSeconds += pulseIntervalInSeconds * (1 + effectiveBasalPulsesPerBolusPulse);
        }

        return Duration.millis(Math.round(totalEstimatedDurationInSeconds * 1000));
    }

    public static boolean isCertainFailure(Exception ex) {
        return ex instanceof OmnipodException && ((OmnipodException) ex).isCertainFailure();
    }

    public static class BolusCommandResult {
        private final CommandDeliveryStatus commandDeliveryStatus;
        private final SingleSubject<BolusDeliveryResult> deliveryResultSubject;

        BolusCommandResult(CommandDeliveryStatus commandDeliveryStatus, SingleSubject<BolusDeliveryResult> deliveryResultSubject) {
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

        BolusDeliveryResult(double unitsDelivered) {
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

    private static class ActiveBolusData {
        private final double units;
        private final DateTime startDate;
        private final CommandDeliveryStatus commandDeliveryStatus;
        private final SingleSubject<BolusDeliveryResult> bolusCompletionSubject;
        private final CompositeDisposable disposables;

        private ActiveBolusData(double units, DateTime startDate, CommandDeliveryStatus commandDeliveryStatus, SingleSubject<BolusDeliveryResult> bolusCompletionSubject, CompositeDisposable disposables) {
            this.units = units;
            this.startDate = startDate;
            this.commandDeliveryStatus = commandDeliveryStatus;
            this.bolusCompletionSubject = bolusCompletionSubject;
            this.disposables = disposables;
        }

        double getUnits() {
            return units;
        }

        DateTime getStartDate() {
            return startDate;
        }

        CommandDeliveryStatus getCommandDeliveryStatus() {
            return commandDeliveryStatus;
        }

        CompositeDisposable getDisposables() {
            return disposables;
        }

        SingleSubject<BolusDeliveryResult> getBolusCompletionSubject() {
            return bolusCompletionSubject;
        }

        double estimateUnitsDelivered() {
            long elapsedMillis = new Duration(startDate, DateTime.now()).getMillis();
            long totalDurationMillis = (long) (units / OmnipodConstants.POD_BOLUS_DELIVERY_RATE * 1000);
            double factor = (double) elapsedMillis / totalDurationMillis;
            double estimatedUnits = Math.min(1D, factor) * units;

            int roundingDivisor = (int) (1 / OmnipodConstants.POD_PULSE_SIZE);
            return (double) Math.round(estimatedUnits * roundingDivisor) / roundingDivisor;
        }
    }
}
