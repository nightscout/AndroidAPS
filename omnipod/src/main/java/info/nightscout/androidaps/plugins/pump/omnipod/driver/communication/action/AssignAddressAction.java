package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.Random;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.AssignAddressCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class AssignAddressAction implements OmnipodAction<VersionResponse> {
    private final PodStateManager podStateManager;

    public AssignAddressAction(PodStateManager podStateManager) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("podStateManager can not be null");
        }
        this.podStateManager = podStateManager;
    }

    @Override
    public VersionResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.hasPodState()) {
            podStateManager.initState(generateRandomAddress());
        }
        if (podStateManager.isPodInitialized() && podStateManager.getPodProgressStatus().isAfter(PodProgressStatus.REMINDER_INITIALIZED)) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, podStateManager.getPodProgressStatus());
        }

        AssignAddressCommand assignAddress = new AssignAddressCommand(podStateManager.getAddress());
        OmnipodMessage assignAddressMessage = new OmnipodMessage(OmnipodConstants.DEFAULT_ADDRESS,
                Collections.singletonList(assignAddress), podStateManager.getMessageNumber());

        VersionResponse assignAddressResponse = communicationService.exchangeMessages(VersionResponse.class, podStateManager, assignAddressMessage,
                OmnipodConstants.DEFAULT_ADDRESS, podStateManager.getAddress());

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
