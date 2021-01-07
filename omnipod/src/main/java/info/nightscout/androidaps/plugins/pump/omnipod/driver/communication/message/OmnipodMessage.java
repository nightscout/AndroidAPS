package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.GetStatusCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodCrc;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.MessageDecodingException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NotEnoughDataException;

public class OmnipodMessage {

    private final int address;
    private final List<MessageBlock> messageBlocks;
    private final int sequenceNumber;

    public OmnipodMessage(OmnipodMessage other) {
        address = other.address;
        messageBlocks = new ArrayList<>(other.messageBlocks);
        sequenceNumber = other.sequenceNumber;
    }

    public OmnipodMessage(int address, List<MessageBlock> messageBlocks, int sequenceNumber) {
        this.address = address;
        this.messageBlocks = new ArrayList<>(messageBlocks);
        this.sequenceNumber = sequenceNumber;
    }

    public static OmnipodMessage decodeMessage(byte[] data) {
        if (data.length < 10) {
            throw new NotEnoughDataException(data);
        }

        int address = ByteUtil.toInt((int) data[0], (int) data[1], (int) data[2],
                (int) data[3], ByteUtil.BitConversion.BIG_ENDIAN);
        byte b9 = data[4];
        int bodyLength = ByteUtil.convertUnsignedByteToInt(data[5]);
        if (data.length - 8 < bodyLength) {
            throw new NotEnoughDataException(data);
        }
        int sequenceNumber = (((int) b9 >> 2) & 0b11111);
        int crc = ByteUtil.toInt(data[data.length - 2], data[data.length - 1]);
        int calculatedCrc = OmnipodCrc.crc16(ByteUtil.substring(data, 0, data.length - 2));
        if (crc != calculatedCrc) {
            throw new CrcMismatchException(calculatedCrc, crc);
        }
        List<MessageBlock> blocks = decodeBlocks(ByteUtil.substring(data, 6, data.length - 6 - 2));
        if (blocks.size() == 0) {
            throw new MessageDecodingException("No blocks decoded");
        }

        return new OmnipodMessage(address, blocks, sequenceNumber);
    }

    private static List<MessageBlock> decodeBlocks(byte[] data) {
        List<MessageBlock> blocks = new ArrayList<>();
        int index = 0;
        while (index < data.length) {
            try {
                MessageBlockType blockType = MessageBlockType.fromByte(data[index]);
                MessageBlock block = blockType.decode(ByteUtil.substring(data, index));
                blocks.add(block);
                int blockLength = block.getRawData().length;
                index += blockLength;
            } catch (Exception ex) {
                throw new MessageDecodingException("Failed to decode blocks", ex);
            }
        }

        return blocks;
    }

    public byte[] getEncoded() {
        byte[] encodedData = new byte[0];
        for (MessageBlock messageBlock : messageBlocks) {
            encodedData = ByteUtil.concat(encodedData, messageBlock.getRawData());
        }

        byte[] header = new byte[0];
        //right before the message blocks we have 6 bits of seqNum and 10 bits of length
        header = ByteUtil.concat(header, ByteUtil.getBytesFromInt(address));
        header = ByteUtil.concat(header, (byte) (((sequenceNumber & 0x1F) << 2) + ((encodedData.length >> 8) & 0x03)));
        header = ByteUtil.concat(header, (byte) (encodedData.length & 0xFF));
        encodedData = ByteUtil.concat(header, encodedData);
        int crc = OmnipodCrc.crc16(encodedData);
        encodedData = ByteUtil.concat(encodedData, ByteUtil.substring(ByteUtil.getBytesFromInt(crc), 2, 2));
        return encodedData;
    }

    public void padWithGetStatusCommands(int packetSize, AAPSLogger aapsLogger) {
        while (getEncoded().length <= packetSize) {
            if (getEncoded().length == PacketType.PDM.getMaxBodyLength()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Message length equals max body length: {}", this);
            }
            messageBlocks.add(new GetStatusCommand(PodInfoType.NORMAL));
        }
    }

    public int getAddress() {
        return address;
    }

    public List<MessageBlock> getMessageBlocks() {
        return new ArrayList<>(messageBlocks);
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isNonceResyncable() {
        return containsBlock(NonceResyncableMessageBlock.class);
    }

    public int getSentNonce() {
        for (MessageBlock messageBlock : messageBlocks) {
            if (messageBlock instanceof NonceResyncableMessageBlock) {
                return ((NonceResyncableMessageBlock) messageBlock).getNonce();
            }
        }
        throw new UnsupportedOperationException("Message is not nonce resyncable");
    }

    public void resyncNonce(int nonce) {
        for (MessageBlock messageBlock : messageBlocks) {
            if (messageBlock instanceof NonceResyncableMessageBlock) {
                ((NonceResyncableMessageBlock) messageBlock).setNonce(nonce);
            }
        }
    }

    public boolean containsBlock(Class<? extends MessageBlock> blockType) {
        for (MessageBlock messageBlock : messageBlocks) {
            if (blockType.isInstance(messageBlock)) {
                return true;
            }
        }
        return false;
    }

    public boolean isGetStatusMessage() {
        return messageBlocks.size() == 1 && messageBlocks.get(0).getType() == MessageBlockType.GET_STATUS;
    }

    public boolean isSuspendDeliveryMessage() {
        return isCancelDeliveryMessage() && EnumSet.allOf(DeliveryType.class).equals(((CancelDeliveryCommand) messageBlocks.get(0)).getDeliveryTypes());
    }

    private boolean isCancelDeliveryMessage() {
        return messageBlocks.size() >= 1 && messageBlocks.get(0).getType() == MessageBlockType.CANCEL_DELIVERY;
    }

    @Override
    public String toString() {
        return "OmnipodMessage{" +
                "address=" + address +
                ", messageBlocks=" + messageBlocks +
                ", encoded=" + ByteUtil.shortHexStringWithoutSpaces(getEncoded()) +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
