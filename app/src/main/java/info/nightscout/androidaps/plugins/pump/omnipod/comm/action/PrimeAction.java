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
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

public class PrimeAction implements OmnipodAction<StatusResponse> {

    private final PrimeService service;
    private final PodSessionState podState;

    public PrimeAction(PrimeService primeService, PodSessionState podState) {
        if (primeService == null) {
            throw new ActionInitializationException("Prime service cannot be null");
        }
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        this.service = primeService;
        this.podState = podState;
    }

    public static void updatePrimingStatus(PodSessionState podState, StatusResponse statusResponse, AAPSLogger aapsLogger) {
        if (podState.getSetupProgress().equals(SetupProgress.PRIMING) && statusResponse.getPodProgressStatus().equals(PodProgressStatus.PRIMING_COMPLETED)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Updating SetupProgress from PRIMING to PRIMING_FINISHED");
            podState.setSetupProgress(SetupProgress.PRIMING_FINISHED);
        }
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (podState.getSetupProgress().isBefore(SetupProgress.POD_CONFIGURED)) {
            throw new IllegalSetupProgressException(SetupProgress.POD_CONFIGURED, podState.getSetupProgress());
        }
        if (podState.getSetupProgress().isBefore(SetupProgress.STARTING_PRIME)) {
            service.executeDisableTab5Sub16FaultConfigCommand(communicationService, podState);
            service.executeFinishSetupReminderAlertCommand(communicationService, podState);
            podState.setSetupProgress(SetupProgress.STARTING_PRIME);
        }

        if (podState.getSetupProgress().isBefore(SetupProgress.PRIMING)) {
            StatusResponse statusResponse = service.executePrimeBolusCommand(communicationService, podState);
            podState.setSetupProgress(SetupProgress.PRIMING);
            return statusResponse;
        } else if (podState.getSetupProgress().equals(SetupProgress.PRIMING)) {
            // Check status
            StatusResponse statusResponse = communicationService.executeAction(new GetStatusAction(podState));
            updatePrimingStatus(podState, statusResponse, communicationService.aapsLogger);
            return statusResponse;
        } else {
            throw new IllegalSetupProgressException(null, podState.getSetupProgress());
        }
    }
}
