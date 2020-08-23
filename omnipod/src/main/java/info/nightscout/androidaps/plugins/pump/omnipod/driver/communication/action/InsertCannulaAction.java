package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.InsertCannulaService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;

public class InsertCannulaAction implements OmnipodAction<StatusResponse> {

    private final PodStateManager podStateManager;
    private final InsertCannulaService service;
    private final BasalSchedule initialBasalSchedule;

    public InsertCannulaAction(InsertCannulaService insertCannulaService, PodStateManager podStateManager, BasalSchedule initialBasalSchedule) {
        if (insertCannulaService == null) {
            throw new ActionInitializationException("Insert cannula service cannot be null");
        }
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (initialBasalSchedule == null) {
            throw new ActionInitializationException("Initial basal schedule cannot be null");
        }
        this.service = insertCannulaService;
        this.podStateManager = podStateManager;
        this.initialBasalSchedule = initialBasalSchedule;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PRIMING_COMPLETED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }

        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.BASAL_INITIALIZED)) {
            service.programInitialBasalSchedule(communicationService, podStateManager, initialBasalSchedule);
        }

        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.INSERTING_CANNULA)) {
            service.executeExpirationRemindersAlertCommand(communicationService, podStateManager);
            return service.executeInsertionBolusCommand(communicationService, podStateManager);
        } else if (podStateManager.getPodProgressStatus().equals(PodProgressStatus.INSERTING_CANNULA)) {
            // Check status
            return communicationService.executeAction(new GetStatusAction(podStateManager));
        } else {
            throw new IllegalPodProgressException(null, podStateManager.getPodProgressStatus());
        }
    }
}
