package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;


import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.DeactivatePodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.OmnipodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodDbEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodDbEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by andy on 24.11.2019
 */

// TODO this should be used instead of AapsOmnipodManager
public class OmnipodManagerAAPS extends OmnipodManager //implements OmnipodCommunicationManagerInterface
{

    public OmnipodManagerAAPS(OmnipodCommunicationService communicationService, PodSessionState podState) {
        super(communicationService, podState);
    }


    public PumpEnactResult deactivatePod(PodInitReceiver podInitReceiver) {

        if (podState == null) {
            // TODO use string resource
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, "Pod should be paired and primed first");

            return new PumpEnactResult().success(false).enacted(false).comment("Pod should be paired and primed first");
        }

        try {
            communicationService.executeAction(new DeactivatePodAction(podState, true));
            resetPodStateInternal();
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, true, null);

        } catch (Exception ex) {
            // TODO distinguish between certain and uncertain failures
            // TODO user friendly error messages (string resources)
            podInitReceiver.returnInitTaskStatus(PodInitActionType.DeactivatePodWizardStep, false, "Error communicating with Pod [msg=" + ex.getMessage() + "]");

            return new PumpEnactResult().success(false).enacted(false).comment(ex.getMessage());
        }

        return new PumpEnactResult().success(true).enacted(true);
    }


    public PumpEnactResult resetPodState() {
        resetPodStateInternal();

        addToHistory(System.currentTimeMillis(), PodDbEntryType.ResetPodState, null, null, null, true);

        return new PumpEnactResult().success(true).enacted(true);
    }

    private void resetPodStateInternal() {
        podState = null;
        SP.remove(OmnipodConst.Prefs.PodState);

        OmnipodUtil.setPodSessionState(null);
        RxBus.INSTANCE.send(new EventOmnipodPumpValuesChanged());
    }


    private void addToHistory(long requestTime, PodDbEntryType entryType, OmnipodAction omnipodAction, Long responseTime, Object response, boolean success) {
        // TODO andy

        PodDbEntry entry = new PodDbEntry(requestTime, entryType);

        if (omnipodAction!=null) {

        }

        if (responseTime!=null) {
            entry.setDateTimeResponse(responseTime);
        }

        if (response!=null) {

        }

    }
}
