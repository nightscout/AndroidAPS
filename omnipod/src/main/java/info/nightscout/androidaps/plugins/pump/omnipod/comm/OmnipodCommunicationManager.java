package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.OmnipodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalMessageSequenceNumberException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.IllegalResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NonceResyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodReturnedErrorResponseException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodPacket;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.ErrorResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

/**
 * Created by andy on 6/29/18.
 */
// TODO rename to OmnipodRileyLinkCommunicationManager
@Singleton
public class OmnipodCommunicationManager extends RileyLinkCommunicationManager {

    @Inject OmnipodPumpStatus omnipodPumpStatus;

    // This empty constructor must be kept, otherwise dagger injection might break!
    @Inject
    public OmnipodCommunicationManager() {
    }

    @Inject
    public void onInit() {
        // this cannot be done in the constructor, as sp is not populated at that time
        omnipodPumpStatus.previousConnection = sp.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
    }

//    @Override
//    protected void configurePumpSpecificSettings() {
//    }

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
    public RLMessage createResponseMessage(byte[] payload) {
        return new OmnipodPacket(payload);
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.omnipodPumpStatus.setPumpDeviceState(pumpDeviceState);
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

        aapsLogger.debug(LTag.PUMPCOMM, "Exchanging OmnipodMessage [responseClass={}, podStateManager={}, message={}, addressOverride={}, ackAddressOverride={}, automaticallyResyncNonce={}]: {}", //
                responseClass.getSimpleName(), podStateManager, message, addressOverride, ackAddressOverride, automaticallyResyncNonce);

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

            aapsLogger.debug(LTag.PUMPCOMM, "Received response from the Pod [responseMessageBlock={}]", responseMessageBlock);

            if (responseMessageBlock instanceof StatusResponse) {
                podStateManager.updateFromStatusResponse((StatusResponse) responseMessageBlock);
            }

            if (responseClass.isInstance(responseMessageBlock)) {
                podStateManager.setLastSuccessfulCommunication(DateTime.now());
                return (T) responseMessageBlock;
            } else {
                if (responseMessageBlock.getType() == MessageBlockType.ERROR_RESPONSE) {
                    ErrorResponse error = (ErrorResponse) responseMessageBlock;
                    if (error.getErrorResponseCode() == ErrorResponse.ERROR_RESPONSE_CODE_BAD_NONCE) {
                        podStateManager.resyncNonce(error.getNonceSearchKey(), message.getSentNonce(), message.getSequenceNumber());
                        if (automaticallyResyncNonce) {
                            aapsLogger.warn(LTag.PUMPCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Resyncing nonce and retrying to send message as automaticallyResyncNonce=true");
                            message.resyncNonce(podStateManager.getCurrentNonce());
                        } else {
                            aapsLogger.warn(LTag.PUMPCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Not resyncing nonce as automaticallyResyncNonce=true");
                            podStateManager.setLastFailedCommunication(DateTime.now());
                            throw new NonceOutOfSyncException();
                        }
                    } else {
                        podStateManager.setLastFailedCommunication(DateTime.now());
                        throw new PodReturnedErrorResponseException(error);
                    }
                } else if (responseMessageBlock.getType() == MessageBlockType.POD_INFO_RESPONSE && ((PodInfoResponse) responseMessageBlock).getSubType() == PodInfoType.FAULT_EVENT) {
                    PodInfoFaultEvent faultEvent = ((PodInfoResponse) responseMessageBlock).getPodInfo();
                    podStateManager.setFaultEvent(faultEvent);
                    podStateManager.setLastFailedCommunication(DateTime.now());
                    throw new PodFaultException(faultEvent);
                } else {
                    podStateManager.setLastFailedCommunication(DateTime.now());
                    throw new IllegalResponseException(responseClass.getSimpleName(), responseMessageBlock.getType());
                }
            }
        }

        podStateManager.setLastFailedCommunication(DateTime.now());
        throw new NonceResyncException();
    }

    private MessageBlock transportMessages(PodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        int packetAddress = podStateManager.getAddress();
        if (addressOverride != null) {
            packetAddress = addressOverride;
        }

        if (podStateManager.getMessageNumber() != message.getSequenceNumber()) {
            aapsLogger.warn(LTag.PUMPCOMM, "Message number in Pod State [{}] does not match message sequence number [{}]. Setting message number in Pod State to {}", podStateManager.getMessageNumber(), message.getSequenceNumber(), message.getSequenceNumber());
            podStateManager.setMessageNumber(message.getSequenceNumber());
        }

        podStateManager.increaseMessageNumber();

        boolean firstPacket = true;
        byte[] encodedMessage;
        // this does not work well with the deactivate pod command, we somehow either
        // receive an ACK instead of a normal response, or a partial response and a communication timeout
        if (message.isNonceResyncable() && !message.containsBlock(DeactivatePodCommand.class)) {
            OmnipodMessage paddedMessage = new OmnipodMessage(message);
            // If messages are nonce resyncable, we want do distinguish between certain and uncertain failures for verification purposes
            // However, some commands (e.g. cancel delivery) are single packet command by nature. When we get a timeout with a single packet,
            // we are unsure whether or not the command was received by the pod
            // However, if we send > 1 packet, we know that the command wasn't received if we never send the subsequent packets,
            // because the last packet contains the CRC.
            // So we pad the message with get status commands to make it > packet
            paddedMessage.padWithGetStatusCommands(PacketType.PDM.getMaxBodyLength()); // First packet is of type PDM
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
            } catch (Exception ex) {
                OmnipodException newException;
                if (ex instanceof OmnipodException) {
                    newException = (OmnipodException) ex;
                } else {
                    newException = new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex);
                }

                boolean lastPacket = encodedMessage.length == 0;

                // If this is not the last packet, the message wasn't fully sent,
                // so it's impossible for the pod to have received the message
                newException.setCertainFailure(!lastPacket);

                aapsLogger.debug(LTag.PUMPCOMM, "Caught exception in transportMessages. Set certainFailure to {} because encodedMessage.length={}", newException.isCertainFailure(), encodedMessage.length);

                throw newException;
            }
        }

        if (response.getPacketType() == PacketType.ACK) {
            throw new IllegalPacketTypeException(null, PacketType.ACK);
        }

        OmnipodMessage receivedMessage = null;
        byte[] receivedMessageData = response.getEncodedMessage();
        while (receivedMessage == null) {
            try {
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

                try {
                    OmnipodPacket conPacket = exchangePackets(podStateManager, ackForCon, 3, 40);
                    if (conPacket.getPacketType() != PacketType.CON) {
                        throw new IllegalPacketTypeException(PacketType.CON, conPacket.getPacketType());
                    }
                    receivedMessageData = ByteUtil.concat(receivedMessageData, conPacket.getEncodedMessage());
                } catch (OmnipodException ex2) {
                    throw ex2;
                } catch (Exception ex2) {
                    throw new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex2);
                }

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
        } catch (Exception ex) {
            throw new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex);
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

        while (System.currentTimeMillis() < timeoutTime) {
            OmnipodPacket response = null;
            try {
                response = (OmnipodPacket) sendAndListen(packet, responseTimeoutMilliseconds, repeatCount, 9, preambleExtensionMilliseconds);
            } catch (RileyLinkCommunicationException | OmnipodException ex) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                continue;
            } catch (Exception ex) {
                throw new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex);
            }

            if (response == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "exchangePackets response is null");
                continue;
            } else if (!response.isValid()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "exchangePackets response is invalid: " + response);
                continue;
            }
            if (response.getAddress() != packet.getAddress() &&
                    response.getAddress() != OmnipodConst.DEFAULT_ADDRESS) { // In some (strange) cases, the Pod remains a packet address of 0xffffffff during it's lifetime
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
        throw new CommunicationException(CommunicationException.Type.TIMEOUT);
    }

}
