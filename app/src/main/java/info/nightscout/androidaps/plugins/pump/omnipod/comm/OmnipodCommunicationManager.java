package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.OmnipodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommunicationException;
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
import info.nightscout.androidaps.plugins.pump.omnipod.defs.ErrorResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

/**
 * Created by andy on 6/29/18.
 */
public class OmnipodCommunicationManager extends RileyLinkCommunicationManager {

    @Inject public AAPSLogger aapsLogger;
    @Inject OmnipodPumpStatus omnipodPumpStatus;
    @Inject OmnipodPumpPlugin omnipodPumpPlugin;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject ServiceTaskExecutor serviceTaskExecutor;

    public OmnipodCommunicationManager(HasAndroidInjector injector, RFSpy rfspy) {
        super(injector, rfspy);
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

    @Override public PumpStatus getPumpStatus() {
        return null;
    }

    @Override public boolean isDeviceReachable() {
        return false;
    }

    @Override
    public boolean hasTunning() {
        return false;
    }

    @Override
    public <E extends RLMessage> E createResponseMessage(byte[] payload, Class<E> clazz) {
        return (E) new OmnipodPacket(payload);
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.omnipodPumpStatus.setPumpDeviceState(pumpDeviceState);
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, PodState podState, MessageBlock command) {
        return sendCommand(responseClass, podState, command, true);
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, PodState podState, MessageBlock command, boolean automaticallyResyncNone) {
        OmnipodMessage message = new OmnipodMessage(podState.getAddress(), Collections.singletonList(command), podState.getMessageNumber());
        return exchangeMessages(responseClass, podState, message, automaticallyResyncNone);
    }

    // Convenience method
    public <T> T executeAction(OmnipodAction<T> action) {
        return action.execute(this);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodState podState, OmnipodMessage message) {
        return exchangeMessages(responseClass, podState, message, true);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodState podState, OmnipodMessage message, boolean automaticallyResyncNonce) {
        return exchangeMessages(responseClass, podState, message, null, null, automaticallyResyncNonce);
    }

    public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodState podState, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        return exchangeMessages(responseClass, podState, message, addressOverride, ackAddressOverride, true);
    }

    public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, PodState podState, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride, boolean automaticallyResyncNonce) {

        aapsLogger.debug(LTag.PUMPCOMM, "Exchanging OmnipodMessage [responseClass={}, podState={}, message={}, addressOverride={}, ackAddressOverride={}, automaticallyResyncNonce={}]: {}", //
                responseClass.getSimpleName(), podState, message, addressOverride, ackAddressOverride, automaticallyResyncNonce, message);

        for (int i = 0; 2 > i; i++) {

            if (podState.hasNonceState() && message.isNonceResyncable()) {
                podState.advanceToNextNonce();
            }

            MessageBlock responseMessageBlock = transportMessages(podState, message, addressOverride, ackAddressOverride);

            if (responseMessageBlock instanceof StatusResponse) {
                podState.updateFromStatusResponse((StatusResponse) responseMessageBlock);
            }

            if (responseClass.isInstance(responseMessageBlock)) {
                return (T) responseMessageBlock;
            } else {
                if (responseMessageBlock.getType() == MessageBlockType.ERROR_RESPONSE) {
                    ErrorResponse error = (ErrorResponse) responseMessageBlock;
                    if (error.getErrorResponseType() == ErrorResponseType.BAD_NONCE) {
                        podState.resyncNonce(error.getNonceSearchKey(), message.getSentNonce(), message.getSequenceNumber());
                        if (automaticallyResyncNonce) {
                            message.resyncNonce(podState.getCurrentNonce());
                        } else {
                            throw new NonceOutOfSyncException();
                        }
                    } else {
                        throw new PodReturnedErrorResponseException((ErrorResponse) responseMessageBlock);
                    }
                } else if (responseMessageBlock.getType() == MessageBlockType.POD_INFO_RESPONSE && ((PodInfoResponse) responseMessageBlock).getSubType() == PodInfoType.FAULT_EVENT) {
                    PodInfoFaultEvent faultEvent = ((PodInfoResponse) responseMessageBlock).getPodInfo();
                    podState.setFaultEvent(faultEvent);
                    throw new PodFaultException(faultEvent);
                } else {
                    throw new IllegalResponseException(responseClass.getSimpleName(), responseMessageBlock.getType());
                }
            }
        }

        throw new NonceResyncException();
    }

    private MessageBlock transportMessages(PodState podState, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        int packetAddress = podState.getAddress();
        if (addressOverride != null) {
            packetAddress = addressOverride;
        }

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
            OmnipodPacket packet = new OmnipodPacket(packetAddress, packetType, podState.getPacketNumber(), encodedMessage);
            byte[] encodedMessageInPacket = packet.getEncodedMessage();

            // getting the data remaining to be sent
            encodedMessage = ByteUtil.substring(encodedMessage, encodedMessageInPacket.length, encodedMessage.length - encodedMessageInPacket.length);
            firstPacket = false;

            try {
                // We actually ignore previous (ack) responses if it was not last packet to send
                response = exchangePackets(podState, packet);
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
            podState.increasePacketNumber(1);
            throw new IllegalPacketTypeException(null, PacketType.ACK);
        }

        OmnipodMessage receivedMessage = null;
        byte[] receivedMessageData = response.getEncodedMessage();
        while (receivedMessage == null) {
            try {
                receivedMessage = OmnipodMessage.decodeMessage(receivedMessageData);
            } catch (NotEnoughDataException ex) {
                // Message is (probably) not complete yet
                OmnipodPacket ackForCon = createAckPacket(podState, packetAddress, ackAddressOverride);

                try {
                    OmnipodPacket conPacket = exchangePackets(podState, ackForCon, 3, 40);
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

        podState.increaseMessageNumber(2);

        ackUntilQuiet(podState, packetAddress, ackAddressOverride);

        List<MessageBlock> messageBlocks = receivedMessage.getMessageBlocks();

        if (messageBlocks.size() == 0) {
            throw new NotEnoughDataException(receivedMessageData);
        } else if (messageBlocks.size() > 1) {
            // BS: don't expect this to happen
            aapsLogger.error(LTag.PUMPBTCOMM, "Received more than one message block: {}", messageBlocks.toString());
        }

        return messageBlocks.get(0);
    }

    private OmnipodPacket createAckPacket(PodState podState, Integer packetAddress, Integer messageAddress) {
        int pktAddress = podState.getAddress();
        int msgAddress = podState.getAddress();
        if (packetAddress != null) {
            pktAddress = packetAddress;
        }
        if (messageAddress != null) {
            msgAddress = messageAddress;
        }
        return new OmnipodPacket(pktAddress, PacketType.ACK, podState.getPacketNumber(), ByteUtil.getBytesFromInt(msgAddress));
    }

    private void ackUntilQuiet(PodState podState, Integer packetAddress, Integer messageAddress) {
        OmnipodPacket ack = createAckPacket(podState, packetAddress, messageAddress);
        boolean quiet = false;
        while (!quiet) try {
            sendAndListen(ack, 300, 1, 0, 40, OmnipodPacket.class);
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

        podState.increasePacketNumber(1);
    }

    private OmnipodPacket exchangePackets(PodState podState, OmnipodPacket packet) {
        return exchangePackets(podState, packet, 0, 333, 9000, 127);
    }

    private OmnipodPacket exchangePackets(PodState podState, OmnipodPacket packet, int repeatCount, int preambleExtensionMilliseconds) {
        return exchangePackets(podState, packet, repeatCount, 333, 9000, preambleExtensionMilliseconds);
    }

    private OmnipodPacket exchangePackets(PodState podState, OmnipodPacket packet, int repeatCount, int responseTimeoutMilliseconds, int exchangeTimeoutMilliseconds, int preambleExtensionMilliseconds) {
        long timeoutTime = System.currentTimeMillis() + exchangeTimeoutMilliseconds;

        while (System.currentTimeMillis() < timeoutTime) {
            OmnipodPacket response = null;
            try {
                response = sendAndListen(packet, responseTimeoutMilliseconds, repeatCount, 9, preambleExtensionMilliseconds, OmnipodPacket.class);
            } catch (RileyLinkCommunicationException | OmnipodException ex) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets", ex);
            } catch (Exception ex) {
                throw new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, ex);
            }
            if (response == null || !response.isValid()) {
                continue;
            }
            if (response.getAddress() != packet.getAddress()) {
                continue;
            }
            if (response.getSequenceNumber() != ((podState.getPacketNumber() + 1) & 0b11111)) {
                continue;
            }

            podState.increasePacketNumber(2);
            return response;
        }
        throw new CommunicationException(CommunicationException.Type.TIMEOUT);
    }

}
