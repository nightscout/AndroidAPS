package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTime;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetupPodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class SetupPodAction implements OmnipodAction<VersionResponse> {
    private final PodSessionState podState;

    public SetupPodAction(PodSessionState podState) {
        this.podState = podState;
    }

    @Override
    public VersionResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podState.getSetupProgress().equals(SetupProgress.ADDRESS_ASSIGNED)) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, podState.getSetupProgress());
        }
        DateTime activationDate = DateTime.now(podState.getTimeZone());

        SetupPodCommand setupPodCommand = new SetupPodCommand(podState.getAddress(), activationDate,
                podState.getLot(), podState.getTid());
        OmnipodMessage message = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(setupPodCommand), podState.getMessageNumber());
        VersionResponse setupPodResponse;
        try {
            setupPodResponse = communicationService.exchangeMessages(VersionResponse.class, podState,
                    message, OmnipodConst.DEFAULT_ADDRESS, podState.getAddress());
        } catch (IllegalPacketTypeException ex) {
            if (PacketType.ACK.equals(ex.getActual())) {
                // Pod is already configured
                podState.setSetupProgress(SetupProgress.POD_CONFIGURED);
                return null;
            }
            throw ex;
        }

        if (!setupPodResponse.isSetupPodVersionResponse()) {
            throw new IllegalVersionResponseTypeException("setupPod", "assignAddress");
        }
        if (setupPodResponse.getAddress() != podState.getAddress()) {
            throw new IllegalMessageAddressException(podState.getAddress(), setupPodResponse.getAddress());
        }
        if (setupPodResponse.getPodProgressStatus() != PodProgressStatus.PAIRING_COMPLETED) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_COMPLETED, setupPodResponse.getPodProgressStatus());
        }

        podState.setSetupProgress(SetupProgress.POD_CONFIGURED);

        return setupPodResponse;
    }
}
