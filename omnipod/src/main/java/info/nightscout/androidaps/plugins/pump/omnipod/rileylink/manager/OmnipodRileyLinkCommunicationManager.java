package info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager;

import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.OmnipodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodPacket;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.ErrorResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusUpdatableResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfo;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoDetailedStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActivationTimeExceededException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageSequenceNumberException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceResyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkTimeoutException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkUnexpectedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.RileyLinkUnreachableException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;

/**
 * Created by andy on 6/29/18.
 */
@Singleton
public class OmnipodRileyLinkCommunicationManager extends RileyLinkCommunicationManager<OmnipodPacket> {

    // This empty constructor must be kept, otherwise dagger injection might break!
    @Inject
    public OmnipodRileyLinkCommunicationManager() {
    }

    @Override
    public boolean tryToConnectToDevice() {
        // TODO
        return false;
    }

    @Override
    public byte[] createPumpMessageContent(RLMessageType type) {
        return new byte[0];
    }

    @Override public boolean isDeviceReachable() {
        return false;
    }

    @Override
    public OmnipodPacket createResponseMessage(byte[] payload) {
        return new OmnipodPacket(payload);
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        // Intentionally left blank
        // We don't use PumpDeviceState in the Omnipod driver
    }

    @Override protected OmnipodPacket sendAndListen(OmnipodPacket msg, int timeout_ms, int repeatCount, int retryCount, Integer extendPreamble_ms) throws RileyLinkCommunicationException {
        return super.sendAndListen(msg, timeout_ms, repeatCount, retryCount, extendPreamble_ms);
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, PodStateManager podStateManager, MessageBlock command) {
        return sendCommand(responseClass, podStateManager, command, true);
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, PodStateManager podStateManager, MessageBlock command, boolean automaticallyResyncNone) {
        OmnipodMessage message = new OmnipodMessage(podStateManager.getAddress(), Collections.singletonList(command), podStateManager.getMessageNumber());
        return exchangeMessages(responseClass, podStateManager, message, automaticallyResyncNone);
    }

    // Convenience method
    public <T> T executeAction(OmnipodAction<T> action) {
        return action.execute(this);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodStateManager podStateManager, OmnipodMessage message) {
        return exchangeMessages(responseClass, podStateManager, message, true);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodStateManager podStateManager, OmnipodMessage message, boolean automaticallyResyncNonce) {
        return exchangeMessages(responseClass, podStateManager, message, null, null, automaticallyResyncNonce);
    }

    public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        return exchangeMessages(responseClass, podStateManager, message, addressOverride, ackAddressOverride, true);
    }

    public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride, boolean automaticallyResyncNonce) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Exchanging OmnipodMessage: responseClass={}, podStateManager={}, message={}, addressOverride={}, ackAddressOverride={}, automaticallyResyncNonce={}", //
                responseClass.getSimpleName(), podStateManager, message, addressOverride, ackAddressOverride, automaticallyResyncNonce);

        try {
            for (int i = 0; 2 > i; i++) {

                if (podStateManager.isPodInitialized() && message.isNonceResyncable()) {
                    podStateManager.advanceToNextNonce();
                }

                MessageBlock responseMessageBlock;
                try {
                    responseMessageBlock = transportMessages(podStateManager, message, addressOverride, ackAddressOverride);
                } catch (Exception ex) {
                    podStateManager.setLastFailedCommunication(DateTime.now());
                    throw ex;
                }

                aapsLogger.debug(LTag.PUMPBTCOMM, "Received response from the Pod [responseMessageBlock={}]", responseMessageBlock);

                if (responseMessageBlock instanceof StatusUpdatableResponse) {
                    podStateManager.updateFromResponse((StatusUpdatableResponse) responseMessageBlock, message);
                } else if (responseMessageBlock instanceof PodInfoResponse) {
                    PodInfo podInfo = ((PodInfoResponse) responseMessageBlock).getPodInfo();
                    if (podInfo instanceof StatusUpdatableResponse) {
                        podStateManager.updateFromResponse((StatusUpdatableResponse) podInfo, message);
                    }
                }

                if (responseClass.isInstance(responseMessageBlock)) {
                    podStateManager.setLastSuccessfulCommunication(DateTime.now());
                    return responseClass.cast(responseMessageBlock);
                } else {
                    if (responseMessageBlock.getType() == MessageBlockType.ERROR_RESPONSE) {
                        ErrorResponse error = (ErrorResponse) responseMessageBlock;
                        if (error.getErrorResponseCode() == ErrorResponse.ERROR_RESPONSE_CODE_BAD_NONCE) {
                            podStateManager.resyncNonce(error.getNonceSearchKey(), message.getSentNonce(), message.getSequenceNumber());
                            if (automaticallyResyncNonce) {
                                aapsLogger.warn(LTag.PUMPBTCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Resyncing nonce and retrying to send message as automaticallyResyncNonce=true");
                                message.resyncNonce(podStateManager.getCurrentNonce());
                            } else {
                                aapsLogger.warn(LTag.PUMPBTCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Not resyncing nonce as automaticallyResyncNonce=true");
                                podStateManager.setLastFailedCommunication(DateTime.now());
                                throw new NonceOutOfSyncException();
                            }
                        } else {
                            podStateManager.setLastFailedCommunication(DateTime.now());
                            throw new PodReturnedErrorResponseException(error);
                        }
                    } else if (responseMessageBlock.getType() == MessageBlockType.POD_INFO_RESPONSE && ((PodInfoResponse) responseMessageBlock).getSubType() == PodInfoType.DETAILED_STATUS) {
                        PodInfoDetailedStatus detailedStatus = (PodInfoDetailedStatus) ((PodInfoResponse) responseMessageBlock).getPodInfo();
                        if (detailedStatus.isFaulted()) {
                            // Treat as successful communication in order to prevent false positive pump unreachable alarms
                            podStateManager.setLastSuccessfulCommunication(DateTime.now());
                            throw new PodFaultException(detailedStatus);
                        } else if (detailedStatus.isActivationTimeExceeded()) {
                            // Treat as successful communication in order to prevent false positive pump unreachable alarms
                            podStateManager.setLastSuccessfulCommunication(DateTime.now());
                            throw new ActivationTimeExceededException();
                        } else {
                            // Shouldn't happen
                            podStateManager.setLastFailedCommunication(DateTime.now());
                            throw new IllegalResponseException(responseClass.getSimpleName(), responseMessageBlock.getType());
                        }
                    } else {
                        podStateManager.setLastFailedCommunication(DateTime.now());
                        throw new IllegalResponseException(responseClass.getSimpleName(), responseMessageBlock.getType());
                    }
                }
            }

            podStateManager.setLastFailedCommunication(DateTime.now());
            throw new NonceResyncException();
        } finally {
            podStateManager.storePodState();
        }

    }

    private MessageBlock transportMessages(PodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        int packetAddress = podStateManager.getAddress();
        if (addressOverride != null) {
            packetAddress = addressOverride;
        }

        if (podStateManager.getMessageNumber() != message.getSequenceNumber()) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Message number in Pod State [{}] does not match message sequence number [{}]. Setting message number in Pod State to {}", podStateManager.getMessageNumber(), message.getSequenceNumber(), message.getSequenceNumber());
            podStateManager.setMessageNumber(message.getSequenceNumber());
        }

        podStateManager.increaseMessageNumber();

        boolean firstPacket = true;
        byte[] encodedMessage;
        // this does not work well with the deactivate pod command, we somehow either
        // receive an ACK instead of a normal response, or a partial response and a communication timeout
        if (message.isNonceResyncable() && !message.containsBlock(DeactivatePodCommand.class)) {
            OmnipodMessage paddedMessage = new OmnipodMessage(message);
            // If messages are nonce resyncable, we want to distinguish between certain and uncertain failures for verification purposes
            // However, some commands (e.g. cancel delivery) are single packet command by nature. When we get a timeout with a single packet,
            // we are unsure whether or not the command was received by the pod
            // However, if we send > 1 packet, we know that the command wasn't received if we never send the subsequent packets,
            // because the last packet contains the CRC.
            // So we pad the message with get status commands to make it > packet
            paddedMessage.padWithGetStatusCommands(PacketType.PDM.getMaxBodyLength(), aapsLogger); // First packet is of type PDM
            encodedMessage = paddedMessage.getEncoded();
        } else {
            encodedMessage = message.getEncoded();
        }

        OmnipodPacket response = null;
        while (encodedMessage.length > 0) {
            PacketType packetType = firstPacket ? PacketType.PDM : PacketType.CON;
            OmnipodPacket packet = new OmnipodPacket(packetAddress, packetType, podStateManager.getPacketNumber(), encodedMessage);
            byte[] encodedMessageInPacket = packet.getEncodedMessage();

            // getting the data remaining to be sent
            encodedMessage = ByteUtil.substring(encodedMessage, encodedMessageInPacket.length, encodedMessage.length - encodedMessageInPacket.length);
            firstPacket = false;

            try {
                // We actually ignore previous (ack) responses if it was not last packet to send
                response = exchangePackets(podStateManager, packet);
            } catch (OmnipodException ex) {
                boolean lastPacket = encodedMessage.length == 0;

                // If this is not the last packet, the message wasn't fully sent,
                // so it's impossible for the pod to have received the message
                ex.setCertainFailure(!lastPacket);

                aapsLogger.debug(LTag.PUMPBTCOMM, "Caught OmnipodException in transportMessages. Set certainFailure to {} because encodedMessage.length={}", ex.isCertainFailure(), encodedMessage.length);

                throw ex;
            }
        }

        if (response.getPacketType() == PacketType.ACK) {
            throw new IllegalPacketTypeException(null, PacketType.ACK);
        }

        OmnipodMessage receivedMessage = null;
        byte[] receivedMessageData = response.getEncodedMessage();
        while (receivedMessage == null) {
            try {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Attempting to decode message: {}", ByteUtil.shortHexStringWithoutSpaces(receivedMessageData));
                receivedMessage = OmnipodMessage.decodeMessage(receivedMessageData);
                if (receivedMessage.getAddress() != message.getAddress()) {
                    throw new IllegalMessageAddressException(message.getAddress(), receivedMessage.getAddress());
                }
                if (receivedMessage.getSequenceNumber() != podStateManager.getMessageNumber()) {
                    throw new IllegalMessageSequenceNumberException(podStateManager.getMessageNumber(), receivedMessage.getSequenceNumber());
                }
            } catch (NotEnoughDataException ex) {
                // Message is (probably) not complete yet

                aapsLogger.debug(LTag.PUMPBTCOMM, "Caught NotEnoughDataException. Sending ACK for CON");

                OmnipodPacket ackForCon = createAckPacket(podStateManager, packetAddress, ackAddressOverride);

                OmnipodPacket conPacket = exchangePackets(podStateManager, ackForCon, 3, 40);
                if (conPacket.getPacketType() != PacketType.CON) {
                    throw new IllegalPacketTypeException(PacketType.CON, conPacket.getPacketType());
                }
                receivedMessageData = ByteUtil.concat(receivedMessageData, conPacket.getEncodedMessage());
            }
        }

        ackUntilQuiet(podStateManager, packetAddress, ackAddressOverride);

        List<MessageBlock> messageBlocks = receivedMessage.getMessageBlocks();

        if (messageBlocks.size() == 0) {
            throw new NotEnoughDataException(receivedMessageData);
        } else if (messageBlocks.size() > 1) {
            // BS: don't expect this to happen
            aapsLogger.error(LTag.PUMPBTCOMM, "Received more than one message block: {}", messageBlocks.toString());
        }

        MessageBlock messageBlock = messageBlocks.get(0);

        podStateManager.increaseMessageNumber();

        return messageBlock;
    }

    private OmnipodPacket createAckPacket(PodStateManager podStateManager, Integer packetAddress, Integer messageAddress) {
        if (packetAddress == null) {
            packetAddress = podStateManager.getAddress();
        }
        if (messageAddress == null) {
            messageAddress = podStateManager.getAddress();
        }
        return new OmnipodPacket(packetAddress, PacketType.ACK, podStateManager.getPacketNumber(), ByteUtil.getBytesFromInt(messageAddress));
    }

    private void ackUntilQuiet(PodStateManager podStateManager, Integer packetAddress, Integer messageAddress) {
        OmnipodPacket ack = createAckPacket(podStateManager, packetAddress, messageAddress);
        boolean quiet = false;
        while (!quiet) try {
            sendAndListen(ack, 300, 1, 0, 40);
        } catch (RileyLinkCommunicationException ex) {
            if (RileyLinkBLEError.Timeout.equals(ex.getErrorCode())) {
                quiet = true;
            } else {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in ackUntilQuiet", ex);
            }
        } catch (OmnipodException ex) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in ackUntilQuiet", ex);
        }

        podStateManager.increasePacketNumber();
    }

    private OmnipodPacket exchangePackets(PodStateManager podStateManager, OmnipodPacket packet) {
        return exchangePackets(podStateManager, packet, 0, 333, 9000, 127);
    }

    private OmnipodPacket exchangePackets(PodStateManager podStateManager, OmnipodPacket packet, int repeatCount, int preambleExtensionMilliseconds) {
        return exchangePackets(podStateManager, packet, repeatCount, 333, 9000, preambleExtensionMilliseconds);
    }

    private OmnipodPacket exchangePackets(PodStateManager podStateManager, OmnipodPacket packet, int repeatCount, int responseTimeoutMilliseconds, int exchangeTimeoutMilliseconds, int preambleExtensionMilliseconds) {
        long timeoutTime = System.currentTimeMillis() + exchangeTimeoutMilliseconds;

        podStateManager.increasePacketNumber();

        boolean gotResponseFromRileyLink = false;

        while (System.currentTimeMillis() < timeoutTime) {
            OmnipodPacket response;
            try {
                response = sendAndListen(packet, responseTimeoutMilliseconds, repeatCount, 9, preambleExtensionMilliseconds);
                gotResponseFromRileyLink = true;
            } catch (RileyLinkCommunicationException ex) {
                if (ex.getErrorCode() != RileyLinkBLEError.NoResponse) {
                    gotResponseFromRileyLink = true;
                }
                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                continue;
            } catch (OmnipodException ex) {
                gotResponseFromRileyLink = true;
                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                continue;
            } catch (Exception ex) {
                throw new RileyLinkUnexpectedException(ex);
            }

            if (!response.isValid()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "exchangePackets response is invalid: " + response);
                continue;
            }

            if (response.getAddress() != packet.getAddress() &&
                    response.getAddress() != OmnipodConstants.DEFAULT_ADDRESS) { // In some (strange) cases, the Pod remains a packet address of 0xffffffff during it's lifetime
                aapsLogger.debug(LTag.PUMPBTCOMM, "Packet address " + response.getAddress() + " doesn't match " + packet.getAddress());
                continue;
            }

            if (response.getSequenceNumber() != podStateManager.getPacketNumber()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Packet sequence number " + response.getSequenceNumber() + " does not match " + podStateManager.getPacketNumber());
                continue;
            }

            // Once we have verification that the POD heard us, we can increment our counters
            podStateManager.increasePacketNumber();

            return response;
        }

        if (gotResponseFromRileyLink) {
            throw new RileyLinkTimeoutException();
        }

        throw new RileyLinkUnreachableException();
    }
}
