package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
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
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {
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
                        podInitReceiver.returnInitTaskStatus(podInitActionType, res.getResultType().isSuccess(), createCommentForSetupActionResult(res)));
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                // TODO use string resource instead of exception message
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, ex.getMessage());
                return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
            }
        } else if (PodInitActionType.FillCannulaSetBasalProfileWizardStep.equals(podInitActionType)) {
            try {
                delegate.insertCannula(mapProfileToBasalSchedule(profile), res -> {
                    podInitReceiver.returnInitTaskStatus(podInitActionType, res.getResultType().isSuccess(), createCommentForSetupActionResult(res));
                    OmnipodUtil.setPodSessionState(delegate.getPodState());
                    RxBus.INSTANCE.send(new EventOmnipodPumpValuesChanged());
                });
                return new PumpEnactResult().success(true).enacted(true);
            } catch (Exception ex) {
                // TODO use string resource instead of exception message
                podInitReceiver.returnInitTaskStatus(podInitActionType, false, ex.getMessage());
                return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
            }
        }
        // Todo use string resource
        return new PumpEnactResult().success(false).enacted(false).comment("Illegal PodInitActionType: " + podInitActionType.name());
    }

    @Override
    public PumpEnactResult getPodStatus() {
        try {
            StatusResponse statusResponse = delegate.getPodStatus();
            return new PumpEnactResult().success(true).enacted(false);
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        try {
            delegate.deactivatePod();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, ex.getMessage());
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
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
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
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
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelBolus() {
        try {
            delegate.cancelBolus();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        try {
            delegate.setTemporaryBasal(tempBasalPair);
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        try {
            delegate.cancelTemporaryBasal();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public PumpEnactResult acknowledgeAlerts() {
        try {
            delegate.acknowledgeAlerts();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
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
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }
    }

    public PumpEnactResult suspendDelivery() {
        try {
            delegate.suspendDelivery();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    public PumpEnactResult resumeDelivery() {
        try {
            delegate.resumeDelivery();
        } catch (Exception ex) {
            // TODO use string resource instead of exception message
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult setTime() {
        try {
            delegate.setTime();
        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
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

    private String createCommentForSetupActionResult(SetupActionResult res) {
        String comment = null;
        switch (res.getResultType()) {
            case FAILURE:
                // TODO use string resource
                comment = "Unexpected setup progress: " + res.getSetupProgress();
                break;
            case VERIFICATION_FAILURE:
                // TODO use string resource
                comment = "Verification failed: " + res.getException().getClass().getSimpleName() + ": " + res.getException().getMessage();
                break;
        }
        return comment;
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
