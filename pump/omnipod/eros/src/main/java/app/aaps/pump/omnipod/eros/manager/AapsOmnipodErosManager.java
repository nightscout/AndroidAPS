package app.aaps.pump.omnipod.eros.manager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import app.aaps.core.data.model.BS;
import app.aaps.core.data.model.TE;
import app.aaps.core.data.pump.defs.PumpType;
import app.aaps.core.data.time.T;
import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import app.aaps.core.interfaces.pump.PumpEnactResult;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.pump.defs.PumpTypeExtensionKt;
import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.Event;
import app.aaps.core.interfaces.rx.events.EventDismissNotification;
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress;
import app.aaps.core.interfaces.rx.events.EventRefreshOverview;
import app.aaps.core.interfaces.ui.UiInteraction;
import app.aaps.core.keys.interfaces.Preferences;
import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.defs.TempBasalPair;
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey;
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType;
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey;
import app.aaps.pump.omnipod.eros.R;
import app.aaps.pump.omnipod.eros.definition.PodHistoryEntryType;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoResponse;
import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.definition.BeepConfigType;
import app.aaps.pump.omnipod.eros.driver.definition.FaultEventCode;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalScheduleEntry;
import app.aaps.pump.omnipod.eros.driver.exception.ActivationTimeExceededException;
import app.aaps.pump.omnipod.eros.driver.exception.CommandFailedAfterChangingDeliveryStatusException;
import app.aaps.pump.omnipod.eros.driver.exception.CrcMismatchException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalActivationProgressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalDeliveryStatusException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageAddressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageSequenceNumberException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPacketTypeException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPodProgressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalResponseException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalVersionResponseTypeException;
import app.aaps.pump.omnipod.eros.driver.exception.MessageDecodingException;
import app.aaps.pump.omnipod.eros.driver.exception.NonceOutOfSyncException;
import app.aaps.pump.omnipod.eros.driver.exception.NonceResyncException;
import app.aaps.pump.omnipod.eros.driver.exception.NotEnoughDataException;
import app.aaps.pump.omnipod.eros.driver.exception.OmnipodException;
import app.aaps.pump.omnipod.eros.driver.exception.PodFaultException;
import app.aaps.pump.omnipod.eros.driver.exception.PodProgressStatusVerificationFailedException;
import app.aaps.pump.omnipod.eros.driver.exception.PodReturnedErrorResponseException;
import app.aaps.pump.omnipod.eros.driver.exception.PrecedingCommandFailedUncertainlyException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkInterruptedException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkTimeoutException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkUnexpectedException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkUnreachableException;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.driver.manager.OmnipodManager;
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged;
import app.aaps.pump.omnipod.eros.extensions.DetailedBolusInfoExtensionKt;
import app.aaps.pump.omnipod.eros.extensions.PumpStateExtensionKt;
import app.aaps.pump.omnipod.eros.history.ErosHistory;
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity;
import app.aaps.pump.omnipod.eros.keys.ErosBooleanPreferenceKey;
import app.aaps.pump.omnipod.eros.keys.ErosStringNonPreferenceKey;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil;
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil;
import io.reactivex.rxjava3.subjects.SingleSubject;

@Singleton
public class AapsOmnipodErosManager {

    private final ErosPodStateManager podStateManager;
    private final ErosHistory erosHistory;
    private final AapsOmnipodUtil aapsOmnipodUtil;
    private final AAPSLogger aapsLogger;
    private final RxBus rxBus;
    private final ResourceHelper rh;
    private final Preferences preferences;
    private final OmnipodManager delegate;
    private final OmnipodAlertUtil omnipodAlertUtil;
    private final PumpSync pumpSync;
    private final UiInteraction uiInteraction;
    private final Provider<PumpEnactResult> pumpEnactResultProvider;

    private boolean basalBeepsEnabled;
    private boolean bolusBeepsEnabled;
    private boolean smbBeepsEnabled;
    private boolean tbrBeepsEnabled;
    private boolean suspendDeliveryButtonEnabled;
    private boolean pulseLogButtonEnabled;
    private boolean timeChangeEventEnabled;
    private boolean notificationUncertainTbrSoundEnabled;
    private boolean notificationUncertainSmbSoundEnabled;
    private boolean notificationUncertainBolusSoundEnabled;
    private boolean automaticallyAcknowledgeAlertsEnabled;
    private boolean rileylinkStatsButtonEnabled;
    private boolean showRileyLinkBatteryLevel;
    private boolean batteryChangeLoggingEnabled;

    @Inject
    public AapsOmnipodErosManager(@NonNull OmnipodRileyLinkCommunicationManager communicationService,
                                  @NonNull ErosPodStateManager podStateManager,
                                  ErosHistory erosHistory,
                                  AapsOmnipodUtil aapsOmnipodUtil,
                                  AAPSLogger aapsLogger,
                                  AapsSchedulers aapsSchedulers,
                                  RxBus rxBus,
                                  Preferences preferences,
                                  ResourceHelper rh,
                                  OmnipodAlertUtil omnipodAlertUtil,
                                  PumpSync pumpSync,
                                  UiInteraction uiInteraction,
                                  Provider<PumpEnactResult> pumpEnactResultProvider
    ) {

        this.podStateManager = podStateManager;
        this.erosHistory = erosHistory;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.preferences = preferences;
        this.rh = rh;
        this.omnipodAlertUtil = omnipodAlertUtil;
        this.pumpSync = pumpSync;
        this.uiInteraction = uiInteraction;
        this.pumpEnactResultProvider = pumpEnactResultProvider;

        delegate = new OmnipodManager(aapsLogger, aapsSchedulers, communicationService, podStateManager);

        reloadSettings();
    }

    @NonNull public static BasalSchedule mapProfileToBasalSchedule(@NonNull Profile profile) {
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for (Profile.ProfileValue basalValue : basalValues) {
            entries.add(new BasalScheduleEntry(PumpTypeExtensionKt.determineCorrectBasalSize(PumpType.OMNIPOD_EROS, basalValue.getValue()),
                    Duration.standardSeconds(basalValue.getTimeAsSeconds())));
        }

        return new BasalSchedule(entries);
    }

    public void reloadSettings() {
        basalBeepsEnabled = preferences.get(OmnipodBooleanPreferenceKey.BasalBeepsEnabled);
        bolusBeepsEnabled = preferences.get(OmnipodBooleanPreferenceKey.BolusBeepsEnabled);
        smbBeepsEnabled = preferences.get(OmnipodBooleanPreferenceKey.SmbBeepsEnabled);
        tbrBeepsEnabled = preferences.get(OmnipodBooleanPreferenceKey.TbrBeepsEnabled);
        suspendDeliveryButtonEnabled = preferences.get(ErosBooleanPreferenceKey.ShowSuspendDeliveryButton);
        pulseLogButtonEnabled = preferences.get(ErosBooleanPreferenceKey.ShowPulseLogButton);
        rileylinkStatsButtonEnabled = preferences.get(ErosBooleanPreferenceKey.ShowRileyLinkStatsButton);
        showRileyLinkBatteryLevel = preferences.get(RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel);
        batteryChangeLoggingEnabled = preferences.get(ErosBooleanPreferenceKey.BatteryChangeLogging);
        timeChangeEventEnabled = preferences.get(ErosBooleanPreferenceKey.TimeChangeEnabled);
        notificationUncertainTbrSoundEnabled = preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainTbrNotification);
        notificationUncertainSmbSoundEnabled = preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainSmbNotification);
        notificationUncertainBolusSoundEnabled = preferences.get(OmnipodBooleanPreferenceKey.SoundUncertainBolusNotification);
        automaticallyAcknowledgeAlertsEnabled = preferences.get(OmnipodBooleanPreferenceKey.AutomaticallyAcknowledgeAlerts);
    }

    public PumpEnactResult initializePod() {
        PumpEnactResult result = pumpEnactResultProvider.get();
        try {
            Boolean res = executeCommand(delegate::pairAndPrime)
                    .blockingGet();

            result.success(res).enacted(res);

            if (!res) {
                result.comment(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_initialize_pod);
            }
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }

        addToHistory(System.currentTimeMillis(), PodHistoryEntryType.INITIALIZE_POD, result.getComment(), result.getSuccess());

        return result;
    }

    public PumpEnactResult insertCannula(Profile profile) {
        if (profile == null) {
            String comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_set_initial_basal_schedule_no_profile);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(comment);
        }

        PumpEnactResult result = pumpEnactResultProvider.get();

        try {
            BasalSchedule basalSchedule = mapProfileToBasalSchedule(profile);

            Boolean res = executeCommand(() -> delegate.insertCannula(basalSchedule, omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown(), omnipodAlertUtil.getLowReservoirAlertUnits())) //
                    .blockingGet();

            result.success(res).enacted(res);
            if (!res) {
                result.comment(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_insert_cannula);
            }
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }

        addToHistory(System.currentTimeMillis(), PodHistoryEntryType.INSERT_CANNULA, result.getComment(), result.getSuccess());

        if (result.getSuccess()) {
            pumpSync.connectNewPump(true);

            uploadCareportalEvent(System.currentTimeMillis() - 1000, TE.Type.INSULIN_CHANGE);
            uploadCareportalEvent(System.currentTimeMillis(), TE.Type.CANNULA_CHANGE);

            dismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED);

            cancelSuspendedFakeTbrIfExists();
        }

        return result;
    }

    @NonNull public PumpEnactResult configureAlerts(@NonNull List<AlertConfiguration> alertConfigurations) {
        try {
            executeCommand(() -> delegate.configureAlerts(alertConfigurations));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, alertConfigurations);
        return pumpEnactResultProvider.get().success(true).enacted(false);
    }

    @NonNull public PumpEnactResult playTestBeep(BeepConfigType beepType) {
        try {
            executeCommand(() -> delegate.playTestBeep(beepType));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.PLAY_TEST_BEEP, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.PLAY_TEST_BEEP, beepType);
        return pumpEnactResultProvider.get().success(true).enacted(false);
    }

    public PumpEnactResult getPodStatus() {
        StatusResponse statusResponse;

        try {
            statusResponse = executeCommand(delegate::getPodStatus);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.GET_POD_STATUS, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.GET_POD_STATUS, statusResponse);

        return pumpEnactResultProvider.get().success(true).enacted(false);
    }

    @NonNull public PumpEnactResult deactivatePod() {
        try {
            executeCommand(delegate::deactivatePod);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.DEACTIVATE_POD, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.DEACTIVATE_POD, null);
        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.OMNIPOD_POD_FAULT);

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    @NonNull public PumpEnactResult setBasalProfile(@Nullable Profile profile, boolean showNotifications) {
        if (profile == null) {
            String note = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_set_profile_empty_profile);
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, note, Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(note);
        }

        // #1963 return synthetic success if pre-activation
        // to allow profile switch prior to pod activation
        // otherwise a catch-22
        if (!podStateManager.getActivationProgress().isCompleted()) {
            // TODO: i18n string
            return pumpEnactResultProvider.get().success(true).enacted(false).comment("pre" +
                    "-activation basal change moot");
        }

        PodHistoryEntryType historyEntryType = podStateManager.isSuspended() ? PodHistoryEntryType.RESUME_DELIVERY : PodHistoryEntryType.SET_BASAL_SCHEDULE;

        try {
            BasalSchedule basalSchedule = mapProfileToBasalSchedule(profile);
            executeCommand(() -> delegate.setBasalSchedule(basalSchedule, isBasalBeepsEnabled()));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, getStringResource(R.string.omnipod_eros_error_set_basal_failed_delivery_suspended), Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(historyEntryType, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, getStringResource(R.string.omnipod_eros_error_set_basal_failed_delivery_might_be_suspended), Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(historyEntryType, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            if (showNotifications) {
                String note;
                if (OmnipodManager.isCertainFailure(ex)) {
                    note = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_set_basal_failed);
                } else {
                    note = getStringResource(R.string.omnipod_eros_error_set_basal_might_have_failed_delivery_might_be_suspended);
                }
                showNotification(Notification.FAILED_UPDATE_PROFILE, note, Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(historyEntryType, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }


        if (historyEntryType == PodHistoryEntryType.RESUME_DELIVERY) {
            cancelSuspendedFakeTbrIfExists();
        }

        addSuccessToHistory(historyEntryType, profile.getBasalValues());

        if (showNotifications) {
            showNotification(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, null);
        }

        dismissNotification(Notification.FAILED_UPDATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_POD_SUSPENDED);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    public PumpEnactResult discardPodState() {
        podStateManager.discardState();

        addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.DISCARD_POD, null);

        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.OMNIPOD_POD_FAULT);
        sendEvent(new EventOmnipodErosPumpValuesChanged());
        sendEvent(new EventRefreshOverview("Omnipod command: " + OmnipodCommandType.DISCARD_POD, false));

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    public PumpEnactResult bolus(DetailedBolusInfo detailedBolusInfo) {
        OmnipodManager.BolusCommandResult bolusCommandResult;

        boolean beepsEnabled = detailedBolusInfo.getBolusType() == BS.Type.SMB ? isSmbBeepsEnabled() : isBolusBeepsEnabled();

        Date bolusStarted;
        try {
            bolusCommandResult = executeCommand(() -> delegate.bolus(PumpTypeExtensionKt.determineCorrectBolusSize(PumpType.OMNIPOD_EROS, detailedBolusInfo.insulin), beepsEnabled, beepsEnabled,
                    detailedBolusInfo.getBolusType() == BS.Type.SMB ? null :
                            (estimatedUnitsDelivered, percentage) -> sendEvent(new EventOverviewBolusProgress(rh, estimatedUnitsDelivered, detailedBolusInfo.getId()))));

            bolusStarted = new Date();
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.SET_BOLUS, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        if (OmnipodManager.CommandDeliveryStatus.UNCERTAIN_FAILURE.equals(bolusCommandResult.getCommandDeliveryStatus())) {
            // For safety reasons, we treat this as a bolus that has successfully been delivered, in order to prevent insulin overdose
            if (detailedBolusInfo.getBolusType() == BS.Type.SMB) {
                showNotification(Notification.OMNIPOD_UNCERTAIN_SMB, getStringResource(R.string.omnipod_eros_error_bolus_failed_uncertain_smb, detailedBolusInfo.insulin), Notification.URGENT, isNotificationUncertainSmbSoundEnabled() ? app.aaps.core.ui.R.raw.boluserror : null);
            } else {
                showErrorDialog(getStringResource(R.string.omnipod_eros_error_bolus_failed_uncertain), isNotificationUncertainBolusSoundEnabled() ? app.aaps.core.ui.R.raw.boluserror : 0);
            }
        }

        detailedBolusInfo.timestamp = bolusStarted.getTime();
        detailedBolusInfo.setPumpType(PumpType.OMNIPOD_EROS);
        detailedBolusInfo.setPumpSerial(serialNumber());

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
            preferences.put(ErosStringNonPreferenceKey.ActiveBolus, DetailedBolusInfoExtensionKt.toJsonString(detailedBolusInfo));
            aapsLogger.debug(LTag.PUMP, "Stored active bolus to preferences for recovery");
        } catch (Exception ex) {
            aapsLogger.error(LTag.PUMP, "Failed to store active bolus to preferences", ex);
        }

        // Bolus is already updated in Pod state. If this was an SMB, it could be that
        // the user is looking at the Pod tab right now, so send an extra event
        // (this is normally done in OmnipodPumpPlugin)
        sendEvent(new EventOmnipodErosPumpValuesChanged());

        // Wait for the bolus to finish
        OmnipodManager.BolusDeliveryResult bolusDeliveryResult =
                bolusCommandResult.getDeliveryResultSubject().blockingGet();

        detailedBolusInfo.insulin = bolusDeliveryResult.getUnitsDelivered();

        addBolusToHistory(detailedBolusInfo);

        preferences.remove(ErosStringNonPreferenceKey.ActiveBolus);

        return pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(detailedBolusInfo.insulin);
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
                String comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_bolus_did_not_succeed);
                addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
                return pumpEnactResultProvider.get().success(true).enacted(false).comment(comment);
            }
        }

        String comment = "Unknown";
        for (int i = 1; delegate.hasActiveBolus(); i++) {
            aapsLogger.debug(LTag.PUMP, "Attempting to cancel bolus (#{})", i);

            try {
                executeCommand(() -> delegate.cancelBolus(isBolusBeepsEnabled()));
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus", i);
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return pumpEnactResultProvider.get().success(true).enacted(true);
            } catch (PodFaultException ex) {
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus (implicitly because of a Pod Fault)");
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return pumpEnactResultProvider.get().success(true).enacted(true);
            } catch (Exception ex) {
                aapsLogger.debug(LTag.PUMP, "Failed to cancel bolus", ex);
                comment = translateException(ex);
            }
        }

        addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
        return pumpEnactResultProvider.get().success(false).enacted(false).comment(comment);
    }

    @NonNull public PumpEnactResult setTemporaryBasal(@NonNull TempBasalPair tempBasalPair) {
        boolean beepsEnabled = isTbrBeepsEnabled();
        try {
            executeCommand(() -> delegate.setTemporaryBasal(PumpTypeExtensionKt.determineCorrectBasalSize(PumpType.OMNIPOD_EROS, tempBasalPair.getInsulinRate()),
                    Duration.standardMinutes(tempBasalPair.getDurationMinutes()), beepsEnabled, beepsEnabled));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);

            showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_eros_error_set_temp_basal_failed_old_tbr_might_be_cancelled), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? app.aaps.core.ui.R.raw.boluserror : null);

            splitActiveTbr(); // Split any active TBR so when we recover from the uncertain TBR status,we only cancel the part after the cancellation

            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            long pumpId = addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);

            if (!OmnipodManager.isCertainFailure(ex)) {
                showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_eros_error_set_temp_basal_failed_old_tbr_cancelled_new_might_have_failed), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? app.aaps.core.ui.R.raw.boluserror : null);

                // Assume that setting the temp basal succeeded here, because in case it didn't succeed,
                // The next StatusResponse that we receive will allow us to recover from the wrong state
                // as we can see that the delivery status doesn't actually show that a TBR is running
                // If we would assume that the TBR didn't succeed, we couldn't properly recover upon the next StatusResponse,
                // as we could only see that the Pod is running a TBR, but we don't know the rate and duration as
                // the Pod doesn't provide this information

                addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);
            }

            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, tempBasalPair);

        addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);

        sendEvent(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));

        return pumpEnactResultProvider.get()
                .duration(tempBasalPair.getDurationMinutes())
                .absolute(PumpTypeExtensionKt.determineCorrectBasalSize(PumpType.OMNIPOD_EROS, tempBasalPair.getInsulinRate()))
                .success(true).enacted(true);
    }

    public PumpEnactResult cancelTemporaryBasal() {
        try {
            executeCommand(() -> delegate.cancelTemporaryBasal(isTbrBeepsEnabled()));
        } catch (Exception ex) {
            if (OmnipodManager.isCertainFailure(ex)) {
                showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_eros_error_cancel_temp_basal_failed_uncertain), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? app.aaps.core.ui.R.raw.boluserror : null);
            } else {
                splitActiveTbr(); // Split any active TBR so when we recover from the uncertain TBR status,we only cancel the part after the cancellation
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, null);

        pumpSync.syncStopTemporaryBasalWithPumpId(
                System.currentTimeMillis(),
                pumpId,
                PumpType.OMNIPOD_EROS,
                serialNumber(),
                false
        );

        sendEvent(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    @NonNull public PumpEnactResult acknowledgeAlerts() {
        try {
            executeCommand(delegate::acknowledgeAlerts);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, null);
        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    public PumpEnactResult suspendDelivery() {
        try {
            executeCommand(() -> delegate.suspendDelivery(isBasalBeepsEnabled()));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, null);
        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.FAILED_UPDATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    // Updates the pods current time based on the device timezone and the pod's time zone
    public PumpEnactResult setTime(boolean showNotifications) {
        try {
            executeCommand(() -> delegate.setTime(isBasalBeepsEnabled()));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, getStringResource(R.string.omnipod_eros_error_set_time_failed_delivery_suspended), Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, getStringResource(R.string.omnipod_eros_error_set_time_failed_delivery_might_be_suspended), Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UPDATE_PROFILE, getStringResource(R.string.omnipod_eros_error_set_time_failed_delivery_might_be_suspended), Notification.URGENT, app.aaps.core.ui.R.raw.boluserror);
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.SET_TIME, null);

        dismissNotification(Notification.FAILED_UPDATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_POD_SUSPENDED);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return pumpEnactResultProvider.get().success(true).enacted(true);
    }

    public PodInfoRecentPulseLog readPulseLog() {
        PodInfoResponse response = executeCommand(() -> delegate.getPodInfo(PodInfoType.RECENT_PULSE_LOG));
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

    public boolean isRileylinkStatsButtonEnabled() {
        return rileylinkStatsButtonEnabled;
    }

    public boolean isShowRileyLinkBatteryLevel() {
        return showRileyLinkBatteryLevel;
    }

    public boolean isBatteryChangeLoggingEnabled() {
        return batteryChangeLoggingEnabled;
    }

    public boolean isTimeChangeEventEnabled() {
        return timeChangeEventEnabled;
    }

    public boolean isNotificationUncertainTbrSoundEnabled() {
        return notificationUncertainTbrSoundEnabled;
    }

    public boolean isNotificationUncertainSmbSoundEnabled() {
        return notificationUncertainSmbSoundEnabled;
    }

    public boolean isNotificationUncertainBolusSoundEnabled() {
        return notificationUncertainBolusSoundEnabled;
    }

    public boolean isAutomaticallyAcknowledgeAlertsEnabled() {
        return automaticallyAcknowledgeAlertsEnabled;
    }

    public void addBolusToHistory(DetailedBolusInfo originalDetailedBolusInfo) {
        DetailedBolusInfo detailedBolusInfo = originalDetailedBolusInfo.copy();

        detailedBolusInfo.setBolusTimestamp(detailedBolusInfo.timestamp);
        detailedBolusInfo.setPumpType(PumpType.OMNIPOD_EROS);
        detailedBolusInfo.setPumpSerial(serialNumber());
        detailedBolusInfo.setBolusPumpId(addSuccessToHistory(detailedBolusInfo.timestamp, PodHistoryEntryType.SET_BOLUS, detailedBolusInfo.insulin + ";" + detailedBolusInfo.carbs));

        if (detailedBolusInfo.carbs > 0 && detailedBolusInfo.getCarbsTimestamp() != null) {
            // split out a separate carbs record without a pumpId
            pumpSync.syncCarbsWithTimestamp(
                    detailedBolusInfo.getCarbsTimestamp(),
                    detailedBolusInfo.carbs,
                    null,
                    PumpType.USER,
                    serialNumber());

            // remove carbs from bolusInfo to not trigger any unwanted code paths in
            // TreatmentsPlugin.addToHistoryTreatment() method
            detailedBolusInfo.carbs = 0;
        }
        pumpSync.syncBolusWithPumpId(
                detailedBolusInfo.timestamp,
                detailedBolusInfo.insulin,
                detailedBolusInfo.getBolusType(),
                detailedBolusInfo.getBolusPumpId(),
                detailedBolusInfo.getPumpType(),
                serialNumber());

    }

    public synchronized void createSuspendedFakeTbrIfNotExists() {
        if (!hasSuspendedFakeTbr()) {
            aapsLogger.debug(LTag.PUMP, "Creating fake suspended TBR");

            long pumpId = addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL, null);

            pumpSync.syncTemporaryBasalWithPumpId(
                    System.currentTimeMillis(),
                    0.0,
                    OmnipodConstants.SERVICE_DURATION.getMillis(),
                    true,
                    PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND,
                    pumpId,
                    PumpType.OMNIPOD_EROS,
                    pumpSync.expectedPumpState().getSerialNumber()
            );
        }
    }

    public synchronized void cancelSuspendedFakeTbrIfExists() {
        if (hasSuspendedFakeTbr()) {
            aapsLogger.debug(LTag.PUMP, "Cancelling fake suspended TBR");
            long pumpId = addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_FAKE_SUSPENDED_TEMPORARY_BASAL, null);

            pumpSync.syncStopTemporaryBasalWithPumpId(
                    System.currentTimeMillis(),
                    pumpId,
                    PumpType.OMNIPOD_EROS,
                    serialNumber(),
                    false
            );
        }
    }

    public boolean hasSuspendedFakeTbr() {
        PumpSync.PumpState pumpState = pumpSync.expectedPumpState();
        if (pumpState.getTemporaryBasal() != null && pumpState.getTemporaryBasal().getPumpId() != null) {
            ErosHistoryRecordEntity historyRecord = erosHistory.findErosHistoryRecordByPumpId(pumpState.getTemporaryBasal().getPumpId());
            return historyRecord != null && PodHistoryEntryType.getByCode(historyRecord.getPodEntryTypeCode()).equals(PodHistoryEntryType.SET_FAKE_SUSPENDED_TEMPORARY_BASAL);
        }
        return false;
    }

    public void reportCancelledTbr() {
        reportCancelledTbr(System.currentTimeMillis());
    }

    public void reportCancelledTbr(long time) {
        aapsLogger.debug(LTag.PUMP, "Reporting cancelled TBR to AAPS");

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL_BY_DRIVER, null);

        pumpSync.syncStopTemporaryBasalWithPumpId(
                time,
                pumpId,
                PumpType.OMNIPOD_EROS,
                serialNumber(),
                false
        );

        sendEvent(new EventRefreshOverview("AapsOmnipodManager.reportCancelledTbr()", false));
    }

    public long addTbrSuccessToHistory(long requestTime, TempBasalPair tempBasalPair) {
        return addSuccessToHistory(requestTime, PodHistoryEntryType.SET_TEMPORARY_BASAL, tempBasalPair);
    }

    // Cancels current TBR and adds a new TBR for the remaining duration
    private void splitActiveTbr() {
        PumpSync.PumpState.TemporaryBasal previouslyRunningTempBasal = pumpSync.expectedPumpState().getTemporaryBasal();
        if (previouslyRunningTempBasal != null) {
            // Cancel the previously running TBR and start a NEW TBR here for the remaining duration,
            // so that we only cancel the remaining part when recovering from an uncertain failure in the cancellation
            int minutesRemaining = PumpStateExtensionKt.getPlannedRemainingMinutesRoundedUp(previouslyRunningTempBasal);

            if (minutesRemaining > 0) {
                reportCancelledTbr(System.currentTimeMillis() - 1000);

                TempBasalPair newTempBasalPair = new TempBasalPair(previouslyRunningTempBasal.getRate(), false, minutesRemaining);
                long pumpId = addSuccessToHistory(PodHistoryEntryType.SPLIT_TEMPORARY_BASAL, newTempBasalPair);

                pumpSync.syncTemporaryBasalWithPumpId(
                        System.currentTimeMillis(),
                        previouslyRunningTempBasal.getRate(),
                        minutesRemaining,
                        true,
                        PumpSync.TemporaryBasalType.NORMAL,
                        pumpId,
                        PumpType.OMNIPOD_EROS,
                        serialNumber()
                );
            }
        }
    }

    private void addTempBasalTreatment(long time, long pumpId, TempBasalPair tempBasalPair) {
        pumpSync.syncTemporaryBasalWithPumpId(
                time,
                tempBasalPair.getInsulinRate(),
                T.Companion.mins(tempBasalPair.getDurationMinutes()).msecs(),
                true,
                PumpSync.TemporaryBasalType.NORMAL,
                pumpId,
                PumpType.OMNIPOD_EROS,
                serialNumber()
        );
    }

    private long addSuccessToHistory(@NonNull PodHistoryEntryType entryType, Object data) {
        return addSuccessToHistory(System.currentTimeMillis(), entryType, data);
    }

    private long addSuccessToHistory(long requestTime, @NonNull PodHistoryEntryType entryType, Object
            data) {
        return addToHistory(requestTime, entryType, data, true);
    }

    private long addFailureToHistory(@NonNull PodHistoryEntryType entryType, Object data) {
        return addFailureToHistory(System.currentTimeMillis(), entryType, data);
    }

    private long addFailureToHistory(long requestTime, @NonNull PodHistoryEntryType entryType, Object
            data) {
        return addToHistory(requestTime, entryType, data, false);
    }

    private long addToHistory(long requestTime, PodHistoryEntryType entryType, Object data,
                              boolean success) {
        ErosHistoryRecordEntity erosHistoryRecordEntity = new ErosHistoryRecordEntity(requestTime, entryType.getCode());

        if (data != null) {
            if (data instanceof String) {
                erosHistoryRecordEntity.setData((String) data);
            } else {
                erosHistoryRecordEntity.setData(aapsOmnipodUtil.getGsonInstance().toJson(data));
            }
        }

        erosHistoryRecordEntity.setSuccess(success);
        erosHistoryRecordEntity.setPodSerial(podStateManager.hasPodState() ? String.valueOf(podStateManager.getAddress()) : "None");

        erosHistory.create(erosHistoryRecordEntity);
        return erosHistoryRecordEntity.getPumpId();
    }

    private void executeCommand(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            handleException(ex);
            throw ex;
        }
    }

    private <T> T executeCommand(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            handleException(ex);
            throw ex;
        }
    }

    private void handleException(Exception ex) {
        if (ex instanceof OmnipodException) {
            aapsLogger.error(LTag.PUMP, String.format("Caught OmnipodException[certainFailure=%s] from OmnipodManager", ((OmnipodException) ex).isCertainFailure()), ex);
            if (ex instanceof PodFaultException) {
                FaultEventCode faultEventCode = ((PodFaultException) ex).getDetailedStatus().getFaultEventCode();
                showPodFaultNotification(faultEventCode);
            }
        } else {
            aapsLogger.error(LTag.PUMP, "Caught an unexpected non-OmnipodException from OmnipodManager", ex);
        }
    }

    public String translateException(Throwable ex) {
        String comment;

        if (ex instanceof CrcMismatchException) {
            comment = getStringResource(R.string.omnipod_eros_error_crc_mismatch);
        } else if (ex instanceof IllegalPacketTypeException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_packet_type);
        } else if (ex instanceof IllegalPodProgressException || ex instanceof IllegalActivationProgressException ||
                ex instanceof IllegalDeliveryStatusException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_progress_state);
        } else if (ex instanceof PodProgressStatusVerificationFailedException) {
            comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_verify_activation_progress);
        } else if (ex instanceof IllegalVersionResponseTypeException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_response);
        } else if (ex instanceof IllegalResponseException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_response);
        } else if (ex instanceof IllegalMessageSequenceNumberException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_message_sequence_number);
        } else if (ex instanceof IllegalMessageAddressException) {
            comment = getStringResource(R.string.omnipod_eros_error_invalid_message_address);
        } else if (ex instanceof MessageDecodingException) {
            comment = getStringResource(R.string.omnipod_eros_error_message_decoding_failed);
        } else if (ex instanceof NonceOutOfSyncException) {
            comment = getStringResource(R.string.omnipod_eros_error_nonce_out_of_sync);
        } else if (ex instanceof NonceResyncException) {
            comment = getStringResource(R.string.omnipod_eros_error_nonce_resync_failed);
        } else if (ex instanceof NotEnoughDataException) {
            comment = getStringResource(R.string.omnipod_eros_error_not_enough_data);
        } else if (ex instanceof PodFaultException) {
            FaultEventCode faultEventCode = ((PodFaultException) ex).getDetailedStatus().getFaultEventCode();
            comment = createPodFaultErrorMessage(faultEventCode);
        } else if (ex instanceof ActivationTimeExceededException) {
            comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_pod_fault_activation_time_exceeded);
        } else if (ex instanceof PodReturnedErrorResponseException) {
            comment = getStringResource(R.string.omnipod_eros_error_pod_returned_error_response);
        } else if (ex instanceof RileyLinkUnreachableException) {
            comment = getStringResource(R.string.omnipod_eros_error_communication_failed_no_response_from_riley_link);
        } else if (ex instanceof RileyLinkInterruptedException) {
            comment = getStringResource(R.string.omnipod_eros_error_communication_failed_riley_link_interrupted);
        } else if (ex instanceof RileyLinkTimeoutException) {
            comment = getStringResource(R.string.omnipod_eros_error_communication_failed_no_response_from_pod);
        } else if (ex instanceof RileyLinkUnexpectedException) {
            Throwable cause = ex.getCause();
            comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_unexpected_exception, cause.getClass().getName(), cause.getMessage());
        } else {
            // Shouldn't be reachable
            comment = getStringResource(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_unexpected_exception, ex.getClass().getName(), ex.getMessage());
        }

        return comment;
    }

    private String createPodFaultErrorMessage(FaultEventCode faultEventCode) {
        return getStringResource(R.string.omnipod_eros_error_pod_fault,
                ByteUtil.INSTANCE.convertUnsignedByteToInt(faultEventCode.getValue()), faultEventCode.name());
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }

    private void showErrorDialog(@NonNull String message, Integer sound) {
        uiInteraction.runAlarm(message, rh.gs(app.aaps.core.ui.R.string.error), sound);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode) {
        showPodFaultNotification(faultEventCode, app.aaps.core.ui.R.raw.boluserror);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode, Integer sound) {
        showNotification(Notification.OMNIPOD_POD_FAULT, createPodFaultErrorMessage(faultEventCode), Notification.URGENT, sound);
    }

    private void showNotification(int id, String message, int urgency, Integer sound) {
        uiInteraction.addNotificationWithSound(id, message, urgency, sound);
    }

    private void dismissNotification(int id) {
        sendEvent(new EventDismissNotification(id));
    }

    private String getStringResource(int id, Object... args) {
        return rh.gs(id, args);
    }

    private void uploadCareportalEvent(long date, TE.Type event) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null, PumpType.OMNIPOD_EROS, Integer.toString(podStateManager.getAddress()));
    }

    public String serialNumber() {
        return podStateManager.isPodInitialized() ? String.valueOf(podStateManager.getAddress()) : "-";
    }
}
