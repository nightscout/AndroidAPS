package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import app.aaps.pump.omnipod.eros.driver.communication.message.command.GetStatusCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoResponse;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class GetPodInfoAction implements OmnipodAction<PodInfoResponse> {
    @NonNull private final ErosPodStateManager podStateManager;
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
    public PodInfoResponse execute(@NonNull OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(PodInfoResponse.class, podStateManager, new GetStatusCommand(podInfoType));
    }
}
