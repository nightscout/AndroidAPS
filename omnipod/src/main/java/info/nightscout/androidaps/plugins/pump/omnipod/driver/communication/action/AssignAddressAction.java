package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.Random;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.AssignAddressCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class AssignAddressAction implements OmnipodAction<Void> {
    private final PodStateManager podStateManager;
    private final AAPSLogger aapsLogger;

    public AssignAddressAction(PodStateManager podStateManager, AAPSLogger aapsLogger) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("podStateManager can not be null");
        }
        if (aapsLogger == null) {
            throw new IllegalArgumentException("Logger can not be null");
        }
        this.podStateManager = podStateManager;
        this.aapsLogger = aapsLogger;
    }

    @Override
    public Void execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.hasPodState()) {
            podStateManager.initState(generateRandomAddress());
        }

        if (podStateManager.getActivationProgress().needsPairing()) {
            AssignAddressCommand assignAddress = new AssignAddressCommand(podStateManager.getAddress());
            OmnipodMessage assignAddressMessage = new OmnipodMessage(OmnipodConstants.DEFAULT_ADDRESS,
                    Collections.singletonList(assignAddress), podStateManager.getMessageNumber());

            try {
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
            } catch (IllegalPacketTypeException ex) {
                if (ex.getActual() == PacketType.ACK && podStateManager.isPodInitialized()) {
                    // When we already assigned the address before, it's possible to only get an ACK here
                    aapsLogger.debug("Received ACK instead of response in AssignAddressAction. Ignoring because we already assigned the address successfully");
                } else {
                    throw ex;
                }
            }
        }

        return null;
    }

    private static int generateRandomAddress() {
        // Create random address with 20 bits to match PDM, could easily use 24 bits instead
        return 0x1f000000 | (new Random().nextInt() & 0x000fffff);
    }
}
