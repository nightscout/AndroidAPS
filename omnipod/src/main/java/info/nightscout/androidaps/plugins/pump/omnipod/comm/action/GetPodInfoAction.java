package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class GetPodInfoAction implements OmnipodAction<PodInfoResponse> {
    private final PodStateManager podStateManager;
    private final PodInfoType podInfoType;

    public GetPodInfoAction(PodStateManager podStateManager, PodInfoType podInfoType) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (podInfoType == null) {
            throw new ActionInitializationException("Pod info type cannot be null");
        }
        this.podStateManager = podStateManager;
        this.podInfoType = podInfoType;
    }

    @Override
    public PodInfoResponse execute(OmnipodCommunicationManager communicationService) {
        return communicationService.sendCommand(PodInfoResponse.class, podStateManager, new GetStatusCommand(podInfoType));
    }
}
