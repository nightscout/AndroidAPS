package info.nightscout.androidaps.plugins.pump.omnipod.manager;

import android.content.Context;
import android.content.Intent;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.OmnipodHistoryRecord;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.data.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActivationTimeExceededException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommandFailedAfterChangingDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalActivationProgressException;
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
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodProgressStatusVerificationFailedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PrecedingCommandFailedUncertainlyException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkInterruptedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkTimeoutException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkUnexpectedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkUnreachableException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodAlertUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
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
    private final NSUpload nsUpload;
    private final ProfileFunction profileFunction;
    private final Context context;

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
                              OmnipodAlertUtil omnipodAlertUtil,
                              NSUpload nsUpload,
                              ProfileFunction profileFunction,
                              Context context) {

        this.podStateManager = podStateManager;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.sp = sp;
        this.resourceHelper = resourceHelper;
        this.injector = injector;
        this.activePlugin = activePlugin;
        this.databaseHelper = databaseHelper;
        this.omnipodAlertUtil = omnipodAlertUtil;
        this.nsUpload = nsUpload;
        this.profileFunction = profileFunction;
        this.context = context;

        delegate = new OmnipodManager(aapsLogger, communicationService, podStateManager);

        reloadSettings();
    }

    public void reloadSettings() {
        basalBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.BASAL_BEEPS_ENABLED, true);
        bolusBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.BOLUS_BEEPS_ENABLED, true);
        smbBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.SMB_BEEPS_ENABLED, true);
        tbrBeepsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.TBR_BEEPS_ENABLED, false);
        suspendDeliveryButtonEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.SUSPEND_DELIVERY_BUTTON_ENABLED, false);
        pulseLogButtonEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.PULSE_LOG_BUTTON_ENABLED, false);
        rileylinkStatsButtonEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.RILEY_LINK_STATS_BUTTON_ENABLED, false);
        showRileyLinkBatteryLevel = sp.getBoolean(OmnipodStorageKeys.Preferences.SHOW_RILEY_LINK_BATTERY_LEVEL, false);
        batteryChangeLoggingEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.BATTERY_CHANGE_LOGGING_ENABLED, false);
        timeChangeEventEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.TIME_CHANGE_EVENT_ENABLED, true);
        notificationUncertainTbrSoundEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_TBR_SOUND_ENABLED, false);
        notificationUncertainSmbSoundEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_SMB_SOUND_ENABLED, true);
        notificationUncertainBolusSoundEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_BOLUS_SOUND_ENABLED, true);
        automaticallyAcknowledgeAlertsEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.AUTOMATICALLY_ACKNOWLEDGE_ALERTS_ENABLED, false);
    }

    public PumpEnactResult initializePod() {
        PumpEnactResult result = new PumpEnactResult(injector);
        try {
            Boolean res = executeCommand(delegate::pairAndPrime)
                    .blockingGet();

            result.success(res).enacted(res);

            if (!res) {
                result.comment(R.string.omnipod_error_failed_to_initialize_pod);
            }
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }

        addToHistory(System.currentTimeMillis(), PodHistoryEntryType.INITIALIZE_POD, result.comment, result.success);

        return result;
    }

    public PumpEnactResult insertCannula(Profile profile) {
        if (profile == null) {
            String comment = getStringResource(R.string.omnipod_error_set_initial_basal_schedule_no_profile);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        PumpEnactResult result = new PumpEnactResult(injector);

        try {
            BasalSchedule basalSchedule = mapProfileToBasalSchedule(profile);

            Boolean res = executeCommand(() -> delegate.insertCannula(basalSchedule, omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown(), omnipodAlertUtil.getLowReservoirAlertUnits())) //
                    .blockingGet();

            result.success(res).enacted(res);
            if (!res) {
                result.comment(R.string.omnipod_error_failed_to_insert_cannula);
            }
        } catch (Exception ex) {
            result.success(false).enacted(false).comment(translateException(ex));
        }

        addToHistory(System.currentTimeMillis(), PodHistoryEntryType.INSERT_CANNULA, result.comment, result.success);

        if (result.success) {
            uploadCareportalEvent(System.currentTimeMillis() - 1000, CareportalEvent.INSULINCHANGE);
            uploadCareportalEvent(System.currentTimeMillis(), CareportalEvent.SITECHANGE);

            dismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED);

            cancelSuspendedFakeTbrIfExists();
        }

        return result;
    }

    public PumpEnactResult configureAlerts(List<AlertConfiguration> alertConfigurations) {
        try {
            executeCommand(() -> delegate.configureAlerts(alertConfigurations));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.CONFIGURE_ALERTS, alertConfigurations);
        return new PumpEnactResult(injector).success(true).enacted(false);
    }

    public PumpEnactResult playTestBeep(BeepConfigType beepType) {
        try {
            executeCommand(() -> delegate.playTestBeep(beepType));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.PLAY_TEST_BEEP, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.PLAY_TEST_BEEP, beepType);
        return new PumpEnactResult(injector).success(true).enacted(false);
    }


    public PumpEnactResult getPodStatus() {
        StatusResponse statusResponse;

        try {
            statusResponse = executeCommand(delegate::getPodStatus);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.GET_POD_STATUS, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.GET_POD_STATUS, statusResponse);

        return new PumpEnactResult(injector).success(true).enacted(false);
    }

    public PumpEnactResult deactivatePod() {
        try {
            executeCommand(delegate::deactivatePod);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.DEACTIVATE_POD, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.DEACTIVATE_POD, null);
        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.OMNIPOD_POD_FAULT);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult setBasalProfile(Profile profile, boolean showNotifications) {
        if (profile == null) {
            String note = getStringResource(R.string.omnipod_error_failed_to_set_profile_empty_profile);
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, note, Notification.URGENT, R.raw.boluserror);
            }
            return new PumpEnactResult(injector).success(false).enacted(false).comment(note);
        }

        PodHistoryEntryType historyEntryType = podStateManager.isSuspended() ? PodHistoryEntryType.RESUME_DELIVERY : PodHistoryEntryType.SET_BASAL_SCHEDULE;

        try {
            BasalSchedule basalSchedule = mapProfileToBasalSchedule(profile);
            executeCommand(() -> delegate.setBasalSchedule(basalSchedule, isBasalBeepsEnabled()));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, getStringResource(R.string.omnipod_error_set_basal_failed_delivery_suspended), Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(historyEntryType, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, getStringResource(R.string.omnipod_error_set_basal_failed_delivery_might_be_suspended), Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(historyEntryType, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            if (showNotifications) {
                String note;
                if (OmnipodManager.isCertainFailure(ex)) {
                    note = getStringResource(R.string.omnipod_error_set_basal_failed);
                } else {
                    note = getStringResource(R.string.omnipod_error_set_basal_might_have_failed_delivery_might_be_suspended);
                }
                showNotification(Notification.FAILED_UDPATE_PROFILE, note, Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(historyEntryType, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }


        if (historyEntryType == PodHistoryEntryType.RESUME_DELIVERY) {
            cancelSuspendedFakeTbrIfExists();
        }

        addSuccessToHistory(historyEntryType, profile.getBasalValues());

        if (showNotifications) {
            showNotification(Notification.PROFILE_SET_OK, resourceHelper.gs(R.string.profile_set_ok), Notification.INFO, null);
        }

        dismissNotification(Notification.FAILED_UDPATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_POD_SUSPENDED);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult discardPodState() {
        podStateManager.discardState();

        addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.DISCARD_POD, null);

        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.OMNIPOD_POD_FAULT);
        sendEvent(new EventOmnipodPumpValuesChanged());
        sendEvent(new EventRefreshOverview("Omnipod command: " + OmnipodCommandType.DISCARD_POD, false));

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult bolus(DetailedBolusInfo detailedBolusInfo) {
        OmnipodManager.BolusCommandResult bolusCommandResult;

        boolean beepsEnabled = detailedBolusInfo.isSMB ? isSmbBeepsEnabled() : isBolusBeepsEnabled();

        Date bolusStarted;
        try {
            bolusCommandResult = executeCommand(() -> delegate.bolus(PumpType.Insulet_Omnipod.determineCorrectBolusSize(detailedBolusInfo.insulin), beepsEnabled, beepsEnabled, detailedBolusInfo.isSMB ? null :
                    (estimatedUnitsDelivered, percentage) -> {
                        EventOverviewBolusProgress progressUpdateEvent = EventOverviewBolusProgress.INSTANCE;
                        progressUpdateEvent.setStatus(getStringResource(R.string.bolusdelivering, detailedBolusInfo.insulin));
                        progressUpdateEvent.setPercent(percentage);
                        sendEvent(progressUpdateEvent);
                    }));

            bolusStarted = new Date();
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.SET_BOLUS, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        if (OmnipodManager.CommandDeliveryStatus.UNCERTAIN_FAILURE.equals(bolusCommandResult.getCommandDeliveryStatus())) {
            // For safety reasons, we treat this as a bolus that has successfully been delivered, in order to prevent insulin overdose
            if (detailedBolusInfo.isSMB) {
                showNotification(Notification.OMNIPOD_UNCERTAIN_SMB, getStringResource(R.string.omnipod_error_bolus_failed_uncertain_smb, detailedBolusInfo.insulin), Notification.URGENT, isNotificationUncertainSmbSoundEnabled() ? R.raw.boluserror : null);
            } else {
                showErrorDialog(getStringResource(R.string.omnipod_error_bolus_failed_uncertain), isNotificationUncertainBolusSoundEnabled() ? R.raw.boluserror : null);
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
                String comment = getStringResource(R.string.omnipod_error_bolus_did_not_succeed);
                addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
                return new PumpEnactResult(injector).success(true).enacted(false).comment(comment);
            }
        }

        String comment = null;
        for (int i = 1; delegate.hasActiveBolus(); i++) {
            aapsLogger.debug(LTag.PUMP, "Attempting to cancel bolus (#{})", i);

            try {
                executeCommand(() -> delegate.cancelBolus(isBolusBeepsEnabled()));
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus", i);
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (PodFaultException ex) {
                aapsLogger.debug(LTag.PUMP, "Successfully cancelled bolus (implicitly because of a Pod Fault)");
                addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (Exception ex) {
                aapsLogger.debug(LTag.PUMP, "Failed to cancel bolus", ex);
                comment = translateException(ex);
            }
        }

        addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.CANCEL_BOLUS, comment);
        return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
    }

    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        boolean beepsEnabled = isTbrBeepsEnabled();
        try {
            executeCommand(() -> delegate.setTemporaryBasal(PumpType.Insulet_Omnipod.determineCorrectBasalSize(tempBasalPair.getInsulinRate()), Duration.standardMinutes(tempBasalPair.getDurationMinutes()), beepsEnabled, beepsEnabled));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);

            showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_error_set_temp_basal_failed_old_tbr_might_be_cancelled), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? R.raw.boluserror : null);

            splitActiveTbr(); // Split any active TBR so when we recover from the uncertain TBR status,we only cancel the part after the cancellation

            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            long pumpId = addFailureToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, errorMessage);

            if (!OmnipodManager.isCertainFailure(ex)) {
                showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_error_set_temp_basal_failed_old_tbr_cancelled_new_might_have_failed), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? R.raw.boluserror : null);

                // Assume that setting the temp basal succeeded here, because in case it didn't succeed,
                // The next StatusResponse that we receive will allow us to recover from the wrong state
                // as we can see that the delivery status doesn't actually show that a TBR is running
                // If we would assume that the TBR didn't succeed, we couldn't properly recover upon the next StatusResponse,
                // as we could only see that the Pod is running a TBR, but we don't know the rate and duration as
                // the Pod doesn't provide this information

                addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);
            }

            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.SET_TEMPORARY_BASAL, tempBasalPair);

        addTempBasalTreatment(System.currentTimeMillis(), pumpId, tempBasalPair);

        sendEvent(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));

        return new PumpEnactResult(injector)
                .duration(tempBasalPair.getDurationMinutes())
                .absolute(PumpType.Insulet_Omnipod.determineCorrectBasalSize(tempBasalPair.getInsulinRate()))
                .success(true).enacted(true);
    }

    public PumpEnactResult cancelTemporaryBasal() {
        try {
            executeCommand(() -> delegate.cancelTemporaryBasal(isTbrBeepsEnabled()));
        } catch (Exception ex) {
            if (OmnipodManager.isCertainFailure(ex)) {
                showNotification(Notification.OMNIPOD_TBR_ALERTS, getStringResource(R.string.omnipod_error_cancel_temp_basal_failed_uncertain), Notification.URGENT, isNotificationUncertainTbrSoundEnabled() ? R.raw.boluserror : null);
            } else {
                splitActiveTbr(); // Split any active TBR so when we recover from the uncertain TBR status,we only cancel the part after the cancellation
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL, null);

        TemporaryBasal tempBasal = new TemporaryBasal(injector) //
                .date(System.currentTimeMillis()) //
                .duration(0) //
                .pumpId(pumpId) //
                .source(Source.PUMP);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);

        sendEvent(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult acknowledgeAlerts() {
        try {
            executeCommand(delegate::acknowledgeAlerts);
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.ACKNOWLEDGE_ALERTS, null);
        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult suspendDelivery() {
        try {
            executeCommand(() -> delegate.suspendDelivery(isBasalBeepsEnabled()));
        } catch (Exception ex) {
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.SUSPEND_DELIVERY, null);
        createSuspendedFakeTbrIfNotExists();

        dismissNotification(Notification.FAILED_UDPATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    // Updates the pods current time based on the device timezone and the pod's time zone
    public PumpEnactResult setTime(boolean showNotifications) {
        try {
            executeCommand(() -> delegate.setTime(isBasalBeepsEnabled()));
        } catch (CommandFailedAfterChangingDeliveryStatusException ex) {
            createSuspendedFakeTbrIfNotExists();
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, getStringResource(R.string.omnipod_error_set_time_failed_delivery_suspended), Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (PrecedingCommandFailedUncertainlyException ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, getStringResource(R.string.omnipod_error_set_time_failed_delivery_might_be_suspended), Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex.getCause());
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        } catch (Exception ex) {
            if (showNotifications) {
                showNotification(Notification.FAILED_UDPATE_PROFILE, getStringResource(R.string.omnipod_error_set_time_failed_delivery_might_be_suspended), Notification.URGENT, R.raw.boluserror);
            }
            String errorMessage = translateException(ex);
            addFailureToHistory(PodHistoryEntryType.SET_TIME, errorMessage);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(errorMessage);
        }

        addSuccessToHistory(PodHistoryEntryType.SET_TIME, null);

        dismissNotification(Notification.FAILED_UDPATE_PROFILE);
        dismissNotification(Notification.OMNIPOD_POD_SUSPENDED);
        dismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC);

        return new PumpEnactResult(injector).success(true).enacted(true);
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
        reportCancelledTbr(System.currentTimeMillis());
    }

    public void reportCancelledTbr(long time) {
        aapsLogger.debug(LTag.PUMP, "Reporting cancelled TBR to AAPS");

        long pumpId = addSuccessToHistory(PodHistoryEntryType.CANCEL_TEMPORARY_BASAL_BY_DRIVER, null);

        TemporaryBasal temporaryBasal = new TemporaryBasal(injector) //
                .date(time) //
                .duration(0) //
                .source(Source.PUMP) //
                .pumpId(pumpId);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(temporaryBasal);

        sendEvent(new EventRefreshOverview("AapsOmnipodManager.reportCancelledTbr()", false));
    }

    public long addTbrSuccessToHistory(long requestTime, TempBasalPair tempBasalPair) {
        return addSuccessToHistory(requestTime, PodHistoryEntryType.SET_TEMPORARY_BASAL, tempBasalPair);
    }

    // Cancels current TBR and adds a new TBR for the remaining duration
    private void splitActiveTbr() {
        TemporaryBasal previouslyRunningTempBasal = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
        if (previouslyRunningTempBasal != null) {
            // Cancel the previously running TBR and start a NEW TBR here for the remaining duration,
            // so that we only cancel the remaining part when recovering from an uncertain failure in the cancellation
            int minutesRemaining = previouslyRunningTempBasal.getPlannedRemainingMinutesRoundedUp();

            if (minutesRemaining > 0) {
                reportCancelledTbr(System.currentTimeMillis() - 1000);

                TempBasalPair newTempBasalPair = new TempBasalPair(previouslyRunningTempBasal.absoluteRate, false, minutesRemaining);
                long pumpId = addSuccessToHistory(PodHistoryEntryType.SPLIT_TEMPORARY_BASAL, newTempBasalPair);

                TemporaryBasal tempBasal = new TemporaryBasal(injector) //
                        .date(System.currentTimeMillis()) //
                        .absolute(previouslyRunningTempBasal.absoluteRate)
                        .duration(minutesRemaining) //
                        .pumpId(pumpId) //
                        .source(Source.PUMP);

                activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);
            }
        }
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

    private long addSuccessToHistory(long requestTime, PodHistoryEntryType entryType, Object
            data) {
        return addToHistory(requestTime, entryType, data, true);
    }

    private long addFailureToHistory(PodHistoryEntryType entryType, Object data) {
        return addFailureToHistory(System.currentTimeMillis(), entryType, data);
    }

    private long addFailureToHistory(long requestTime, PodHistoryEntryType entryType, Object
            data) {
        return addToHistory(requestTime, entryType, data, false);
    }

    private long addToHistory(long requestTime, PodHistoryEntryType entryType, Object data,
                              boolean success) {
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
            comment = getStringResource(R.string.omnipod_error_crc_mismatch);
        } else if (ex instanceof IllegalPacketTypeException) {
            comment = getStringResource(R.string.omnipod_error_invalid_packet_type);
        } else if (ex instanceof IllegalPodProgressException || ex instanceof IllegalActivationProgressException ||
                ex instanceof IllegalDeliveryStatusException) {
            comment = getStringResource(R.string.omnipod_error_invalid_progress_state);
        } else if (ex instanceof PodProgressStatusVerificationFailedException) {
            comment = getStringResource(R.string.omnipod_error_failed_to_verify_activation_progress);
        } else if (ex instanceof IllegalVersionResponseTypeException) {
            comment = getStringResource(R.string.omnipod_error_invalid_response);
        } else if (ex instanceof IllegalResponseException) {
            comment = getStringResource(R.string.omnipod_error_invalid_response);
        } else if (ex instanceof IllegalMessageSequenceNumberException) {
            comment = getStringResource(R.string.omnipod_error_invalid_message_sequence_number);
        } else if (ex instanceof IllegalMessageAddressException) {
            comment = getStringResource(R.string.omnipod_error_invalid_message_address);
        } else if (ex instanceof MessageDecodingException) {
            comment = getStringResource(R.string.omnipod_error_message_decoding_failed);
        } else if (ex instanceof NonceOutOfSyncException) {
            comment = getStringResource(R.string.omnipod_error_nonce_out_of_sync);
        } else if (ex instanceof NonceResyncException) {
            comment = getStringResource(R.string.omnipod_error_nonce_resync_failed);
        } else if (ex instanceof NotEnoughDataException) {
            comment = getStringResource(R.string.omnipod_error_not_enough_data);
        } else if (ex instanceof PodFaultException) {
            FaultEventCode faultEventCode = ((PodFaultException) ex).getDetailedStatus().getFaultEventCode();
            comment = createPodFaultErrorMessage(faultEventCode);
        } else if (ex instanceof ActivationTimeExceededException) {
            comment = getStringResource(R.string.omnipod_error_pod_fault_activation_time_exceeded);
        } else if (ex instanceof PodReturnedErrorResponseException) {
            comment = getStringResource(R.string.omnipod_error_pod_returned_error_response);
        } else if (ex instanceof RileyLinkUnreachableException) {
            comment = getStringResource(R.string.omnipod_error_communication_failed_no_response_from_riley_link);
        } else if (ex instanceof RileyLinkInterruptedException) {
            comment = getStringResource(R.string.omnipod_error_communication_failed_riley_link_interrupted);
        } else if (ex instanceof RileyLinkTimeoutException) {
            comment = getStringResource(R.string.omnipod_error_communication_failed_no_response_from_pod);
        } else if (ex instanceof RileyLinkUnexpectedException) {
            Throwable cause = ex.getCause();
            comment = getStringResource(R.string.omnipod_error_unexpected_exception, cause.getClass().getName(), cause.getMessage());
        } else {
            // Shouldn't be reachable
            comment = getStringResource(R.string.omnipod_error_unexpected_exception, ex.getClass().getName(), ex.getMessage());
        }

        return comment;
    }

    private String createPodFaultErrorMessage(FaultEventCode faultEventCode) {
        return getStringResource(R.string.omnipod_error_pod_fault,
                ByteUtil.convertUnsignedByteToInt(faultEventCode.getValue()), faultEventCode.name());
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }

    private void showErrorDialog(String message, Integer sound) {
        Intent intent = new Intent(context, ErrorHelperActivity.class);
        intent.putExtra("soundid", sound);
        intent.putExtra("status", message);
        intent.putExtra("title", resourceHelper.gs(R.string.error));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode) {
        showPodFaultNotification(faultEventCode, R.raw.boluserror);
    }

    private void showPodFaultNotification(FaultEventCode faultEventCode, Integer sound) {
        showNotification(Notification.OMNIPOD_POD_FAULT, createPodFaultErrorMessage(faultEventCode), Notification.URGENT, sound);
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

    private void dismissNotification(int id) {
        sendEvent(new EventDismissNotification(id));
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

    private void uploadCareportalEvent(long date, String event) {
        if (databaseHelper.getCareportalEventFromTimestamp(date) != null)
            return;
        try {
            JSONObject data = new JSONObject();
            String enteredBy = sp.getString("careportal_enteredby", "");
            if (enteredBy.isEmpty()) {
                data.put("enteredBy", enteredBy);
            }
            data.put("created_at", DateUtil.toISOString(date));
            data.put("mills", date);
            data.put("eventType", event);
            data.put("units", profileFunction.getUnits());
            CareportalEvent careportalEvent = new CareportalEvent(injector);
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = event;
            careportalEvent.json = data.toString();
            databaseHelper.createOrUpdate(careportalEvent);
            nsUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception when uploading SiteChange event.", e);
        }
    }
}
