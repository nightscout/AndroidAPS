package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class GetStatusAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;

    public GetStatusAction(PodStateManager podState) {
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
