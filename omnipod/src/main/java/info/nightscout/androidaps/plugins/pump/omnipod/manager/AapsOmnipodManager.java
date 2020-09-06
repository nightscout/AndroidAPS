package info.nightscout.androidaps.plugins.pump.omnipod.manager;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.OmnipodHistoryRecord;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.data.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommandFailedAfterChangingDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommandInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.DeliveryStatusVerificationFailedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageSequenceNumberException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.MessageDecodingException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceResyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.SetupActionResult;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodAlertUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.SingleSubject;

@Singleton
public class AapsOmnipodManager {

    private final PodStateManager podStateManager;
    private final AapsOmnipodUtil aapsOmnipodUtil;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final HasAndroidInjector injector;
    private final ActivePluginProvider activePlugin;
    private final SP sp;
    private final OmnipodManager delegate;
    private final DatabaseHelperInterface databaseHelper;
    private final OmnipodAlertUtil omnipodAlertUtil;

    private boolean basalBeepsEnabled;
    private boolean bolusBeepsEnabled;
    private boolean smbBeepsEnabled;
    private boolean tbrBeepsEnabled;
    private boolean suspendDeliveryButtonEnabled;
    private boolean pulseLogButtonEnabled;
    private boolean timeChangeEventEnabled;

    @Inject
    public AapsOmnipodManager(OmnipodRileyLinkCommunicationManager communicationService,
                              PodStateManager podStateManager,
                              AapsOmnipodUtil aapsOmnipodUtil,
                              AAPSLogger aapsLogger,
                              RxBusWrapper rxBus,
                              SP sp,
                              ResourceHelper resourceHelper,
                              HasAndroidInjector injector,
                              ActivePluginProvider activePlugin,
                              DatabaseHelperInterface databaseHelper,
                              OmnipodAlertUtil omnipodAlertUtil) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager can not be null");
        }
        this.podStateManager = podStateManager;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.injector = injector;
        this.activePlugin = activePlugin;
        this.databaseHelper = databaseHelper;
        this.sp = sp;
        this.omnipodAlertUtil = omnipodAlertUtil;

        delegate = new OmnipodManager(aapsLogger, sp, communicationService, podStateManager);

        reloadSettings();
    }

    public void reloadSettings() {
        basalBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.BASAL_BEEPS_ENABLED, true);
        bolusBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.BOLUS_BEEPS_ENABLED, true);
        smbBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.SMB_BEEPS_ENABLED, true);
        tbrBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.TBR_BEEPS_ENABLED, true);
        suspendDeliveryButtonEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.SUSPEND_DELIVERY_BUTTON_ENABLED, false);
        pulseLogButtonEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.PULSE_LOG_BUTTON_ENABLED, false);
        timeChangeEventEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.TIME_CHANGE_EVENT_ENABLED, true);
    }

    public PumpEnactResult pairAndPrime(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver) {
        if (podInitActionType != PodInitActionType.PAIR_AND_PRIME_WIZARD_STEP) {
            return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_illegal_init_action_type, podInitActionType.name()));
        }

        try {
            Disposable disposable = delegate.pairAndPrime().subscribe(res -> //
                    handleSetupActionResult(podInitActionType, podInitReceiver, res, System.currentTimeMillis(), null));

            return new PumpEnactResult(injector).success(true).enacted(true);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
            addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.PAIR_AND_PRIME, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult setInitialBasalScheduleAndInsertCannula(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile) {
        if (podInitActionType != PodInitActionType.FILL_CANNULA_SET_BASAL_PROFILE_WIZARD_STEP) {
            return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_illegal_init_action_type, podInitActionType.name()));
        }

        try {
            BasalSchedule basalSchedule;
            try {
                basalSchedule = mapProfileToBasalSchedule(profile);
            } catch (Exception ex) {
                throw new CommandInitializationException("Basal profile mapping failed", ex);
            }

            Disposable disposable = delegate.insertCannula(basalSchedule, omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown(), omnipodAlertUtil.getLowReservoirAlertUnits()).subscribe(res -> //
                    handleSetupActionResult(podInitActionType, podInitReceiver, res, System.currentTimeMillis(), profile));

            rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED));

            cancelSuspendedFakeTbrIfExists();

            return new PumpEnactResult(injector).success(true).enacted(true);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
            addFailureToHistory(PodHistoryEntryType.FILL_CANNULA_SET_BASAL_PROFILE, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult configureAlerts(List<AlertConfiguration> alertConfigurations) {
        try {
            StatusResponse statusResponse = delegate.configureAlerts(alertConfigurations);
            addSuccessToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, alertConfigurations);
            return new PumpEnactResult(injector).success(true).enacted(false);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult getPodStatus() {
        try {
            StatusResponse statusResponse = delegate.getPodStatus();
            addSuccessToHistory(PodHistoryEntryType.GET_POD_STATUS, statusResponse);
            return new PumpEnactResult(injector).success(true).enacted(false);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.GET_POD_STATUS, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        try {
            delegate.deactivatePod();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DEACTIVATE_POD_WIZARD_STEP, false, comment);
            addFailureToHistory(PodHistoryEntryType.DEACTIVATE_POD, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        addSuccessToHistory(PodHistoryEntryType.DEACTIVATE_POD, null);

        createSuspendedFakeTbrIfNotExists();

        podInitReceiver.returnInitTaskStatus(PodInitActionType.DEACTIVATE_POD_WIZARD_STEP, true, null);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult setBasalProfile(Profile profile) {
        PodHistoryEntryType historyEntryType = podStateManager.isSuspended() ? PodHistoryEntryType.RESUME_DELIVERY : PodHistoryEntryType.SET_BASAL_SCHEDULE;

        try {
            BasalSchedule basalSchedule;
            try {
                basalSchedule = mapProfileToBasalSchedule(profile);
            } catch (Exception ex) {
                throw new CommandInitializationException("Basal profile mapping failed", ex);
            }
            delegate.setBasalSchedule(basalSchedule, isBasalBeepsEnabled());

            if (historyEntryType == PodHistoryEntryType.RESUME_DELIVERY) {
                cancelSuspendedFakeTbrIfExists();
            }
            addSuccessToHistory(historyEntryType, profile.getBasalValues());
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            String comment = getStringResource(R.string.omnipod_error_set_basal_failed_delivery_suspended);
            showNotification(Notification.FAILED_UDPATE_PROFILE, comment, Notification.URGENT, R.raw.boluserror);
            addFailureToHistory(historyEntryType, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (DeliveryStatusVerificationFailedException ex) {
            String comment;
            if (ex.getExpectedStatus() == DeliveryStatus.SUSPENDED) {
                // Happened when suspending delivery before setting the new profile
                comment = getStringResource(R.string.omnipod_error_set_basal_failed_delivery_might_be_suspended);
            } else {
                // Happened when setting the new profile (after suspending delivery)
                comment = getStringResource(R.string.omnipod_error_set_basal_might_have_failed_delivery_might_be_suspended);
            }
            showNotification(Notification.FAILED_UDPATE_PROFILE, comment, Notification.URGENT, R.raw.boluserror);
            addFailureToHistory(historyEntryType, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            showNotification(Notification.FAILED_UDPATE_PROFILE, comment, Notification.URGENT, R.raw.boluserror);
            addFailureToHistory(historyEntryType, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED));
        showNotification(Notification.PROFILE_SET_OK,
                resourceHelper.gs(R.string.profile_set_ok),
                Notification.INFO, null);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult discardPodState() {
        podStateManager.discardState();

        addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.RESET_POD_STATE, null);

        createSuspendedFakeTbrIfNotExists();

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult bolus(DetailedBolusInfo detailedBolusInfo) {
        OmnipodManager.BolusCommandResult bolusCommandResult;

        boolean beepsEnabled = detailedBolusInfo.isSMB ? isSmbBeepsEnabled() : isBolusBeepsEnabled();

        Date bolusStarted;
        try {
            bolusCommandResult = delegate.bolus(PumpType.Insulet_Omnipod.determineCorrectBolusSize(detailedBolusInfo.insulin), beepsEnabled, beepsEnabled, detailedBolusInfo.isSMB ? null :
                    (estimatedUnitsDelivered, percentage) -> {
                        EventOverviewBolusProgress progressUpdateEvent = EventOverviewBolusProgress.INSTANCE;
                        progressUpdateEvent.setStatus(getStringResource(R.string.bolusdelivering, detailedBolusInfo.insulin));
                        progressUpdateEvent.setPercent(percentage);
                        sendEvent(progressUpdateEvent);
                    });

            bolusStarted = new Date();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.SET_BOLUS, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        if (OmnipodManager.CommandDeliveryStatus.UNCERTAIN_FAILURE.equals(bolusCommandResult.getCommandDeliveryStatus())) {
            // For safety reasons, we treat this as a bolus that has successfully been delivered, in order to prevent insulin overdose
            if (detailedBolusInfo.isSMB) {
                showNotification(getStringResource(R.string.omnipod_bolus_failed_uncertain_smb, detailedBolusInfo.insulin), Notification.URGENT, R.raw.boluserror);
            } else {
                showNotification(getStringResource(R.string.omnipod_bolus_failed_uncertain), Notification.URGENT, R.raw.boluserror);
            }
        }

        detailedBolusInfo.date = bolusStarted.getTime();
        detailedBolusInfo.source = Source.PUMP;

        // Store the current bolus for in case the app crashes, gets killed, the phone dies or whatever before the bolus finishes
        // If we have a stored value for the current bolus on startup, we'll create a Treatment for it
        // However this can potentially be hours later if for example your phone died and you can't charge it
        // FIXME !!!
        // The proper solution here would be to create a treatment right after the bolus started,
        // and update that treatment after the bolus has finished in case the actual units delivered don't match the requested bolus units
        // That way, the bolus would immediately be sent to NS so in case the phone dies you could still see the bolus
        // Unfortunately this doesn't work because
        //  a) when cancelling a bolus within a few seconds of starting it, after updating the Treatment,
        //     we get a new treatment event from NS containing the originally created treatment with the original insulin amount.
        //     This event is processed in TreatmentService.createTreatmentFromJsonIfNotExists().
        //     Opposed to what the name of this method suggests, it does createOrUpdate,
        //     overwriting the insulin delivered with the original value.
        //     So practically it seems impossible to update a Treatment when using NS
        //  b) we only send newly created treatments to NS, so the insulin amount in NS would never be updated
        //
        // I discussed this with the AAPS team but nobody seems to care so we're stuck with this ugly workaround for now
        try {
            ActiveBolus activeBolus = ActiveBolus.fromDetailedBolusInfo(detailedBolusInfo);
            sp.putString(OmnipodStorageKeys.Preferences.ACTIVE_BOLUS, aapsOmnipodUtil.getGsonInstance().toJson(activeBolus));
            aapsLogger.debug(LTag.PUMP, "Stored active bolus to SP for recovery");
        } catch (Exception ex) {
            aapsLogger.error(LTag.PUMP, "Failed to store active bolus to SP", ex);
        }

        // Bolus is already updated in Pod state. If this was an SMB, it could be that
        // the user is looking at the Pod tab right now, so send an extra event
        // (this is normally done in OmnipodPumpPlugin)
        sendEvent(new EventOmnipodPumpValuesChanged());

        // Wait for the bolus to finish
        OmnipodManager.BolusDeliveryResult bolusDeliveryResult =
                bolusCommandResult.getDeliveryResultSubject().blockingGet();

        detailedBolusInfo.insulin = bolusDeliveryResult.getUnitsDelivered();

        addBolusToHistory(detailedBolusInfo);

        sp.remove(OmnipodStorageKeys.Preferences.ACTIVE_BOLUS);

        return new PumpEnactResult(injector).success(true).enacted(true).carbsDelivered(detailedBolusInfo.carbs).bolusDelivered(detailedBolusInfo.insulin);
    }

    public PumpEnactResult cancelBolus() {
        SingleSubject<Boolean> bolusCommandExecutionSubject = delegate.getBolusCommandExecutionSubject();
        if (bolusCommandExecutionSubject != null) {
            // Wait until the bolus command has actually been executed before sending the cancel bolus command
            aapsLogger.debug(LTag.PUMP, "Cancel bolus was requested, but the bolus command is still being executed. Awaiting bolus command execution");
            boolean bolusCommandSuccessfullyExecuted = bolusCommandExecutionSubject.blockingGet();
            if (bolusCommandSuccessfullyExecuted) {
                aapsLogger.debug(LTag.PUMP, "Bolus command successfully executed. Proceeding bolus cancellation");
            } else {
                aapsLogger.debug(LTag.PUMP, "Not cancelling bolus: bolus command failed");
                String comment = getStringResource(R.string.omnipod_bolus_did_not_succeed);
                addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
                return new PumpEnactResult(injector).success(true).enacted(false).comment(comment);
            }
        }

        String comment = null;
        for (int i = 1; delegate.hasActiveBolus(); i++) {
            aapsLogger.debug(LTag.PUMP, "Attempting to cancel bolus (#{})", i);
            try {
                delegate.cancelBolus(isBolusBeepsEnabled());
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus", i);
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (PodFaultException ex) {
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus (implicitly because of a Pod Fault)");
                showPodFaultNotification(ex.getFaultEvent().getFaultEventCode());
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (Exception ex) {
                aapsLogger.debug(LTag.PUMP, "Failed to cancel bolus", ex);
                comment = handleAndTranslateException(ex);
            }
        }

        addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
        return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
    }

    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        boolean beepsEnabled = isTbrBeepsEnabled();
        try {
            delegate.setTemporaryBasal(PumpType.Insulet_Omnipod.determineCorrectBasalSize(tempBasalPair.getInsulinRate()), Duration.standardMinutes(tempBasalPair.getDurationMinutes()), beepsEnabled, beepsEnabled);
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            String comment = getStringResource(R.string.omnipod_cancelled_old_tbr_failed_to_set_new);
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, comment);
            showNotification(comment, Notification.NORMAL, null);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (DeliveryStatusVerificationFailedException ex) {
            String comment;
            if (ex.getExpectedStatus() == DeliveryStatus.TEMP_BASAL_RUNNING) {
                // Happened after cancelling the old TBR, when attempting to set new TBR

                comment = getStringResource(R.string.omnipod_error_set_temp_basal_failed_old_tbr_cancelled_new_might_have_failed);
                long pumpId = addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, comment);

                // Assume that setting the temp basal succeeded here, because in case it didn't succeed,
                // The next StatusResponse that we receive will allow us to recover from the wrong state
                // as we can see that the delivery status doesn't actually show that a TBR is running
                // If we would assume that the TBR didn't succeed, we couldn't properly recover upon the next StatusResponse,
                // as we could only see that the Pod is running a TBR, but we don't know the rate and duration as
                // the Pod doesn't provide this information
                addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);
            } else {
                // Happened when attempting to cancel the old TBR
                comment = getStringResource(R.string.omnipod_error_set_temp_basal_failed_old_tbr_might_be_cancelled);
                addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, comment);
            }

            showNotification(comment, Notification.URGENT, R.raw.boluserror);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, tempBasalPair);

        addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult cancelTemporaryBasal() {
        try {
            delegate.cancelTemporaryBasal(isTbrBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, null);

        TemporaryBasal tempBasal = new TemporaryBasal(injector) //
                .date(System.currentTimeMillis()) //
                .duration(0) //
                .pumpId(pumpId) //
                .source(Source.PUMP);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult acknowledgeAlerts() {
        try {
            delegate.acknowledgeAlerts();
            addSuccessToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult suspendDelivery() {
        try {
            delegate.suspendDelivery(isBasalBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        addSuccessToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, null);

        createSuspendedFakeTbrIfNotExists();

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    // Updates the pods current time based on the device timezone and the pod's time zone
    public PumpEnactResult setTime() {
        try {
            delegate.setTime(isBasalBeepsEnabled());
            addSuccessToHistory(PodHistoryEntryType.SET_TIME, null);
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            String comment = getStringResource(R.string.omnipod_error_set_time_failed_delivery_suspended);
            showNotification(comment, Notification.URGENT, R.raw.boluserror);
            addFailureToHistory(PodHistoryEntryType.SET_TIME, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (DeliveryStatusVerificationFailedException ex) {
            String comment = getStringResource(R.string.omnipod_error_set_time_failed_delivery_might_be_suspended);
            showNotification(comment, Notification.URGENT, R.raw.boluserror);
            addFailureToHistory(PodHistoryEntryType.SET_TIME, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(PodHistoryEntryType.SET_TIME, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PodInfoRecentPulseLog readPulseLog() {
        PodInfoResponse response = delegate.getPodInfo(PodInfoType.RECENT_PULSE_LOG);
        return (PodInfoRecentPulseLog) response.getPodInfo();
    }

    public OmnipodRileyLinkCommunicationManager getCommunicationService() {
        return delegate.getCommunicationService();
    }

    public DateTime getTime() {
        return delegate.getTime();
    }

    public boolean isBasalBeepsEnabled() {
        return basalBeepsEnabled;
    }

    public boolean isBolusBeepsEnabled() {
        return bolusBeepsEnabled;
    }

    public boolean isSmbBeepsEnabled() {
        return smbBeepsEnabled;
    }

    public boolean isTbrBeepsEnabled() {
        return tbrBeepsEnabled;
    }

    public boolean isSuspendDeliveryButtonEnabled() {
        return suspendDeliveryButtonEnabled;
    }

    public boolean isPulseLogButtonEnabled() {
        return pulseLogButtonEnabled;
    }

    public boolean isTimeChangeEventEnabled() {
        return timeChangeEventEnabled;
    }

    public void addBolusToHistory(DetailedBolusInfo originalDetailedBolusInfo) {
        DetailedBolusInfo detailedBolusInfo = originalDetailedBolusInfo.copy();

        long pumpId = addSuccessToHistory(detailedBolusInfo.date, PodHistoryEntryType.SET_BOLUS, detailedBolusInfo.insulin + ";" + detailedBolusInfo.carbs);
        detailedBolusInfo.pumpId = pumpId;

        if (detailedBolusInfo.carbs > 0 && detailedBolusInfo.carbTime > 0) {
            // split out a separate carbs record without a pumpId
            DetailedBolusInfo carbInfo = new DetailedBolusInfo();
            carbInfo.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60L * 1000L;
            carbInfo.carbs = detailedBolusInfo.carbs;
            carbInfo.source = Source.USER;
            activePlugin.getActiveTreatments().addToHistoryTreatment(carbInfo, false);

            // remove carbs from bolusInfo to not trigger any unwanted code paths in
            // TreatmentsPlugin.addToHistoryTreatment() method
            detailedBolusInfo.carbTime = 0;
            detailedBolusInfo.carbs = 0;
        }
        activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, false);
    }

    public synchronized void createSuspendedFakeTbrIfNotExists() {
        if (!hasSuspendedFakeTbr()) {
            aapsLogger.debug(LTag.PUMP, "Creating fake suspended TBR");

            long pumpId = addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL, null);

            TemporaryBasal temporaryBasal = new TemporaryBasal(injector) //
                    .date(System.currentTimeMillis()) //
                    .absolute(0.0) //
                    .duration((int) OmnipodConstants.SERVICE_DURATION.getStandardMinutes()) //
                    .source(Source.PUMP) //
                    .pumpId(pumpId);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(temporaryBasal);
        }
    }

    public synchronized void cancelSuspendedFakeTbrIfExists() {
        if (hasSuspendedFakeTbr()) {
            aapsLogger.debug(LTag.PUMP, "Cancelling fake suspended TBR");
            long pumpId = addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_FAKE_SUSPENDED_TEMPORARY_BASAL, null);

            TemporaryBasal temporaryBasal = new TemporaryBasal(injector) //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    .source(Source.PUMP) //
                    .pumpId(pumpId);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(temporaryBasal);
        }
    }

    public boolean hasSuspendedFakeTbr() {
        if (activePlugin.getActiveTreatments().isTempBasalInProgress()) {
            TemporaryBasal tempBasal = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
            OmnipodHistoryRecord historyRecord = databaseHelper.findOmnipodHistoryRecordByPumpId(tempBasal.pumpId);
            return historyRecord != null && PodHistoryEntryType.getByCode(historyRecord.getPodEntryTypeCode()).equals(PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL);
        }
        return false;
    }

    public void reportCancelledTbr() {
        aapsLogger.debug(LTag.PUMP, "Reporting cancelled TBR to AAPS");

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL_BY_DRIVER, null);

        TemporaryBasal temporaryBasal = new TemporaryBasal(injector) //
                .date(System.currentTimeMillis()) //
                .duration(0) //
                .source(Source.PUMP) //
                .pumpId(pumpId);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(temporaryBasal);
    }

    private void addTempBasalTreatment(long time, long pumpId, TempBasalPair tempBasalPair) {
        TemporaryBasal tempStart = new TemporaryBasal(injector) //
                .date(time) //
                .duration(tempBasalPair.getDurationMinutes()) //
                .absolute(tempBasalPair.getInsulinRate()) //
                .pumpId(pumpId) //
                .source(Source.PUMP);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);
    }

    private long addSuccessToHistory(PodHistoryEntryType entryType, Object data) {
        return addSuccessToHistory(System.currentTimeMillis(), entryType, data);
    }

    private long addSuccessToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        return addToHistory(requestTime, entryType, data, true);
    }

    private long addFailureToHistory(PodHistoryEntryType entryType, Object data) {
        return addFailureToHistory(System.currentTimeMillis(), entryType, data);
    }

    private long addFailureToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        return addToHistory(requestTime, entryType, data, false);
    }

    private long addToHistory(long requestTime, PodHistoryEntryType entryType, Object data, boolean success) {
        OmnipodHistoryRecord omnipodHistoryRecord = new OmnipodHistoryRecord(requestTime, entryType.getCode());

        if (data != null) {
            if (data instanceof String) {
                omnipodHistoryRecord.setData((String) data);
            } else {
                omnipodHistoryRecord.setData(aapsOmnipodUtil.getGsonInstance().toJson(data));
            }
        }

        omnipodHistoryRecord.setSuccess(success);
        omnipodHistoryRecord.setPodSerial(podStateManager.hasPodState() ? String.valueOf(podStateManager.getAddress()) : "None");

        databaseHelper.createOrUpdate(omnipodHistoryRecord);

        return omnipodHistoryRecord.getPumpId();
    }

    private void handleSetupActionResult(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, SetupActionResult res, long time, Profile profile) {
        String comment = null;
        switch (res.getResultType()) {
            case FAILURE: {
                aapsLogger.error(LTag.PUMP, "Setup action failed: illegal setup progress: {}", res.getPodProgressStatus());
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state, res.getPodProgressStatus());
            }
            break;
            case VERIFICATION_FAILURE: {
                aapsLogger.error(LTag.PUMP, "Setup action verification failed: caught exception", res.getException());
                comment = getStringResource(R.string.omnipod_driver_error_setup_action_verification_failed);
            }
            break;
        }

        if (podInitActionType == PodInitActionType.PAIR_AND_PRIME_WIZARD_STEP) {
            addToHistory(time, PodHistoryEntryType.PAIR_AND_PRIME, comment, res.getResultType().isSuccess());
        } else {
            addToHistory(time, PodHistoryEntryType.FILL_CANNULA_SET_BASAL_PROFILE, res.getResultType().isSuccess() ? profile.getBasalValues() : comment, res.getResultType().isSuccess());
        }

        podInitReceiver.returnInitTaskStatus(podInitActionType, res.getResultType().isSuccess(), comment);
    }

    private String handleAndTranslateException(Exception ex) {
        String comment;

        if (ex instanceof OmnipodException) {
            if (ex instanceof ActionInitializationException || ex instanceof CommandInitializationException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_parameters);
            } else if (ex instanceof CommunicationException) {
                if (((CommunicationException) ex).getType() == CommunicationException.Type.TIMEOUT) {
                    comment = getStringResource(R.string.omnipod_driver_error_communication_failed_timeout);
                } else {
                    comment = getStringResource(R.string.omnipod_driver_error_communication_failed_unexpected_exception);
                }
            } else if (ex instanceof CrcMismatchException) {
                comment = getStringResource(R.string.omnipod_driver_error_crc_mismatch);
            } else if (ex instanceof IllegalPacketTypeException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_packet_type);
            } else if (ex instanceof IllegalPodProgressException || ex instanceof IllegalDeliveryStatusException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state);
            } else if (ex instanceof IllegalVersionResponseTypeException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_response);
            } else if (ex instanceof IllegalResponseException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_response);
            } else if (ex instanceof IllegalMessageSequenceNumberException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_message_sequence_number);
            } else if (ex instanceof IllegalMessageAddressException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_message_address);
            } else if (ex instanceof MessageDecodingException) {
                comment = getStringResource(R.string.omnipod_driver_error_message_decoding_failed);
            } else if (ex instanceof NonceOutOfSyncException) {
                comment = getStringResource(R.string.omnipod_driver_error_nonce_out_of_sync);
            } else if (ex instanceof NonceResyncException) {
                comment = getStringResource(R.string.omnipod_driver_error_nonce_resync_failed);
            } else if (ex instanceof NotEnoughDataException) {
                comment = getStringResource(R.string.omnipod_driver_error_not_enough_data);
            } else if (ex instanceof PodFaultException) {
                FaultEventCode faultEventCode = ((PodFaultException) ex).getFaultEvent().getFaultEventCode();
                showPodFaultNotification(faultEventCode);
                comment = createPodFaultErrorMessage(faultEventCode);
            } else if (ex instanceof PodReturnedErrorResponseException) {
                comment = getStringResource(R.string.omnipod_driver_error_pod_returned_error_response);
            } else {
                // Shouldn't be reachable
                comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            }
            aapsLogger.error(LTag.PUMP, String.format("Caught OmnipodException[certainFailure=%s] from OmnipodManager (user-friendly error message: %s)", ((OmnipodException) ex).isCertainFailure(), comment), ex);
        } else {
            comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            aapsLogger.error(LTag.PUMP, String.format("Caught unexpected exception type[certainFailure=false] from OmnipodManager (user-friendly error message: %s)", comment), ex);
        }

        return comment;
    }

    private String createPodFaultErrorMessage(FaultEventCode faultEventCode) {
        String comment;
        comment = getStringResource(R.string.omnipod_driver_error_pod_fault,
                ByteUtil.convertUnsignedByteToInt(faultEventCode.getValue()), faultEventCode.name());
        return comment;
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode) {
        showPodFaultNotification(faultEventCode, R.raw.boluserror);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode, Integer sound) {
        showNotification(createPodFaultErrorMessage(faultEventCode), Notification.URGENT, sound);
    }

    private void showNotification(String message, int urgency, Integer sound) {
        showNotification(Notification.OMNIPOD_PUMP_ALARM, message, urgency, sound);
    }

    private void showNotification(int id, String message, int urgency, Integer sound) {
        Notification notification = new Notification( //
                id, //
                message, //
                urgency);
        if (sound != null) {
            notification.soundId = sound;
        }
        sendEvent(new EventNewNotification(notification));
    }

    private String getStringResource(int id, Object... args) {
        return resourceHelper.gs(id, args);
    }

    public static BasalSchedule mapProfileToBasalSchedule(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile can not be null");
        }
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        if (basalValues == null) {
            throw new IllegalArgumentException("Basal values can not be null");
        }
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for (Profile.ProfileValue basalValue : basalValues) {
            entries.add(new BasalScheduleEntry(PumpType.Insulet_Omnipod.determineCorrectBasalSize(basalValue.value),
                    Duration.standardSeconds(basalValue.timeAsSeconds)));
        }

        return new BasalSchedule(entries);
    }
}
