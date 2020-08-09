package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.InsertCannulaService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
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

    public static void updateCannulaInsertionStatus(PodStateManager podStateManager, StatusResponse statusResponse, AAPSLogger aapsLogger) {
        if (podStateManager.getSetupProgress().equals(SetupProgress.CANNULA_INSERTING) &&
                statusResponse.getPodProgressStatus().isReadyForDelivery()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Updating SetupProgress from CANNULA_INSERTING to COMPLETED");
            podStateManager.setSetupProgress(SetupProgress.COMPLETED);
        }
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.isPaired() || podStateManager.getSetupProgress().isBefore(SetupProgress.PRIMING_FINISHED)) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_FINISHED, podStateManager.getSetupProgress());
        }

        if (podStateManager.getSetupProgress().isBefore(SetupProgress.INITIAL_BASAL_SCHEDULE_SET)) {
            service.programInitialBasalSchedule(communicationService, podStateManager, initialBasalSchedule);
            podStateManager.setSetupProgress(SetupProgress.INITIAL_BASAL_SCHEDULE_SET);
        }
        if (podStateManager.getSetupProgress().isBefore(SetupProgress.STARTING_INSERT_CANNULA)) {
            service.executeExpirationRemindersAlertCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.STARTING_INSERT_CANNULA);
        }

        if (podStateManager.getSetupProgress().isBefore(SetupProgress.CANNULA_INSERTING)) {
            StatusResponse statusResponse = service.executeInsertionBolusCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.CANNULA_INSERTING);
            return statusResponse;
        } else if (podStateManager.getSetupProgress().equals(SetupProgress.CANNULA_INSERTING)) {
            // Check status
            StatusResponse statusResponse = communicationService.executeAction(new GetStatusAction(podStateManager));
            updateCannulaInsertionStatus(podStateManager, statusResponse, communicationService.aapsLogger);
            return statusResponse;
        } else {
            throw new IllegalSetupProgressException(null, podStateManager.getSetupProgress());
        }
    }
}
