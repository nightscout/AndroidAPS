package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class PrimeAction implements OmnipodAction<StatusResponse> {

    private final PrimeService service;
    private final PodStateManager podStateManager;

    public PrimeAction(PrimeService primeService, PodStateManager podStateManager) {
        if (primeService == null) {
            throw new ActionInitializationException("Prime service cannot be null");
        }
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        this.service = primeService;
        this.podStateManager = podStateManager;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PAIRING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_COMPLETED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }
        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING)) {
            service.executeDisableTab5Sub16FaultConfigCommand(communicationService, podStateManager);
            service.executeFinishSetupReminderAlertCommand(communicationService, podStateManager);
            return service.executePrimeBolusCommand(communicationService, podStateManager);
        } else if (podStateManager.getPodProgressStatus().equals(PodProgressStatus.PRIMING)) {
            // Check status
            return communicationService.executeAction(new GetStatusAction(podStateManager));
        } else {
            throw new IllegalPodProgressException(null, podStateManager.getPodProgressStatus());
        }
    }
}
