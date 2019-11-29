package info.nightscout.androidaps.plugins.pump.omnipod.comm.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.CrcMismatchException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.MessageDecodingException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.NotEnoughDataException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmniCRC;

public class OmnipodMessage {

    private static final Logger LOG = LoggerFactory.getLogger(OmnipodCommunicationService.class);
    private final int address;
    private final List<MessageBlock> messageBlocks;
    private final int sequenceNumber;

    public OmnipodMessage(int address, List<MessageBlock> messageBlocks, int sequenceNumber) {
        this.address = address;
        this.messageBlocks = messageBlocks;
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
        int calculatedCrc = OmniCRC.crc16(ByteUtil.substring(data, 0, data.length - 2));
        if (crc != calculatedCrc) {
            throw new CrcMismatchException(calculatedCrc, crc);
        }
        List<MessageBlock> blocks = decodeBlocks(ByteUtil.substring(data, 6, data.length - 6 - 2));
        if (blocks == null || blocks.size() == 0) {
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
        int crc = OmniCRC.crc16(encodedData);
        encodedData = ByteUtil.concat(encodedData, ByteUtil.substring(ByteUtil.getBytesFromInt(crc), 2, 2));
        return encodedData;
    }

    public List<MessageBlock> getMessageBlocks() {
        return messageBlocks;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "OmnipodMessage{" +
                "address=" + address +
                ", encoded=" + ByteUtil.shortHexString(getEncoded()) +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }

    public boolean isNonceResyncable() {
        return messageBlocks.size() > 0 && (messageBlocks.get(0) instanceof NonceResyncableMessageBlock);
    }

    public int getSentNonce() {
        if (!isNonceResyncable()) {
            throw new UnsupportedOperationException("Message is not nonce resyncable");
        }
        return ((NonceResyncableMessageBlock) messageBlocks.get(0)).getNonce();
    }

    public void resyncNonce(int nonce) {
        for (MessageBlock messageBlock : messageBlocks) {
            if (messageBlock instanceof NonceResyncableMessageBlock) {
                ((NonceResyncableMessageBlock) messageBlock).setNonce(nonce);
            }
        }
    }
}
