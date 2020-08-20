package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.InsertCannulaService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

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
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PRIMING_COMPLETED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }

        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.BASAL_INITIALIZED)) {
            service.programInitialBasalSchedule(communicationService, podStateManager, initialBasalSchedule);
        }
        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.INSERTING_CANNULA)) {
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
