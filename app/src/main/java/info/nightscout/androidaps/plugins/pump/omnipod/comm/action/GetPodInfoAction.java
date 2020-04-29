package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

public class GetPodInfoAction implements OmnipodAction<PodInfoResponse> {
    private final PodSessionState podState;
    private final PodInfoType podInfoType;

    public GetPodInfoAction(PodSessionState podState, PodInfoType podInfoType) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (podInfoType == null) {
            throw new ActionInitializationException("Pod info type cannot be null");
        }
        this.podState = podState;
        this.podInfoType = podInfoType;
    }

    @Override
    public PodInfoResponse execute(OmnipodCommunicationManager communicationService) {
        return communicationService.sendCommand(PodInfoResponse.class, podState, new GetStatusCommand(podInfoType));
    }
}
