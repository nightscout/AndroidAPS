package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.SetupActionResult;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommandInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageSequenceNumberException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.MessageDecodingException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NonceResyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;
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
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.Disposable;

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {

    private final OmnipodUtil omnipodUtil;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final HasAndroidInjector injector;
    private final ActivePluginProvider activePlugin;
    private final OmnipodPumpStatus pumpStatus;
    private final Context context;

    private final OmnipodManager delegate;

    @Deprecated
    private static AapsOmnipodManager instance;

    private Date lastBolusTime;
    private Double lastBolusUnits;

    @Deprecated
    public static AapsOmnipodManager getInstance() {
        return instance;
    }

    public AapsOmnipodManager(OmnipodCommunicationManager communicationService,
                              PodSessionState podState,
                              OmnipodPumpStatus pumpStatus,
                              OmnipodUtil omnipodUtil,
                              AAPSLogger aapsLogger,
                              RxBusWrapper rxBus,
                              SP sp,
                              ResourceHelper resourceHelper,
                              HasAndroidInjector injector,
                              ActivePluginProvider activePlugin,
                              Context context) {
        this.omnipodUtil = omnipodUtil;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.injector = injector;
        this.activePlugin = activePlugin;
        this.pumpStatus = pumpStatus;
        this.context = context;

        delegate = new OmnipodManager(aapsLogger, sp, communicationService, podState, podSessionState -> {
            // Handle pod state changes
            omnipodUtil.setPodSessionState(podSessionState);
            updatePumpStatus(podSessionState);
        });
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
                sendEvent(new EventRefreshOverview("Omnipod Pump", false));
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
                        sendEvent(new EventRefreshOverview("Omnipod Pump", false));
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
                int address = obtainNextPodAddress();

                Disposable disposable = delegate.pairAndPrime(address).subscribe(res -> //
                        handleSetupActionResult(podInitActionType, podInitReceiver, res, time, null));

                removeNextPodAddress();

                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                addFailureToHistory(time, PodHistoryEntryType.PairAndPrime, comment);
                return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
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
                        handleSetupActionResult(podInitActionType, podInitReceiver, res, time, profile));
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                addFailureToHistory(time, PodHistoryEntryType.FillCannulaSetBasalProfile, comment);
                return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
            }
        }

        return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_illegal_init_action_type, podInitActionType.name()));
    }

    @Override
    public PumpEnactResult getPodStatus() {
        long time = System.currentTimeMillis();
        try {
            StatusResponse statusResponse = delegate.getPodStatus();
            addSuccessToHistory(time, PodHistoryEntryType.GetPodStatus, statusResponse);
            return new PumpEnactResult(injector).success(true).enacted(false);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.GetPodStatus, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        long time = System.currentTimeMillis();
        try {
            delegate.deactivatePod();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, comment);
            addFailureToHistory(time, PodHistoryEntryType.DeactivatePod, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        reportImplicitlyCanceledTbr();

        addSuccessToHistory(time, PodHistoryEntryType.DeactivatePod, null);

        podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, true, null);

        this.omnipodUtil.setPodSessionState(null);

        return new PumpEnactResult(injector).success(true).enacted(true);
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
            // Because setting a basal profile actually suspends and then resumes delivery, TBR is implicitly cancelled
            reportImplicitlyCanceledTbr();
            addSuccessToHistory(time, PodHistoryEntryType.SetBasalSchedule, profile.getBasalValues());
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                reportImplicitlyCanceledTbr();
                addToHistory(time, PodHistoryEntryType.SetBasalSchedule, "Uncertain failure", false);
                return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_basal_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            reportImplicitlyCanceledTbr();
            addFailureToHistory(time, PodHistoryEntryType.SetBasalSchedule, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }


        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    @Override
    public PumpEnactResult resetPodStatus() {
        delegate.resetPodState(true);

        reportImplicitlyCanceledTbr();

        this.omnipodUtil.setPodSessionState(null);
        this.omnipodUtil.removeNextPodAddress();

        addSuccessToHistory(System.currentTimeMillis(), PodHistoryEntryType.ResetPodState, null);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setBolus(DetailedBolusInfo detailedBolusInfo) {
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
            addFailureToHistory(System.currentTimeMillis(), PodHistoryEntryType.SetBolus, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        if (OmnipodManager.CommandDeliveryStatus.UNCERTAIN_FAILURE.equals(bolusCommandResult.getCommandDeliveryStatus())) {
            // For safety reasons, we treat this as a bolus that has successfully been delivered, in order to prevent insulin overdose

            showErrorDialog(getStringResource(R.string.omnipod_bolus_failed_uncertain), R.raw.boluserror);
        }

        // Wait for the bolus to finish
        OmnipodManager.BolusDeliveryResult bolusDeliveryResult =
                bolusCommandResult.getDeliveryResultSubject().blockingGet();

        double unitsDelivered = bolusDeliveryResult.getUnitsDelivered();

        if (pumpStatus != null && !detailedBolusInfo.isSMB) {
            lastBolusTime = pumpStatus.lastBolusTime = bolusStarted;
            lastBolusUnits = pumpStatus.lastBolusAmount = unitsDelivered;
        }

        long pumpId = addSuccessToHistory(bolusStarted.getTime(), PodHistoryEntryType.SetBolus, unitsDelivered + ";" + detailedBolusInfo.carbs);

        detailedBolusInfo.date = bolusStarted.getTime();
        detailedBolusInfo.insulin = unitsDelivered;
        detailedBolusInfo.pumpId = pumpId;
        detailedBolusInfo.source = Source.PUMP;

        activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, false);

        if (delegate.getPodState().hasFaultEvent()) {
            showPodFaultErrorDialog(delegate.getPodState().getFaultEvent().getFaultEventCode(), R.raw.urgentalarm);
        }

        return new PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(unitsDelivered);
    }

    @Override
    public PumpEnactResult cancelBolus() {
        long time = System.currentTimeMillis();
        String comment = null;
        while (delegate.hasActiveBolus()) {
            try {
                delegate.cancelBolus(isBolusBeepsEnabled());
                addSuccessToHistory(time, PodHistoryEntryType.CancelBolus, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (PodFaultException ex) {
                showPodFaultErrorDialog(ex.getFaultEvent().getFaultEventCode(), null);
                addSuccessToHistory(time, PodHistoryEntryType.CancelBolus, null);
                return new PumpEnactResult(injector).success(true).enacted(true);
            } catch (Exception ex) {
                comment = handleAndTranslateException(ex);
            }
        }

        addFailureToHistory(time, PodHistoryEntryType.CancelBolus, comment);
        return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        boolean beepsEnabled = isTempBasalBeepsEnabled();
        long time = System.currentTimeMillis();
        try {
            delegate.setTemporaryBasal(PumpType.Insulet_Omnipod.determineCorrectBasalSize(tempBasalPair.getInsulinRate()), Duration.standardMinutes(tempBasalPair.getDurationMinutes()), beepsEnabled, beepsEnabled);
            time = System.currentTimeMillis();
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                addToHistory(time, PodHistoryEntryType.SetTemporaryBasal, "Uncertain failure", false);
                return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_temp_basal_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.SetTemporaryBasal, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        reportImplicitlyCanceledTbr();

        long pumpId = addSuccessToHistory(time, PodHistoryEntryType.SetTemporaryBasal, tempBasalPair);

        pumpStatus.tempBasalStart = time;
        pumpStatus.tempBasalAmount = tempBasalPair.getInsulinRate();
        pumpStatus.tempBasalLength = tempBasalPair.getDurationMinutes();
        pumpStatus.tempBasalEnd = DateTimeUtil.getTimeInFutureFromMinutes(time, tempBasalPair.getDurationMinutes());
        pumpStatus.tempBasalPumpId = pumpId;

        TemporaryBasal tempStart = new TemporaryBasal(injector) //
                .date(time) //
                .duration(tempBasalPair.getDurationMinutes()) //
                .absolute(tempBasalPair.getInsulinRate()) //
                .pumpId(pumpId)
                .source(Source.PUMP);

        activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        long time = System.currentTimeMillis();
        try {
            delegate.cancelTemporaryBasal(isTempBasalBeepsEnabled());
            addSuccessToHistory(time, PodHistoryEntryType.CancelTemporaryBasalForce, null);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.CancelTemporaryBasalForce, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult(injector).success(true).enacted(true);
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
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
        return new PumpEnactResult(injector).success(true).enacted(true);
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
            return new PumpEnactResult(injector).success(true).enacted(true);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            addFailureToHistory(time, PodHistoryEntryType.GetPodInfo, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult suspendDelivery() {
        try {
            delegate.suspendDelivery(isBasalBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PumpEnactResult resumeDelivery() {
        try {
            delegate.resumeDelivery(isBasalBeepsEnabled());
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    // Updates the pods current time based on the device timezone and the pod's time zone
    public PumpEnactResult setTime() {
        long time = System.currentTimeMillis();
        try {
            delegate.setTime(isBasalBeepsEnabled());
            // Because set time actually suspends and then resumes delivery, TBR is implicitly cancelled
            reportImplicitlyCanceledTbr();
            addSuccessToHistory(time, PodHistoryEntryType.SetTime, null);
        } catch (Exception ex) {
            if ((ex instanceof OmnipodException) && !((OmnipodException) ex).isCertainFailure()) {
                reportImplicitlyCanceledTbr();
                addFailureToHistory(time, PodHistoryEntryType.SetTime, "Uncertain failure");
                return new PumpEnactResult(injector).success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_set_time_failed_uncertain));
            }
            String comment = handleAndTranslateException(ex);
            reportImplicitlyCanceledTbr();
            addFailureToHistory(time, PodHistoryEntryType.SetTime, comment);
            return new PumpEnactResult(injector).success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult(injector).success(true).enacted(true);
    }

    public PodInfoRecentPulseLog readPulseLog() {
        PodInfoResponse response = delegate.getPodInfo(PodInfoType.RECENT_PULSE_LOG);
        return response.getPodInfo();
    }

    public OmnipodCommunicationManager getCommunicationService() {
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
        //TreatmentsPlugin plugin = TreatmentsPlugin.getPlugin();
        TreatmentsInterface plugin = activePlugin.getActiveTreatments();
        if (plugin.isTempBasalInProgress()) {
            aapsLogger.debug(LTag.PUMP, "Reporting implicitly cancelled TBR to Treatments plugin");

            long time = System.currentTimeMillis() - 1000;

            addSuccessToHistory(time, PodHistoryEntryType.CancelTemporaryBasal, null);

            TemporaryBasal temporaryBasal = new TemporaryBasal(injector) //
                    .date(time) //
                    .duration(0) //
                    .pumpId(pumpStatus.tempBasalPumpId)
                    .source(Source.PUMP);

            plugin.addToHistoryTempBasal(temporaryBasal);
        }
    }

    private long addSuccessToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        return addToHistory(requestTime, entryType, data, true);
    }

    private long addFailureToHistory(long requestTime, PodHistoryEntryType entryType, Object data) {
        return addToHistory(requestTime, entryType, data, false);
    }

    private long addToHistory(long requestTime, PodHistoryEntryType entryType, Object data, boolean success) {
        PodHistory podHistory = new PodHistory(requestTime, entryType);

        if (data != null) {
            if (data instanceof String) {
                podHistory.setData((String) data);
            } else {
                podHistory.setData(omnipodUtil.getGsonInstance().toJson(data));
            }
        }

        podHistory.setSuccess(success);
        podHistory.setPodSerial(pumpStatus.podNumber);

        MainApp.getDbHelper().createOrUpdate(podHistory);

        return podHistory.getPumpId();
    }

    private int obtainNextPodAddress() {
        Integer nextPodAddress = this.omnipodUtil.getNextPodAddress();
        if (nextPodAddress == null) {
            nextPodAddress = OmnipodManager.generateRandomAddress();
            this.omnipodUtil.setNextPodAddress(nextPodAddress);
        }

        return nextPodAddress;
    }

    private void removeNextPodAddress() {
        this.omnipodUtil.removeNextPodAddress();
    }

    private void handleSetupActionResult(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, SetupActionResult res, long time, Profile profile) {
        String comment = null;
        switch (res.getResultType()) {
            case FAILURE: {
                aapsLogger.error(LTag.PUMP, "Setup action failed: illegal setup progress: {}", res.getSetupProgress());
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state, res.getSetupProgress());
            }
            break;
            case VERIFICATION_FAILURE: {
                aapsLogger.error(LTag.PUMP, "Setup action verification failed: caught exception", res.getException());
                comment = getStringResource(R.string.omnipod_driver_error_setup_action_verification_failed);
            }
            break;
        }

        if (podInitActionType == PodInitActionType.PairAndPrimeWizardStep) {
            addToHistory(time, PodHistoryEntryType.PairAndPrime, comment, res.getResultType().isSuccess());
        } else {
            addToHistory(time, PodHistoryEntryType.FillCannulaSetBasalProfile, res.getResultType().isSuccess() ? profile.getBasalValues() : comment, res.getResultType().isSuccess());
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
            } else if (ex instanceof IllegalPodProgressException || ex instanceof IllegalSetupProgressException
                    || ex instanceof IllegalDeliveryStatusException) {
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
                showPodFaultErrorDialog(faultEventCode, R.raw.urgentalarm);
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

    private void showPodFaultErrorDialog(FaultEventCode faultEventCode, Integer sound) {
        showErrorDialog(createPodFaultErrorMessage(faultEventCode), sound);
    }

    private void showErrorDialog(String message, Integer sound) {
        Intent intent = new Intent(context, ErrorHelperActivity.class);
        intent.putExtra("soundid", sound == null ? 0 : sound);
        intent.putExtra("status", message);
        intent.putExtra("title", resourceHelper.gs(R.string.treatmentdeliveryerror));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
        return resourceHelper.gs(id, args);
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
            entries.add(new BasalScheduleEntry(PumpType.Insulet_Omnipod.determineCorrectBasalSize(basalValue.value),
                    Duration.standardSeconds(basalValue.timeAsSeconds)));
        }

        return new BasalSchedule(entries);
    }
}
