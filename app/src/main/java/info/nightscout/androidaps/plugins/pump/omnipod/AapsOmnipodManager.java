package info.nightscout.androidaps.plugins.pump.omnipod;

import org.joda.time.DateTime;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {
    private final OmnipodManager delegate;

    private static AapsOmnipodManager instance;

    // FIXME this is dirty
    public static AapsOmnipodManager getInstance() {
        return instance;
    }

    public AapsOmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        delegate = new OmnipodManager(communicationService, podState);
        instance = this;
    }

    @Override
    public PumpEnactResult initPod(PodInitActionType podInitActionType, PodInitReceiver podInitReceiver, Profile profile) {
        if (PodInitActionType.PairAndPrimeWizardStep.equals(podInitActionType)) {
            PumpEnactResult result = delegate.pairAndPrime();
            podInitReceiver.returnInitTaskStatus(podInitActionType, result.success, (result.success ? null : result.comment));
            return result;
        } else if (PodInitActionType.FillCannulaSetBasalProfileWizardStep.equals(podInitActionType)) {
            // FIXME we need a basal profile here
            PumpEnactResult result = delegate.insertCannula(profile);
            podInitReceiver.returnInitTaskStatus(podInitActionType, result.success, (result.success ? null : result.comment));
            return result;
        }
        return new PumpEnactResult().success(false).enacted(false).comment("Illegal PodInitActionType: " + podInitActionType.name());
    }

    @Override
    public PumpEnactResult getPodStatus() {
        return delegate.getPodStatus();
    }

    @Override
    public PumpEnactResult deactivatePod() {
        return delegate.deactivatePod();
    }

    @Override
    public PumpEnactResult setBasalProfile(Profile basalProfile) {
        return delegate.setBasalProfile(basalProfile);
    }

    @Override
    public PumpEnactResult resetPodStatus() {
        return delegate.resetPodState();
    }

    @Override
    public PumpEnactResult setBolus(Double amount) {
        return delegate.bolus(amount);
    }

    @Override
    public PumpEnactResult cancelBolus() {
        return delegate.cancelBolus();
    }

    @Override
    public PumpEnactResult setTemporaryBasal(TempBasalPair tempBasalPair) {
        return delegate.setTemporaryBasal(tempBasalPair);
    }

    @Override
    public PumpEnactResult cancelTemporaryBasal() {
        return delegate.cancelTemporaryBasal();
    }

    @Override
    public PumpEnactResult acknowledgeAlerts() {
        return delegate.acknowledgeAlerts();
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult getPodInfo(PodInfoType podInfoType) {
        return delegate.getPodInfo(podInfoType);
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult suspendDelivery() {
        return delegate.suspendDelivery();
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult resumeDelivery() {
        return delegate.resumeDelivery();
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult setTime() {
        return delegate.setTime();
    }

    public OmnipodCommunicationService getCommunicationService() {
        return delegate.getCommunicationService();
    }

    public DateTime getTime() {
        return delegate.getTime();
    }

    public boolean isInitialized() {
        return delegate.isInitialized();
    }

    public String getPodStateAsString() {
        return delegate.getPodStateAsString();
    }
}
