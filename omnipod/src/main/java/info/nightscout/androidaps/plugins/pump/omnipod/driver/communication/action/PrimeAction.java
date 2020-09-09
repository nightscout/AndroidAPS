package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

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
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PAIRING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_COMPLETED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }
        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING)) {
            // FaultConfigCommand sets internal pod variables to effectively disable $6x faults which occur more often with a 0 TBR
            service.executeDisableTab5Sub16And17FaultConfigCommand(communicationService, podStateManager);

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
