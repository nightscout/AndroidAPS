package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class GetPodInfoAction implements OmnipodAction<PodInfoResponse> {
    private final ErosPodStateManager podStateManager;
    private final PodInfoType podInfoType;

    public GetPodInfoAction(ErosPodStateManager podStateManager, PodInfoType podInfoType) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (podInfoType == null) {
            throw new IllegalArgumentException("Pod info type cannot be null");
        }
        this.podStateManager = podStateManager;
        this.podInfoType = podInfoType;
    }

    @Override
    public PodInfoResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(PodInfoResponse.class, podStateManager, new GetStatusCommand(podInfoType));
    }
}
