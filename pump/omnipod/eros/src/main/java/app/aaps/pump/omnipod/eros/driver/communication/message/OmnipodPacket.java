package app.aaps.pump.omnipod.eros.driver.communication.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.hw.rileylink.ble.data.RLMessage;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodCrc;
import app.aaps.pump.omnipod.eros.driver.definition.PacketType;
import app.aaps.pump.omnipod.eros.driver.exception.CrcMismatchException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPacketTypeException;

/**
 * Created by andy on 6/1/18.
 */
public class OmnipodPacket implements RLMessage {
    private int packetAddress = 0;
    private PacketType packetType = PacketType.INVALID;
    private int sequenceNumber = 0;
    @Nullable private byte[] encodedMessage = null;
    private boolean valid = false;

    public OmnipodPacket(byte[] encoded) {
        if (encoded.length < 7) {
            return;
        }
        this.packetAddress = ByteUtil.INSTANCE.toInt(encoded[0], (int) encoded[1],
                (int) encoded[2], (int) encoded[3], ByteUtil.BitConversion.BIG_ENDIAN);
        try {
            this.packetType = PacketType.fromByte((byte) (((int) encoded[4] & 0xFF) >> 5));
        } catch (IllegalArgumentException ex) {
            throw new IllegalPacketTypeException(null, null);
        }
        this.sequenceNumber = (encoded[4] & 0b11111);
        byte crc = OmnipodCrc.crc8(ByteUtil.INSTANCE.substring(encoded, 0, encoded.length - 1));
        if (crc != encoded[encoded.length - 1]) {
            throw new CrcMismatchException(crc, encoded[encoded.length - 1]);
        }
        this.encodedMessage = ByteUtil.INSTANCE.substring(encoded, 5, encoded.length - 1 - 5);
        valid = true;
    }

    public OmnipodPacket(int packetAddress, PacketType packetType, int packetNumber, byte[] encodedMessage) {
        this.packetAddress = packetAddress;
        this.packetType = packetType;
        this.sequenceNumber = packetNumber;
        this.encodedMessage = encodedMessage;
        if (encodedMessage.length > packetType.getMaxBodyLength()) {
            this.encodedMessage = ByteUtil.INSTANCE.substring(encodedMessage, 0, packetType.getMaxBodyLength());
        }
        this.valid = true;
    }

    public PacketType getPacketType() {
        return packetType;
    }

    public int getAddress() {
        return packetAddress;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public byte[] getEncodedMessage() {
        return encodedMessage;
    }

    @Override
    public byte[] getTxData() {
        byte[] output = new byte[0];
        output = ByteUtil.INSTANCE.concat(output, ByteUtil.INSTANCE.getBytesFromInt(this.packetAddress));
        output = ByteUtil.INSTANCE.concat(output, (byte) ((this.packetType.getValue() << 5) + (sequenceNumber & 0b11111)));
        output = ByteUtil.INSTANCE.concat(output, encodedMessage);
        output = ByteUtil.INSTANCE.concat(output, OmnipodCrc.crc8(output));
        return output;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @NonNull @Override
    public String toString() {
        return "OmnipodPacket{" +
                "packetAddress=" + packetAddress +
                ", packetType=" + packetType +
                ", sequenceNumber=" + sequenceNumber +
                ", encodedMessage=" + ByteUtil.INSTANCE.shortHexStringWithoutSpaces(encodedMessage) +
                ", valid=" + valid +
                '}';
    }
}
