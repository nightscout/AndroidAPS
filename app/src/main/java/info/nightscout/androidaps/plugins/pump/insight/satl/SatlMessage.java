package info.nightscout.androidaps.plugins.pump.insight.satl;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.insight.exceptions.IncompatibleSatlVersionException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidMacTrailerException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidNonceException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidPacketLengthsException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidPreambleException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidSatlCRCException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InvalidSatlCommandException;
import info.nightscout.androidaps.plugins.pump.insight.ids.SatlCommandIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;
import info.nightscout.androidaps.plugins.pump.insight.utils.Nonce;
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.Cryptograph;

public abstract class SatlMessage {

    private static final long PREAMBLE = 4293840008L;
    private static final byte VERSION = 0x20;

    private Nonce nonce;
    private long commID = 0;
    private byte[] satlContent;

    protected ByteBuf getData() {
        return new ByteBuf(0);
    }

    protected void parse(ByteBuf byteBuf) {

    }

    public ByteBuf serialize(Class<? extends SatlMessage> clazz, byte[] key) {
        ByteBuf byteBuf;
        if (nonce == null || key == null) byteBuf = serializeCRC(clazz);
        else byteBuf = serializeCTR(nonce.getProductionalBytes(), key, SatlCommandIDs.IDS.getID(clazz));
        satlContent = byteBuf.getBytes(8, byteBuf.getSize() - 16);
        return byteBuf;
    }

    private ByteBuf serializeCRC(Class<? extends SatlMessage> clazz) {
        ByteBuf data = getData();
        int length = data.getSize() + 31;
        ByteBuf byteBuf = new ByteBuf(length + 8);
        byteBuf.putUInt32LE(PREAMBLE);
        byteBuf.putUInt16LE(length);
        byteBuf.putUInt16LE(~length);
        byteBuf.putByte(VERSION);
        byteBuf.putByte(SatlCommandIDs.IDS.getID(clazz));
        byteBuf.putUInt16LE(data.getSize() + 2);
        byteBuf.putUInt32LE(clazz == KeyRequest.class ? 1 : commID);
        byteBuf.putBytes((byte) 0x00, 13);
        byteBuf.putByteBuf(data);
        byteBuf.putUInt16LE(Cryptograph.calculateCRC(byteBuf.getBytes(8, length - 10)));
        byteBuf.putBytes((byte) 0x00, 8);
        return byteBuf;
    }

    private ByteBuf serializeCTR(ByteBuf nonce, byte[] key, byte commandId) {
        ByteBuf data = getData();
        ByteBuf encryptedData = ByteBuf.from(Cryptograph.encryptDataCTR(data.getBytes(), key, nonce.getBytes()));
        int length = 29 + encryptedData.getSize();
        ByteBuf byteBuf = new ByteBuf(length + 8);
        byteBuf.putUInt32LE(PREAMBLE);
        byteBuf.putUInt16LE(length);
        byteBuf.putUInt16LE(~length);
        byteBuf.putByte(VERSION);
        byteBuf.putByte(commandId);
        byteBuf.putUInt16LE(encryptedData.getSize());
        byteBuf.putUInt32LE(commID);
        byteBuf.putByteBuf(nonce);
        byteBuf.putByteBuf(encryptedData);
        byteBuf.putBytes(Cryptograph.produceCCMTag(byteBuf.getBytes(16, 13), data.getBytes(), byteBuf.getBytes(8, 21), key));
        return byteBuf;
    }

    public static SatlMessage deserialize(ByteBuf data, Nonce lastNonce, byte[] key) throws InvalidMacTrailerException, InvalidSatlCRCException, InvalidNonceException, InvalidPreambleException, InvalidPacketLengthsException, IncompatibleSatlVersionException, InvalidSatlCommandException {
        SatlMessage satlMessage;
        byte[] satlContent = data.getBytes(8, data.getSize() - 16);
        if (key == null) satlMessage = deserializeCRC(data);
        else satlMessage = deserializeCTR(data, lastNonce, key);
        satlMessage.setSatlContent(satlContent);
        return satlMessage;
    }

    private static SatlMessage deserializeCTR(ByteBuf data, Nonce lastNonce, byte[] key) throws InvalidMacTrailerException, InvalidNonceException, InvalidPreambleException, InvalidPacketLengthsException, IncompatibleSatlVersionException, InvalidSatlCommandException {
        long preamble = data.readUInt32LE();
        int packetLength = data.readUInt16LE();
        int packetLengthXOR = data.readUInt16LE() ^ 65535;
        byte[] header = data.getBytes(21);
        byte version = data.readByte();
        byte commandId = data.readByte();
        Class<? extends SatlMessage> clazz = SatlCommandIDs.IDS.getType(commandId);
        int dataLength = data.readUInt16LE();
        long commId = data.readUInt32LE();
        byte[] nonce = data.readBytes(13);
        byte[] payload = data.readBytes(dataLength);
        byte[] trailer = data.readBytes(8);
        Nonce parsedNonce = Nonce.fromProductionalBytes(nonce);
        payload = Cryptograph.encryptDataCTR(payload, key, nonce);
        if (!Arrays.equals(trailer, Cryptograph.produceCCMTag(nonce, payload, header, key))) throw new InvalidMacTrailerException();
        if (!lastNonce.isSmallerThan(parsedNonce)) throw new InvalidNonceException();
        if (preamble != PREAMBLE) throw new InvalidPreambleException();
        if (packetLength != packetLengthXOR) throw new InvalidPacketLengthsException();
        if (version != VERSION) throw new IncompatibleSatlVersionException();
        if (clazz == null) throw new InvalidSatlCommandException();
        SatlMessage message = null;
        try {
            message = clazz.newInstance();
        } catch (Exception ignored) {
        }
        message.parse(ByteBuf.from(payload));
        message.setNonce(parsedNonce);
        message.setCommID(commId);
        return message;
    }

    private static SatlMessage deserializeCRC(ByteBuf data) throws InvalidSatlCRCException, InvalidPreambleException, InvalidPacketLengthsException, IncompatibleSatlVersionException, InvalidSatlCommandException {
        long preamble = data.readUInt32LE();
        int packetLength = data.readUInt16LE();
        int packetLengthXOR = data.readUInt16LE() ^ 65535;
        byte[] crcContent = data.getBytes(packetLength - 10);
        byte version = data.readByte();
        byte commandId = data.readByte();
        Class<? extends SatlMessage> clazz = SatlCommandIDs.IDS.getType(commandId);
        int dataLength = data.readUInt16LE();
        long commId = data.readUInt32LE();
        byte[] nonce = data.readBytes(13);
        byte[] payload = data.readBytes(dataLength - 2);
        int crc = data.readUInt16LE();
        data.shift(8);
        if (crc != Cryptograph.calculateCRC(crcContent)) throw new InvalidSatlCRCException();
        if (preamble != PREAMBLE) throw new InvalidPreambleException();
        if (packetLength != packetLengthXOR) throw new InvalidPacketLengthsException();
        if (version != VERSION) throw new IncompatibleSatlVersionException();
        if (clazz == null) throw new InvalidSatlCommandException();
        SatlMessage message = null;
        try {
            message = clazz.newInstance();
        } catch (Exception ignored) {
        }
        message.parse(ByteBuf.from(payload));
        message.setNonce(Nonce.fromProductionalBytes(nonce));
        message.setCommID(commId);
        return message;
    }

    public static boolean hasCompletePacket(ByteBuf byteBuf) {
        if (byteBuf.getSize() < 37) return false;
        if (byteBuf.getSize() < byteBuf.getUInt16LE(4) + 8) return false;
        return true;
    }

    public Nonce getNonce() {
        return this.nonce;
    }

    public long getCommID() {
        return this.commID;
    }

    public byte[] getSatlContent() {
        return this.satlContent;
    }

    public void setNonce(Nonce nonce) {
        this.nonce = nonce;
    }

    public void setCommID(long commID) {
        this.commID = commID;
    }

    public void setSatlContent(byte[] satlContent) {
        this.satlContent = satlContent;
    }
}
