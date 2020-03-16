package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;

public class PrimeAction implements OmnipodAction<StatusResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(PrimeAction.class);

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

    public static void updatePrimingStatus(PodSessionState podState, StatusResponse statusResponse) {
        if (podState.getSetupProgress().equals(SetupProgress.PRIMING) && statusResponse.getPodProgressStatus().equals(PodProgressStatus.READY_FOR_BASAL_SCHEDULE)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Updating SetupProgress from PRIMING to PRIMING_FINISHED");
            }
            podState.setSetupProgress(SetupProgress.PRIMING_FINISHED);
        }
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
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
            updatePrimingStatus(podState, statusResponse);
            return statusResponse;
        } else {
            throw new IllegalSetupProgressException(null, podState.getSetupProgress());
        }
    }
}
