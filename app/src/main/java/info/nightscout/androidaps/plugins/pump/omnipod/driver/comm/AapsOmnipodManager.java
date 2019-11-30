package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.SetupActionResult;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodDbEntryType;
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

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {
    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);
    private final OmnipodManager delegate;

    private static AapsOmnipodManager instance;
    private OmnipodPumpStatus pumpStatus;

    public static AapsOmnipodManager getInstance() {
        return instance;
    }

    public AapsOmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState, OmnipodPumpStatus pumpStatus) {
        delegate = new OmnipodManager(communicationService, podState);
        this.pumpStatus = pumpStatus;
        instance = this;
    }

    @Override
    public PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile) {
        if (PodInitActionType.PairAndPrimeWizardStep.equals(podInitActionType)) {
            try {
                delegate.pairAndPrime(res -> //
                        handleSetupActionResult(podInitActionType, podInitReceiver, res));
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                return new PumpEnactResult().success(false).enacted(false).comment(comment);
            }
        } else if (PodInitActionType.FillCannulaSetBasalProfileWizardStep.equals(podInitActionType)) {
            try {
                delegate.insertCannula(mapProfileToBasalSchedule(profile), res -> //
                        handleSetupActionResult(podInitActionType, podInitReceiver, res));
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                String comment = handleAndTranslateException(ex);
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, comment);
                return new PumpEnactResult().success(false).enacted(false).comment(comment);
            }
        }

        return new PumpEnactResult().success(false).enacted(false).comment(getStringResource(R.string.omnipod_error_illegal_init_action_type, podInitActionType.name()));
    }

    @Override
    public PumpEnactResult getPodStatus() {
        try {
            StatusResponse statusResponse = delegate.getPodStatus();
            return new PumpEnactResult().success(true).enacted(false);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        try {
            delegate.deactivatePod();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, comment);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, true, null);

        OmnipodUtil.setPodSessionState(null);
        RxBus.INSTANCE.send(new EventOmnipodPumpValuesChanged());

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setBasalProfile(Profile basalProfile) {
        try {
            delegate.setBasalSchedule(mapProfileToBasalSchedule(basalProfile));
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult resetPodStatus() {
        delegate.resetPodState();

        //addToHistory(System.currentTimeMillis(), PodDbEntryType.ResetPodState, null, null, null, null);

        OmnipodUtil.setPodSessionState(null);
        RxBus.INSTANCE.send(new EventOmnipodPumpValuesChanged());

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setBolus(Double amount) {
        try {
            delegate.bolus(amount, statusResponse -> {
                if (statusResponse == null) {
                    // Failed to retrieve status response after bolus
                    // Bolus probably finished anyway
                } else if (statusResponse.getDeliveryStatus().isBolusing()) {
                    // This shouldn't happen
                } else {
                    // Bolus successfully completed
                }
            });
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            if(OmnipodManager.isCertainFailure(ex)) {
                return new PumpEnactResult().success(false).enacted(false).comment(comment);
            } else {
                // TODO notify user about uncertain failure
                //  we don't know if the bolus failed, so for safety reasons, we choose to register the bolus as succesful.
                return new PumpEnactResult().success(true).enacted(true);
            }
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelBolus() {
        try {
            delegate.cancelBolus();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        try {
            delegate.setTemporaryBasal(tempBasalPair);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        try {
            delegate.cancelTemporaryBasal();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult acknowledgeAlerts() {
        try {
            delegate.acknowledgeAlerts();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public void setPumpStatus(OmnipodPumpStatus pumpStatus) {
        this.pumpStatus = pumpStatus;
        this.getCommunicationService().setPumpStatus(pumpStatus);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult getPodInfo(PodInfoType podInfoType) {
        try {
            // TODO how can we return the PodInfo response?
            // This method is useless unless we return the PodInfoResponse,
            // because the pod state we keep, doesn't get updated from a PodInfoResponse.
            // We use StatusResponses for that, which can be obtained from the getPodStatus method
            PodInfoResponse podInfo = delegate.getPodInfo(podInfoType);
            return new PumpEnactResult().success(true).enacted(true);
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
    }

    public PumpEnactResult suspendDelivery() {
        try {
            delegate.suspendDelivery();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult resumeDelivery() {
        try {
            delegate.resumeDelivery();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult setTime() {
        try {
            delegate.setTime();
        } catch (Exception ex) {
            String comment = handleAndTranslateException(ex);
            return new PumpEnactResult().success(false).enacted(false).comment(comment);
        }
        return new PumpEnactResult().success(true).enacted(true);
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


    private void addToHistory(long requestTime, PodDbEntryType entryType, String data, boolean success) {
        // TODO andy needs to be refactored

        //PodDbEntry entry = new PodDbEntry(requestTime, entryType);


    }

    private void handleSetupActionResult(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, SetupActionResult res) {
        String comment = null;
        switch (res.getResultType()) {
            case FAILURE:
                LOG.error("Setup action failed: illegal setup progress: {}", res.getSetupProgress());
                comment = getStringResource(R.string.omnipod_driver_error_invalid_progress_state, res.getSetupProgress());
                break;
            case VERIFICATION_FAILURE:
                if (loggingEnabled()) {
                    LOG.error("Setup action verification failed: caught exception", res.getException());
                }
                comment = getStringResource(R.string.omnipod_driver_error_setup_action_verification_failed);
                break;
        }

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
                // TODO handle pod fault with some kind of dialog that has a button to start pod deactivation
                comment = getStringResource(R.string.omnipod_driver_error_pod_fault, ((PodFaultException) ex).getFaultEvent().getFaultEventCode().name());
            } else if (ex instanceof PodReturnedErrorResponseException) {
                comment = getStringResource(R.string.omnipod_driver_error_pod_returned_error_response);
            } else {
                // Shouldn't be reachable
                comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            }
            if (loggingEnabled()) {
                LOG.error(String.format("Caught OmnipodException[certainFailure=%s] from OmnipodManager (user-friendly error message: %s)", ((OmnipodException) ex).isCertainFailure(), comment), ex);
            }
        } else {
            comment = getStringResource(R.string.omnipod_driver_error_unexpected_exception_type, ex.getClass().getName());
            if (loggingEnabled()) {
                LOG.error(String.format("Caught unexpected exception type[certainFailure=false] from OmnipodManager (user-friendly error message: %s)", comment), ex);
            }
        }

        return comment;
    }

    private String getStringResource(int id, Object... args) {
        return MainApp.gs(id, args);
    }

    private boolean loggingEnabled() {
        return L.isEnabled(L.PUMP);
    }

    // TODO add tests
    static BasalSchedule mapProfileToBasalSchedule(Profile profile) {
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for (Profile.ProfileValue basalValue : basalValues) {
            entries.add(new BasalScheduleEntry(basalValue.value, Duration.standardSeconds(basalValue.timeAsSeconds)));
        }

        return new BasalSchedule(entries);
    }
}
