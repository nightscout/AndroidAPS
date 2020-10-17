package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalActivationProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class PrimeAction implements OmnipodAction<Void> {

    private final PrimeService service;
    private final PodStateManager podStateManager;

    public PrimeAction(PrimeService primeService, PodStateManager podStateManager) {
        if (primeService == null) {
            throw new IllegalArgumentException("Prime service cannot be null");
        }
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        this.service = primeService;
        this.podStateManager = podStateManager;
    }

    @Override
    public Void execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (podStateManager.getActivationProgress().isBefore(ActivationProgress.PAIRING_COMPLETED)) {
            throw new IllegalActivationProgressException(ActivationProgress.PAIRING_COMPLETED, podStateManager.getActivationProgress());
        }

        if (podStateManager.getActivationProgress().needsDisableTab5Sub16And17()) {
            // FaultConfigCommand sets internal pod variables to effectively disable $6x faults which occur more often with a 0 TBR
            service.executeDisableTab5Sub16And17FaultConfigCommand(communicationService, podStateManager);
            podStateManager.setActivationProgress(ActivationProgress.TAB_5_SUB_16_AND_17_DISABLED);
        }

        if (podStateManager.getActivationProgress().needsSetupReminders()) {
            service.executeFinishSetupReminderAlertCommand(communicationService, podStateManager);
            podStateManager.setActivationProgress(ActivationProgress.SETUP_REMINDERS_SET);
        }

        if (podStateManager.getActivationProgress().needsPriming()) {
            service.executePrimeBolusCommand(communicationService, podStateManager);
            podStateManager.setActivationProgress(ActivationProgress.PRIMING);
        }

        return null;
    }
}
