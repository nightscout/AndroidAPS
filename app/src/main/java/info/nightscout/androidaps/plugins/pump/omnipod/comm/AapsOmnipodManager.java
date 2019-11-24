package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.OmnipodManagerAAPS;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

public class AapsOmnipodManager implements OmnipodCommunicationManagerInterface {
    private final OmnipodManagerAAPS delegate;

    private static AapsOmnipodManager instance;
    private OmnipodPumpStatus pumpStatus;

    public static AapsOmnipodManager getInstance() {
        return instance;
    }

    public AapsOmnipodManager(OmnipodCommunicationService communicationService, PodSessionState podState) {
        delegate = new OmnipodManagerAAPS(communicationService, podState);
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
            OmnipodUtil.setPodSessionState(delegate.podState);
            return result;
        }
        return new PumpEnactResult().success(false).enacted(false).comment("Illegal PodInitActionType: " + podInitActionType.name());
    }

    @Override
    public PumpEnactResult getPodStatus() {
        return delegate.getPodStatus();
    }

    @Override
    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {
        return delegate.deactivatePod(podInitReceiver);
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

    @Override
    public void setPumpStatus(OmnipodPumpStatus pumpStatus) {
        this.pumpStatus = pumpStatus;
    }

    // TODO should we add this to the OmnipodCommunicationManager interface?
    public PumpEnactResult getPodInfo(PodInfoType podInfoType) {
        return delegate.getPodInfo(podInfoType);
    }

    public PumpEnactResult suspendDelivery() {
        return delegate.suspendDelivery();
    }

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
