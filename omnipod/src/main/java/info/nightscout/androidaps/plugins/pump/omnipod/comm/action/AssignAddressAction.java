package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.Random;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.AssignAddressCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class AssignAddressAction implements OmnipodAction<VersionResponse> {
    private final PodStateManager podStateManager;

    public AssignAddressAction(PodStateManager podStateManager) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("podStateManager can not be null");
        }
        this.podStateManager = podStateManager;
    }

    @Override
    public VersionResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.hasPodState()) {
            podStateManager.initState(generateRandomAddress());
        }
        if (podStateManager.isPodInitialized() && podStateManager.getPodProgressStatus().isAfter(PodProgressStatus.REMINDER_INITIALIZED)) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, podStateManager.getPodProgressStatus());
        }

        AssignAddressCommand assignAddress = new AssignAddressCommand(podStateManager.getAddress());
        OmnipodMessage assignAddressMessage = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(assignAddress), podStateManager.getMessageNumber());

        VersionResponse assignAddressResponse = communicationService.exchangeMessages(VersionResponse.class, podStateManager, assignAddressMessage,
                OmnipodConst.DEFAULT_ADDRESS, podStateManager.getAddress());

        if (!assignAddressResponse.isAssignAddressVersionResponse()) {
            throw new IllegalVersionResponseTypeException("assignAddress", "setupPod");
        }
        if (assignAddressResponse.getAddress() != podStateManager.getAddress()) {
            throw new IllegalMessageAddressException(podStateManager.getAddress(), assignAddressResponse.getAddress());
        }

        podStateManager.setInitializationParameters(assignAddressResponse.getLot(), assignAddressResponse.getTid(), //
                assignAddressResponse.getPiVersion(), assignAddressResponse.getPmVersion(), DateTimeZone.getDefault(), assignAddressResponse.getPodProgressStatus());

        return assignAddressResponse;
    }

    private static int generateRandomAddress() {
        // Create random address with 20 bits to match PDM, could easily use 24 bits instead
        return 0x1f000000 | (new Random().nextInt() & 0x000fffff);
    }
}
