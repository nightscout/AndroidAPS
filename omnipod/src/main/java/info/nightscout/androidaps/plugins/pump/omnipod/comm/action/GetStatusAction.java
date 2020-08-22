package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class GetStatusAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;

    public GetStatusAction(PodStateManager podState) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        this.podStateManager = podState;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podStateManager, new GetStatusCommand(PodInfoType.NORMAL));
    }
}
