package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
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

    public static void updatePrimingStatus(PodStateManager podStateManager, StatusResponse statusResponse, AAPSLogger aapsLogger) {
        if (podStateManager.getSetupProgress().equals(SetupProgress.PRIMING) && statusResponse.getPodProgressStatus().equals(PodProgressStatus.PRIMING_COMPLETED)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Updating SetupProgress from PRIMING to PRIMING_FINISHED");
            podStateManager.setSetupProgress(SetupProgress.PRIMING_FINISHED);
        }
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (podStateManager.getSetupProgress().isBefore(SetupProgress.POD_CONFIGURED)) {
            throw new IllegalSetupProgressException(SetupProgress.POD_CONFIGURED, podStateManager.getSetupProgress());
        }
        if (podStateManager.getSetupProgress().isBefore(SetupProgress.STARTING_PRIME)) {
            service.executeDisableTab5Sub16FaultConfigCommand(communicationService, podStateManager);
            service.executeFinishSetupReminderAlertCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.STARTING_PRIME);
        }

        if (podStateManager.getSetupProgress().isBefore(SetupProgress.PRIMING)) {
            StatusResponse statusResponse = service.executePrimeBolusCommand(communicationService, podStateManager);
            podStateManager.setSetupProgress(SetupProgress.PRIMING);
            return statusResponse;
        } else if (podStateManager.getSetupProgress().equals(SetupProgress.PRIMING)) {
            // Check status
            StatusResponse statusResponse = communicationService.executeAction(new GetStatusAction(podStateManager));
            updatePrimingStatus(podStateManager, statusResponse, communicationService.aapsLogger);
            return statusResponse;
        } else {
            throw new IllegalSetupProgressException(null, podStateManager.getSetupProgress());
        }
    }
}
