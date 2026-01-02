package app.aaps.pump.omnipod.eros.rileylink.manager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.plugin.ActivePlugin;
import app.aaps.core.interfaces.pump.defs.PumpDeviceState;
import app.aaps.core.keys.interfaces.Preferences;
import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil;
import app.aaps.pump.common.hw.rileylink.ble.RFSpy;
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse;
import app.aaps.pump.common.hw.rileylink.ble.defs.RLMessageType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData;
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import app.aaps.pump.omnipod.eros.driver.communication.action.OmnipodAction;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodPacket;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.DeactivatePodCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.ErrorResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusUpdatableResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfo;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDetailedStatus;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoResponse;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PacketType;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;
import app.aaps.pump.omnipod.eros.driver.exception.ActivationTimeExceededException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageAddressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageSequenceNumberException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPacketTypeException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalResponseException;
import app.aaps.pump.omnipod.eros.driver.exception.NonceOutOfSyncException;
import app.aaps.pump.omnipod.eros.driver.exception.NonceResyncException;
import app.aaps.pump.omnipod.eros.driver.exception.NotEnoughDataException;
import app.aaps.pump.omnipod.eros.driver.exception.OmnipodException;
import app.aaps.pump.omnipod.eros.driver.exception.PodFaultException;
import app.aaps.pump.omnipod.eros.driver.exception.PodReturnedErrorResponseException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkTimeoutException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkUnexpectedException;
import app.aaps.pump.omnipod.eros.driver.exception.RileyLinkUnreachableException;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;

/**
 * Created by andy on 6/29/18.
 */
@Singleton
public class OmnipodRileyLinkCommunicationManager extends RileyLinkCommunicationManager<OmnipodPacket> {

    // This empty constructor must be kept, otherwise dagger injection might break!
    @Inject
    public OmnipodRileyLinkCommunicationManager(
            AAPSLogger aapsLogger,
            Preferences preferences,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            RFSpy rfspy,
            ActivePlugin activePlugin,
            RileyLinkUtil rileyLinkUtil,
            Provider<WakeAndTuneTask> wakeAndTuneTaskProvider,
            Provider<RadioResponse> radioResponseProvider

    ) {
        super(aapsLogger, preferences, rileyLinkServiceData, serviceTaskExecutor, rfspy, activePlugin, rileyLinkUtil, wakeAndTuneTaskProvider, radioResponseProvider);
    }

    @Override
    public boolean tryToConnectToDevice() {
        // TODO
        return false;
    }

    @NonNull @Override
    public byte[] createPumpMessageContent(@NonNull RLMessageType type) {
        return new byte[0];
    }

    @Override public boolean isDeviceReachable() {
        return false;
    }

    @NonNull @Override
    public OmnipodPacket createResponseMessage(@NonNull byte[] payload) {
        return new OmnipodPacket(payload);
    }

    @Override
    public void setPumpDeviceState(@NonNull PumpDeviceState pumpDeviceState) {
        // Intentionally left blank
        // We don't use PumpDeviceState in the Omnipod driver
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, ErosPodStateManager podStateManager, MessageBlock command) {
        return sendCommand(responseClass, podStateManager, command, true);
    }

    public <T extends MessageBlock> T sendCommand(Class<T> responseClass, ErosPodStateManager podStateManager, MessageBlock command, boolean automaticallyResyncNone) {
        OmnipodMessage message = new OmnipodMessage(podStateManager.getAddress(), Collections.singletonList(command), podStateManager.getMessageNumber());
        return exchangeMessages(responseClass, podStateManager, message, automaticallyResyncNone);
    }

    // Convenience method
    public <T> T executeAction(OmnipodAction<T> action) {
        return action.execute(this);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, ErosPodStateManager podStateManager, OmnipodMessage message) {
        return exchangeMessages(responseClass, podStateManager, message, true);
    }

    public <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, ErosPodStateManager podStateManager, @NonNull OmnipodMessage message, boolean automaticallyResyncNonce) {
        return exchangeMessages(responseClass, podStateManager, message, null, null, automaticallyResyncNonce);
    }

    public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, ErosPodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        return exchangeMessages(responseClass, podStateManager, message, addressOverride, ackAddressOverride, true);
    }

    @Nullable public synchronized <T extends MessageBlock> T exchangeMessages(Class<T> responseClass, ErosPodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride, boolean automaticallyResyncNonce) {
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Exchanging OmnipodMessage: responseClass={}, podStateManager={}, message={}, addressOverride={}, ackAddressOverride={}, automaticallyResyncNonce={}", //
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

                getAapsLogger().debug(LTag.PUMPBTCOMM, "Received response from the Pod [responseMessageBlock={}]", responseMessageBlock);

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
                                getAapsLogger().warn(LTag.PUMPBTCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Resyncing nonce and retrying to send message as automaticallyResyncNonce=true");
                                message.resyncNonce(podStateManager.getCurrentNonce());
                            } else {
                                getAapsLogger().warn(LTag.PUMPBTCOMM, "Received ErrorResponse 0x14 (Nonce out of sync). Not resyncing nonce as automaticallyResyncNonce=true");
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

    private MessageBlock transportMessages(ErosPodStateManager podStateManager, OmnipodMessage message, Integer addressOverride, Integer ackAddressOverride) {
        int packetAddress = podStateManager.getAddress();
        if (addressOverride != null) {
            packetAddress = addressOverride;
        }

        if (podStateManager.getMessageNumber() != message.getSequenceNumber()) {
            getAapsLogger().warn(LTag.PUMPBTCOMM, "Message number in Pod State [{}] does not match message sequence number [{}]. Setting message number in Pod State to {}", podStateManager.getMessageNumber(), message.getSequenceNumber(), message.getSequenceNumber());
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
            paddedMessage.padWithGetStatusCommands(PacketType.PDM.getMaxBodyLength(), getAapsLogger()); // First packet is of type PDM
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
            encodedMessage = ByteUtil.INSTANCE.substring(encodedMessage, encodedMessageInPacket.length, encodedMessage.length - encodedMessageInPacket.length);
            firstPacket = false;

            try {
                // We actually ignore previous (ack) responses if it was not last packet to send
                response = exchangePackets(podStateManager, packet);
            } catch (OmnipodException ex) {
                boolean lastPacket = encodedMessage.length == 0;

                // If this is not the last packet, the message wasn't fully sent,
                // so it's impossible for the pod to have received the message
                ex.setCertainFailure(!lastPacket);

                getAapsLogger().debug(LTag.PUMPBTCOMM, "Caught OmnipodException in transportMessages. Set certainFailure to {} because encodedMessage.length={}", ex.isCertainFailure(), encodedMessage.length);

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
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Attempting to decode message: {}", ByteUtil.INSTANCE.shortHexStringWithoutSpaces(receivedMessageData));
                receivedMessage = OmnipodMessage.decodeMessage(receivedMessageData);
                if (receivedMessage.getAddress() != message.getAddress()) {
                    throw new IllegalMessageAddressException(message.getAddress(), receivedMessage.getAddress());
                }
                if (receivedMessage.getSequenceNumber() != podStateManager.getMessageNumber()) {
                    throw new IllegalMessageSequenceNumberException(podStateManager.getMessageNumber(), receivedMessage.getSequenceNumber());
                }
            } catch (NotEnoughDataException ex) {
                // Message is (probably) not complete yet

                getAapsLogger().debug(LTag.PUMPBTCOMM, "Caught NotEnoughDataException. Sending ACK for CON");

                OmnipodPacket ackForCon = createAckPacket(podStateManager, packetAddress, ackAddressOverride);

                OmnipodPacket conPacket = exchangePackets(podStateManager, ackForCon, 3, 40);
                if (conPacket.getPacketType() != PacketType.CON) {
                    throw new IllegalPacketTypeException(PacketType.CON, conPacket.getPacketType());
                }
                receivedMessageData = ByteUtil.INSTANCE.concat(receivedMessageData, conPacket.getEncodedMessage());
            }
        }

        ackUntilQuiet(podStateManager, packetAddress, ackAddressOverride);

        List<MessageBlock> messageBlocks = receivedMessage.getMessageBlocks();

        if (messageBlocks.isEmpty()) {
            throw new NotEnoughDataException(receivedMessageData);
        } else if (messageBlocks.size() > 1) {
            // BS: don't expect this to happen
            getAapsLogger().error(LTag.PUMPBTCOMM, "Received more than one message block: {}", messageBlocks.toString());
        }

        MessageBlock messageBlock = messageBlocks.get(0);

        podStateManager.increaseMessageNumber();

        return messageBlock;
    }

    private OmnipodPacket createAckPacket(ErosPodStateManager podStateManager, Integer packetAddress, @Nullable Integer messageAddress) {
        if (packetAddress == null) {
            packetAddress = podStateManager.getAddress();
        }
        if (messageAddress == null) {
            messageAddress = podStateManager.getAddress();
        }
        return new OmnipodPacket(packetAddress, PacketType.ACK, podStateManager.getPacketNumber(), ByteUtil.INSTANCE.getBytesFromInt(messageAddress));
    }

    private void ackUntilQuiet(ErosPodStateManager podStateManager, Integer packetAddress, Integer messageAddress) {
        OmnipodPacket ack = createAckPacket(podStateManager, packetAddress, messageAddress);
        boolean quiet = false;
        while (!quiet) try {
            sendAndListen(ack, 300, 1, 0, 40);
        } catch (RileyLinkCommunicationException ex) {
            if (RileyLinkBLEError.Timeout.equals(ex.getErrorCode())) {
                quiet = true;
            } else {
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Ignoring exception in ackUntilQuiet", ex);
            }
        } catch (OmnipodException ex) {
            getAapsLogger().debug(LTag.PUMPBTCOMM, "Ignoring exception in ackUntilQuiet", ex);
        }

        podStateManager.increasePacketNumber();
    }

    @NonNull private OmnipodPacket exchangePackets(ErosPodStateManager podStateManager, @NonNull OmnipodPacket packet) {
        return exchangePackets(podStateManager, packet, 0, 333, 9000, 127);
    }

    /** @noinspection SameParameterValue*/
    private OmnipodPacket exchangePackets(ErosPodStateManager podStateManager, @NonNull OmnipodPacket packet, int repeatCount, int preambleExtensionMilliseconds) {
        return exchangePackets(podStateManager, packet, repeatCount, 333, 9000, preambleExtensionMilliseconds);
    }

    /** @noinspection SameParameterValue*/
    @NonNull private OmnipodPacket exchangePackets(ErosPodStateManager podStateManager, OmnipodPacket packet, int repeatCount, int responseTimeoutMilliseconds, int exchangeTimeoutMilliseconds, int preambleExtensionMilliseconds) {
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
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                continue;
            } catch (OmnipodException ex) {
                gotResponseFromRileyLink = true;
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Ignoring exception in exchangePackets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                continue;
            } catch (Exception ex) {
                throw new RileyLinkUnexpectedException(ex);
            }

            if (!response.isValid()) {
                getAapsLogger().debug(LTag.PUMPBTCOMM, "exchangePackets response is invalid: " + response);
                continue;
            }

            if (response.getAddress() != packet.getAddress() &&
                    response.getAddress() != OmnipodConstants.DEFAULT_ADDRESS) { // In some (strange) cases, the Pod remains a packet address of 0xffffffff during it's lifetime
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Packet address " + response.getAddress() + " doesn't match " + packet.getAddress());
                continue;
            }

            if (response.getSequenceNumber() != podStateManager.getPacketNumber()) {
                getAapsLogger().debug(LTag.PUMPBTCOMM, "Packet sequence number " + response.getSequenceNumber() + " does not match " + podStateManager.getPacketNumber());
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
