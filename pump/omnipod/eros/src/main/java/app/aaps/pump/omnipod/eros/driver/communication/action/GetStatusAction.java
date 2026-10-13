package app.aaps.pump.omnipod.eros.driver.communication.action;

import app.aaps.pump.omnipod.eros.driver.communication.message.command.GetStatusCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class GetStatusAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;

    public GetStatusAction(ErosPodStateManager podState) {
        if (podState == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        this.podStateManager = podState;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podStateManager, new GetStatusCommand(PodInfoType.NORMAL));
    }
}
