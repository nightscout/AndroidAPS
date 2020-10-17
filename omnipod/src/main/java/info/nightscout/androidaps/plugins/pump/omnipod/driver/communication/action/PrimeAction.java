package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalSetupProgressException;
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
        if (podStateManager.getSetupProgress().isBefore(SetupProgress.PAIRING_COMPLETED)) {
            throw new IllegalSetupProgressException(SetupProgress.PAIRING_COMPLETED, podStateManager.getSetupProgress());
        }

        if (podStateManager.getSetupProgress().needsDisableTab5Sub16And17()) {
            // FaultConfigCommand sets internal pod variables to effectively disable $6x faults which occur more often with a 0 TBR
            service.executeDisableTab5Sub16And17FaultConfigCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.TAB_5_SUB_16_AND_17_DISABLED);
        }

        if (podStateManager.getSetupProgress().needsSetupReminders()) {
            service.executeFinishSetupReminderAlertCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.SETUP_REMINDERS_SET);
        }

        if (podStateManager.getSetupProgress().needsPriming()) {
            service.executePrimeBolusCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.PRIMING);
        }

        return null;
    }
}
