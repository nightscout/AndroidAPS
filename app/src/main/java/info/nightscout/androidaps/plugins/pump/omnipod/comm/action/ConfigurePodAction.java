package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTime;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.ConfigurePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class ConfigurePodAction implements OmnipodAction<VersionResponse> {
    private final PodSessionState podState;

    public ConfigurePodAction(PodSessionState podState) {
        this.podState = podState;
    }

    @Override
    public VersionResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podState.getSetupProgress().equals(SetupProgress.ADDRESS_ASSIGNED)) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, podState.getSetupProgress());
        }
        DateTime activationDate = DateTime.now(podState.getTimeZone());

        ConfigurePodCommand configurePodCommand = new ConfigurePodCommand(podState.getAddress(), activationDate,
                podState.getLot(), podState.getTid());
        OmnipodMessage message = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(configurePodCommand), podState.getMessageNumber());
        VersionResponse configurePodResponse;
        try {
            configurePodResponse = communicationService.exchangeMessages(VersionResponse.class, podState,
                    message, OmnipodConst.DEFAULT_ADDRESS, podState.getAddress());
        } catch (IllegalPacketTypeException ex) {
            if (PacketType.ACK.equals(ex.getActual())) {
                // Pod is already configured
                podState.setSetupProgress(SetupProgress.POD_CONFIGURED);
                return null;
            }
            throw ex;
        }

        if (configurePodResponse.getPodProgressStatus() != PodProgressStatus.PAIRING_SUCCESS) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_SUCCESS, configurePodResponse.getPodProgressStatus());
        }

        podState.setSetupProgress(SetupProgress.POD_CONFIGURED);

        return configurePodResponse;
    }
}
