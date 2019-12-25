package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.SetupActionResult;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentHighFlashLogDump;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodHistory;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodAcknowledgeAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.CommandInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.MessageDecodingException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.NonceResyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import io.reactivex.disposables.Disposable;

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {
    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);
    private final OmnipodManager delegate;

    private static AapsOmnipodManager instance;
    private OmnipodPumpStatus pumpStatus;

    private Date lastBolusTime;
    private Double lastBolusUnits;

    public static AapsOmnipodManager getInstance() {
        return instance;
    }

    public AapsOmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState, OmnipodPumpStatus _pumpStatus) {
        delegate = new OmnipodManager(communicationService, podState, podSessionState -> {
            // Handle pod state changes
            OmnipodUtil.setPodSessionState(podSessionState);
            updatePumpStatus(podSessionState);
        });
        this.pumpStatus = _pumpStatus;
        instance = this;
    }

    private void updatePumpStatus(PodSessionState podSessionState) {
        if (pumpStatus != null) {
            if (podSessionState == null) {
                pumpStatus.ackAlertsText = null;
                pumpStatus.ackAlertsAvailable = false;
                pumpStatus.lastBolusTime = null;
                pumpStatus.lastBolusAmount = null;
                pumpStatus.reservoirRemainingUnits = 0.0;
                pumpStatus.pumpStatusType = PumpStatusType.Suspended;
                sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
                sendEvent(new EventOmnipodPumpValuesChanged());
                sendEvent(new EventRefreshOverview("Omnipod Pump"));
            } else {
                // Update active alerts
                if (podSessionState.hasActiveAlerts()) {
                    List<String> alerts = translateActiveAlerts(podSessionState);
                    String alertsText = TextUtils.join("\n", alerts);

                    if (!pumpStatus.ackAlertsAvailable || !alertsText.equals(pumpStatus.ackAlertsText)) {
                        pumpStatus.ackAlertsAvailable = true;
                        pumpStatus.ackAlertsText = TextUtils.join("\n", alerts);

                        sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
                    }
                } else {
                    if (pumpStatus.ackAlertsAvailable || StringUtils.isNotEmpty(pumpStatus.ackAlertsText)) {
                        pumpStatus.ackAlertsText = null;
                        pumpStatus.ackAlertsAvailable = false;
                        sendEvent(new EventOmnipodAcknowledgeAlertsChanged());
                    }
                }

                // Update other info: last bolus, units remaining, suspended
                if (!Objects.equals(lastBolusTime, pumpStatus.lastBolusTime) //
                        || !Objects.equals(lastBolusUnits, pumpStatus.lastBolusAmount) //
                        || !isReservoirStatusUpToDate(pumpStatus, podSessionState.getReservoirLevel())
                        || podSessionState.isSuspended() != PumpStatusType.Suspended.equals(pumpStatus.pumpStatusType)) {
                    pumpStatus.lastBolusTime = lastBolusTime;
                    pumpStatus.lastBolusAmount = lastBolusUnits;
                    pumpStatus.reservoirRemainingUnits = podSessionState.getReservoirLevel() == null ? 75.0 : podSessionState.getReservoirLevel();
                    pumpStatus.pumpStatusType = podSessionState.isSuspended() ? PumpStatusType.Suspended : PumpStatusType.Running;
                    sendEvent(new EventOmnipodPumpValuesChanged());

                    if (podSessionState.isSuspended() != PumpStatusType.Suspended.equals(pumpStatus.pumpStatusType)) {
                        sendEvent(new EventRefreshOverview("Omnipod Pump"));
                    }
                }
            }
        }
    }

    private static boolean isReservoirStatusUpToDate(OmnipodPumpStatus pumpStatus, Double unitsRemaining) {
        double expectedUnitsRemaining = unitsRemaining == null ? 75.0 : unitsRemaining;
        return Math.abs(expectedUnitsRemaining - pumpStatus.reservoirRemainingUnits) < 0.000001;
    }

    private List<String> translateActiveAlerts(PodSessionState podSessionState) {
        List<String> alerts = new ArrayList<>();
        for (AlertSlot alertSlot : podSessionState.getActiveAlerts().getAlertSlots()) {
            alerts.add(translateAlertType(podSessionState.getConfiguredAlertType(alertSlot)));
        }
        return alerts;
    }

    @Override
    public PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile) {
        long time = System.currentTimeMillis();
        if (PodInitActionType.PairAndPrimeWizardStep.equals(podInitActionType)) {
            try {
                Disposable disposable = delegate.pairAndPrime().subscribe(res -> //
                        handleSetupActionResult(podInitActionType, podInitReceiver, res, time));
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                addFailureToHistory(time, PodHistoryEntryType.PairAndPrime, comment);
                return new PumpEnactResult().success(false).enacted(false).comment(comment);
            }
        } else if (PodInitActionType.FillCannulaSetBasalProfileWizardStep.equals(podInitActionType)) {
            try {
                BasalSchedule basalSchedule;
                try {
                    basalSchedule = mapProfileToBasalSchedule(profile);
                } catch (Exception ex) {
                    throw new CommandInitializationException("Basal profile mapping failed", ex);
                }
                Disposable disposable = delegate.insertCannula(basalSchedule).subscribe(res -> //
                        handleSetupActionResult(podInitActionType, podInitReceiver, res, time));
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                addFailureToHistory(time, PodHistoryEntryType.FillCannulaSetBasalProfile, comment);
                return new PumpEnactResult().success(false).enacted(false).comment(comment);
            }
        }

        return new PumpEnactResult().success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_illegal_init_action_type, podInitActionType.name()));
    }

    @Override
    public PumpEnactResult getPodStatus() {
        long time = System.currentTimeMillis();
        try {
            StatusResponse statusResponse = delegate.getPodStatus();
            addSuccessToHistory(time, PodHistoryEntryType.GetPodStatus, statusResponse);
            return new PumpEnactResult().success(true).enacted(false);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.GetPodStatus, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        long time = System.currentTimeMillis();
        try {
            delegate.deactivatePod();
            addSuccessToHistory(time, PodHistoryEntryType.DeactivatePod, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, comment);
            addFailureToHistory(time, PodHistoryEntryType.DeactivatePod, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, true, null);

        OmnipodUtil.setPodSessionState(null);

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setBasalProfile(Profile profile) {
        long time = System.currentTimeMillis();
        try {
            BasalSchedule basalSchedule;
            try {
                basalSchedule = mapProfileToBasalSchedule(profile);
            } catch (Exception ex) {
                throw new CommandInitializationException("Basal profile mapping failed", ex);
            }
            delegate.setBasalSchedule(basalSchedule, isBasalBeepsEnabled());
            addSuccessToHistory(time, PodHistoryEntryType.SetBasalSchedule, profile);
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                addToHistory(time, PodHistoryEntryType.SetBasalSchedule, "Uncertain failure", false);
                return new PumpEnactResult().success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_basal_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.SetBasalSchedule, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        // Because setting a basal profile actually suspends and then resumes delivery, TBR is implicitly cancelled
        reportImplicitlyCanceledTbr();

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult resetPodStatus() {
        delegate.resetPodState();

        OmnipodUtil.setPodSessionState(null);

        addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.ResetPodState, null);

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setBolus(Double units, boolean isSmb) {
        OmnipodManager.BolusCommandResult bolusCommandResult;

        boolean beepsEnabled = isSmb ? isSmbBeepsEnabled() : isBolusBeepsEnabled();

        Date bolusStarted;
        try {
            bolusCommandResult = delegate.bolus(units, beepsEnabled, beepsEnabled, isSmb ? null :
                    (estimatedUnitsDelivered, percentage) -> {
                        EventOverviewBolusProgress progressUpdateEvent = EventOverviewBolusProgress.INSTANCE;
                        progressUpdateEvent.setStatus(getStringResource(R.string.bolusdelivering, units));
                        progressUpdateEvent.setPercent(percentage);
                        sendEvent(progressUpdateEvent);
                    });

            bolusStarted = new Date();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        if (OmnipodManager.CommandDeliveryStatus.UNCERTAIN_FAILURE.equals(bolusCommandResult.getCommandDeliveryStatus())) {
            // For safety reasons, we treat this as a bolus that has successfully been delivered, in order to prevent insulin overdose

            // FIXME We can't dismiss the alert while the bolus progress dialog is open, so don't use a sound
            showNotificationWithDialog(getStringResource(R.string.omnipod_bolus_failed_uncertain), Notification.URGENT, null);
        }

        // Wait for the bolus to finish
        OmnipodManager.BolusDeliveryResult bolusDeliveryResult =
                bolusCommandResult.getDeliveryResultSubject().blockingGet();

        double unitsDelivered = bolusDeliveryResult.getUnitsDelivered();

        if (pumpStatus != null && !isSmb) {
            lastBolusTime = pumpStatus.lastBolusTime = bolusStarted;
            lastBolusUnits = pumpStatus.lastBolusAmount = unitsDelivered;
        }

        return new PumpEnactResult().success(true).enacted(true).bolusDelivered(unitsDelivered);
    }

    @Override
    public PumpEnactResult cancelBolus() {
        long time = System.currentTimeMillis();
        try {
            delegate.cancelBolus(isBolusBeepsEnabled());
            addSuccessToHistory(time, PodHistoryEntryType.CancelBolus, null);
        } catch (PodFaultException ex) {
            showNotificationWithDialog(createPodFaultErrorMessage(ex.getFaultEvent().getFaultEventType()), Notification.URGENT, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.CancelBolus, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        boolean beepsEnabled = isTempBasalBeepsEnabled();
        long time = System.currentTimeMillis();
        try {
            delegate.setTemporaryBasal(tempBasalPair, beepsEnabled, beepsEnabled);
            addSuccessToHistory(time, PodHistoryEntryType.SetTemporaryBasal, tempBasalPair);
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                addToHistory(time, PodHistoryEntryType.SetTemporaryBasal, "Uncertain failure", false);
                return new PumpEnactResult().success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_temp_basal_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.SetTemporaryBasal, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        long time = System.currentTimeMillis();
        try {
            delegate.cancelTemporaryBasal(isTempBasalBeepsEnabled());
            addSuccessToHistory(time, PodHistoryEntryType.CancelTemporaryBasal, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.CancelTemporaryBasal, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult acknowledgeAlerts() {
        long time = System.currentTimeMillis();
        try {
            delegate.acknowledgeAlerts();
            addSuccessToHistory(time, PodHistoryEntryType.AcknowledgeAlerts, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.AcknowledgeAlerts, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public void setPumpStatus(OmnipodPumpStatus pumpStatus) {
        this.pumpStatus = pumpStatus;
        this.getCommunicationService().setPumpStatus(pumpStatus);
        updatePumpStatus(delegate.getPodState());
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult getPodInfo(PodInfoType podInfoType) {
        long time = System.currentTimeMillis();
        try {
            // TODO how can we return the PodInfo response?
            // This method is useless unless we return the PodInfoResponse,
            // because the pod state we keep, doesn't get updated from a PodInfoResponse.
            // We use StatusResponses for that, which can be obtained from the getPodStatus method
            PodInfoResponse podInfo = delegate.getPodInfo(podInfoType);
            addSuccessToHistory(time, PodHistoryEntryType.GetPodInfo, podInfo);
            return new PumpEnactResult().success(true).enacted(true);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.GetPodInfo, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult suspendDelivery() {
        try {
            delegate.suspendDelivery(isBasalBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult resumeDelivery() {
        try {
            delegate.resumeDelivery(isBasalBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    // Updates the pods current time based on the device timezone and the pod's time zone
    public PumpEnactResult setTime() {
        long time = System.currentTimeMillis();
        try {
            delegate.setTime(isBasalBeepsEnabled());
            addSuccessToHistory(time, PodHistoryEntryType.SetTime, null);
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                addFailureToHistory(time, PodHistoryEntryType.SetTime, "Uncertain failure");
                return new PumpEnactResult().success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_time_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.SetTime, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        // Because set time actually suspends and then resumes delivery, TBR is implicitly cancelled
        reportImplicitlyCanceledTbr();

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PodInfoRecentHighFlashLogDump readPulseLog() {
        PodInfoResponse response = delegate.getPodInfo(PodInfoType.RECENT_HIGH_FLASH_LOG_DUMP);
        return response.getPodInfo();
    }

    public OmnipodCommunicationService getCommunicationService() {
        return delegate.getCommunicationService();
    }

    public DateTime getTime() {
        return delegate.getTime();
    }

    public boolean isInitialized() {
        return delegate.isReadyForDelivery();
    }

    public String getPodStateAsString() {
        return delegate.getPodStateAsString();
    }

    private void reportImplicitlyCanceledTbr() {
        TreatmentsPlugin plugin = TreatmentsPlugin.getPlugin();
        if (plugin.isTempBasalInProgress()) {
            if (isLoggingEnabled()) {
                LOG.debug("Reporting implicitly cancelled TBR to Treatments plugin");
            }

            TemporaryBasal temporaryBasal = new TemporaryBasal() //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    // TODO bs should be Source.PUMP imo, but that doesn't work:
                    //  it says a TEMPBASAL record already exists
                    .source(Source.USER);
            plugin.addToHistoryTempBasal(temporaryBasal);
        }
    }


    public void addSuccessToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        addToHistory(requestTime, entryType, data, true);
    }

    public void addFailureToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        addToHistory(requestTime, entryType, data, false);
    }


    public void addToHistory(long requestTime, PodHistoryEntryType entryType, Object data, boolean success) {

        PodHistory podHistory = new PodHistory(requestTime, entryType);

        if (data != null) {
            if (data instanceof String) {
                podHistory.setData((String) data);
            } else {
                podHistory.setData(OmnipodUtil.getGsonInstance().toJson(data));
            }
        }

        podHistory.setSuccess(success);
        podHistory.setPodSerial(pumpStatus.podNumber);

        MainApp.getDbHelper().createOrUpdate(podHistory);

    }

    private void handleSetupActionResult(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, SetupActionResult res, long time) {
        String comment = null;
        switch (res.getResultType()) {
            case FAILURE:
                if (isLoggingEnabled()) {
                    LOG.error("Setup action failed: illegal setup progress: {}", res.getSetupProgress());
                }
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state, res.getSetupProgress());
                break;
            case VERIFICATION_FAILURE:
                if (isLoggingEnabled()) {
                    LOG.error("Setup action verification failed: caught exception", res.getException());
                }
                comment = getStringResource(R.string.omnipod_driver_error_setup_action_verification_failed);
                break;
        }

        addToHistory(time, podInitActionType == PodInitActionType.PairAndPrimeWizardStep ?
                PodHistoryEntryType.PairAndPrime : PodHistoryEntryType.FillCannulaSetBasalProfile, comment, res.getResultType().isSuccess());

        podInitReceiver.returnInitTaskStatus(podInitActionType, res.getResultType().isSuccess(), comment);
    }

    private String handleAndTranslateException(Exception ex) {
        String comment;

        if (ex instanceof OmnipodException) {
            if (ex instanceof ActionInitializationException || ex instanceof CommandInitializationException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_parameters);
            } else if (ex instanceof CommunicationException) {
                comment = getStringResource(R.string.omnipod_driver_error_communication_failed);
            } else if (ex instanceof CrcMismatchException) {
                comment = getStringResource(R.string.omnipod_driver_error_crc_mismatch);
            } else if (ex instanceof IllegalPacketTypeException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_packet_type);
            } else if (ex instanceof IllegalPodProgressException || ex instanceof IllegalSetupProgressException
                    || ex instanceof IllegalDeliveryStatusException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state);
            } else if (ex instanceof IllegalResponseException) {
                comment = getStringResource(R.string.omnipod_driver_error_invalid_response);
            } else if (ex instanceof MessageDecodingException) {
                comment = getStringResource(R.string.omnipod_driver_error_message_decoding_failed);
            } else if (ex instanceof NonceOutOfSyncException) {
                comment = getStringResource(R.string.omnipod_driver_error_nonce_out_of_sync);
            } else if (ex instanceof NonceResyncException) {
                comment = getStringResource(R.string.omnipod_driver_error_nonce_resync_failed);
            } else if (ex instanceof NotEnoughDataException) {
                comment = getStringResource(R.string.omnipod_driver_error_not_enough_data);
            } else if (ex instanceof PodFaultException) {
                FaultEventType faultEventType = ((PodFaultException) ex).getFaultEvent().getFaultEventType();
                comment = createPodFaultErrorMessage(faultEventType);
            } else if (ex instanceof PodReturnedErrorResponseException) {
                comment = getStringResource(R.string.omnipod_driver_error_pod_returned_error_response);
            } else {
                // Shouldn't be reachable
                comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            }
            if (isLoggingEnabled()) {
                LOG.error(String.format("Caught OmnipodException[certainFailure=%s] from OmnipodManager (user-friendly error message: %s)", ((OmnipodException) ex).isCertainFailure(), comment), ex);
            }
        } else {
            comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            if (isLoggingEnabled()) {
                LOG.error(String.format("Caught unexpected exception type[certainFailure=false] from OmnipodManager (user-friendly error message: %s)", comment), ex);
            }
        }

        return comment;
    }

    private String createPodFaultErrorMessage(FaultEventType faultEventType) {
        String comment;
        comment = getStringResource(R.string.omnipod_driver_error_pod_fault,
                ByteUtil.convertUnsignedByteToInt(faultEventType.getValue()), faultEventType.name());
        return comment;
    }

    private void sendEvent(Event event) {
        RxBus.INSTANCE.send(event);
    }

    private void showNotificationWithDialog(String message, int urgency, Integer sound) {
        // TODO
        showNotification(message, urgency, sound);
    }

    private void showNotification(String message, int urgency, Integer sound) {
        Notification notification = new Notification( //
                Notification.OMNIPOD_PUMP_ALARM, //
                message, //
                urgency);
        if (sound != null) {
            notification.soundId = sound;
        }
        sendEvent(new EventNewNotification(notification));
    }

    private String translateAlertType(AlertType alertType) {
        if (alertType == null) {
            return getStringResource(R.string.omnipod_alert_unknown_alert);
        }
        switch (alertType) {
            case FINISH_PAIRING_REMINDER:
                return getStringResource(R.string.omnipod_alert_finish_pairing_reminder);
            case FINISH_SETUP_REMINDER:
                return getStringResource(R.string.omnipod_alert_finish_setup_reminder_reminder);
            case EXPIRATION_ALERT:
                return getStringResource(R.string.omnipod_alert_expiration);
            case EXPIRATION_ADVISORY_ALERT:
                return getStringResource(R.string.omnipod_alert_expiration_advisory);
            case SHUTDOWN_IMMINENT_ALARM:
                return getStringResource(R.string.omnipod_alert_shutdown_imminent);
            case LOW_RESERVOIR_ALERT:
                return getStringResource(R.string.omnipod_alert_low_reservoir);
            default:
                return alertType.name();
        }
    }

    private boolean isBolusBeepsEnabled() {
        return this.pumpStatus.beepBolusEnabled;
    }

    private boolean isSmbBeepsEnabled() {
        return this.pumpStatus.beepSMBEnabled;
    }

    private boolean isBasalBeepsEnabled() {
        return this.pumpStatus.beepBasalEnabled;
    }

    private boolean isTempBasalBeepsEnabled() {
        return this.pumpStatus.beepTBREnabled;
    }

    private String getStringResource(int id, Object... args) {
        return MainApp.gs(id, args);
    }

    private boolean isLoggingEnabled() {
        return L.isEnabled(L.PUMP);
    }

    static BasalSchedule mapProfileToBasalSchedule(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile can not be null");
        }
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        if (basalValues == null) {
            throw new IllegalArgumentException("Basal values can not be null");
        }
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for (Profile.ProfileValue basalValue : basalValues) {
            entries.add(new BasalScheduleEntry(basalValue.value, Duration.standardSeconds(basalValue.timeAsSeconds)));
        }

        return new BasalSchedule(entries);
    }
}
