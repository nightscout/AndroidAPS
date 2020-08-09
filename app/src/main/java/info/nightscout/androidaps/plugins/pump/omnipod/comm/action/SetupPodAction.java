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
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class SetupPodAction implements OmnipodAction<VersionResponse> {
    private final PodStateManager podStateManager;

    public SetupPodAction(PodStateManager podStateManager) {
        if(podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager can not be null");
        }
        this.podStateManager = podStateManager;
    }

    @Override
    public VersionResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.getSetupProgress().equals(SetupProgress.ADDRESS_ASSIGNED)) {
            throw new IllegalSetupProgressException(SetupProgress.ADDRESS_ASSIGNED, podStateManager.getSetupProgress());
        }
        DateTime activationDate = DateTime.now(podStateManager.getTimeZone());

        SetupPodCommand setupPodCommand = new SetupPodCommand(podStateManager.getAddress(), activationDate,
                podStateManager.getLot(), podStateManager.getTid());
        OmnipodMessage message = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(setupPodCommand), podStateManager.getMessageNumber());
        VersionResponse setupPodResponse;
        try {
            setupPodResponse = communicationService.exchangeMessages(VersionResponse.class, podStateManager,
                    message, OmnipodConst.DEFAULT_ADDRESS, podStateManager.getAddress());
        } catch (IllegalPacketTypeException ex) {
            if (PacketType.ACK.equals(ex.getActual())) {
                // Pod is already configured
                podStateManager.setSetupProgress(SetupProgress.POD_CONFIGURED);
                return null;
            }
            throw ex;
        }

        if (!setupPodResponse.isSetupPodVersionResponse()) {
            throw new IllegalVersionResponseTypeException("setupPod", "assignAddress");
        }
        if (setupPodResponse.getAddress() != podStateManager.getAddress()) {
            throw new IllegalMessageAddressException(podStateManager.getAddress(), setupPodResponse.getAddress());
        }
        if (setupPodResponse.getPodProgressStatus() != PodProgressStatus.PAIRING_COMPLETED) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_COMPLETED, setupPodResponse.getPodProgressStatus());
        }

        podStateManager.setSetupProgress(SetupProgress.POD_CONFIGURED);

        return setupPodResponse;
    }
}
