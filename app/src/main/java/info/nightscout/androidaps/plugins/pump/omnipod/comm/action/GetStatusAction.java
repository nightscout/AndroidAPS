package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

public class GetStatusAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;

    public GetStatusAction(PodSessionState podState) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        this.podState = podState;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podState, new GetStatusCommand(PodInfoType.NORMAL));
    }
}
