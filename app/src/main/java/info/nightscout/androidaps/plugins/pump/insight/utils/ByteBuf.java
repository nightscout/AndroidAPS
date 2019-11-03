package info.nightscout.androidaps.plugins.pump.insight.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

public class ByteBuf {

    private byte[] bytes;
    private int size = 0;

    public ByteBuf(int length) {
        bytes = new byte[length];
    }


    public byte[] getBytes() {
        byte[] bytes = new byte[size];
        System.arraycopy(this.bytes, 0, bytes, 0, size);
        return bytes;
    }

    public void shift(int offset) {
        System.arraycopy(bytes, offset, bytes, 0, bytes.length - offset);
        size -= offset;
    }

    public byte getByte(int position) {
        return bytes[position];
    }

    public byte getByte() {
        return bytes[0];
    }

    public byte readByte() {
        byte b = getByte();
        shift(1);
        return b;
    }

    public void putByte(byte b) {
        bytes[size] = b;
        size += 1;
    }


    public void putBytes(byte b, int count) {
        for (int i = 0; i < count; i++) bytes[size++] = b;
    }


    public byte[] getBytes(int position, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, position, copy, 0, length);
        return copy;
    }

    public byte[] getBytes(int length) {
        return getBytes(0, length);
    }

    public byte[] readBytes(int length) {
        byte[] copy = getBytes(length);
        shift(length);
        return copy;
    }

    byte[] readBytes() {
        return readBytes(size);
    }

    public void putBytes(byte[] bytes, int length) {
        System.arraycopy(bytes, 0, this.bytes, size, length);
        size += length;
    }

    public void putBytes(byte[] bytes) {
        putBytes(bytes, bytes.length);
    }


    private byte[] getBytesLE(int position, int length) {
        byte[] copy = new byte[length];
        for (int i = 0; i < length; i++)
            copy[i] = bytes[length - 1 - i + position];
        return copy;
    }

    private byte[] getBytesLE(int length) {
        return getBytesLE(0, length);
    }

    public byte[] readBytesLE(int length) {
        byte[] copy = getBytesLE(length);
        shift(length);
        return copy;
    }

    private void putBytesLE(byte[] bytes, int length) {
        for (int i = 0; i < length; i++)
            this.bytes[size + length - 1 - i] = bytes[i];
        size += length;
    }

    void putBytesLE(byte[] bytes) {
        putBytesLE(bytes, bytes.length);
    }


    public void putByteBuf(ByteBuf byteBuf) {
        putBytes(byteBuf.getBytes(), byteBuf.getSize());
    }


    private short getUInt8(int position) {
        return (short) (bytes[position] & 0xFF);
    }

    private short getUInt8() {
        return getUInt8(0);
    }

    public short readUInt8() {
        short value = getUInt8();
        shift(1);
        return value;
    }

    public void putUInt8(short value) {
        putByte((byte) (value & 0xFF));
    }


    public int getUInt16LE(int position) {
        return (bytes[position++] & 0xFF |
                (bytes[position] & 0xFF) << 8);
    }

    private int getUInt16LE() {
        return getUInt16LE(0);
    }

    public int readUInt16LE() {
        int i = getUInt16LE();
        shift(2);
        return i;
    }

    public void putUInt16LE(int i) {
        putByte((byte) (i & 0xFF));
        putByte((byte) ((i >> 8) & 0xFF));
    }


    private double getUInt16Decimal(int position) {
        return new BigDecimal(getUInt16LE(position))
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double getUInt16Decimal() {
        return getUInt16Decimal(0);
    }

    public double readUInt16Decimal() {
        double d = getUInt16Decimal();
        shift(2);
        return d;
    }

    public void putUInt16Decimal(double d) {
        putUInt16LE(new BigDecimal(d)
                .multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue());
    }


    private double getUInt32Decimal100(int position) {
        return new BigDecimal(getUInt32LE(position))
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double getUInt32Decimal100() {
        return getUInt32Decimal100(0);
    }

    public double readUInt32Decimal100() {
        double d = getUInt32Decimal100();
        shift(4);
        return d;
    }

    public void putUInt32Decimal100(double d) {
        putUInt32LE(new BigDecimal(d)
                .multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue());
    }


    private double getUInt32Decimal1000(int position) {
        return new BigDecimal(getUInt32LE(position))
                .divide(new BigDecimal(1000), 3, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double getUInt32Decimal1000() {
        return getUInt32Decimal1000(0);
    }

    public double readUInt32Decimal1000() {
        double d = getUInt32Decimal1000();
        shift(4);
        return d;
    }

    public void putUInt32Decimal1000(double d) {
        putUInt32LE(new BigDecimal(d)
                .multiply(new BigDecimal(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue());
    }


    private short getShort(int position) {
        return (short) (bytes[position++] << 8 |
                bytes[position] & 0xFF);
    }

    public short getShort() {
        return getShort(0);
    }

    public short readShort() {
        short s = getShort();
        shift(2);
        return s;
    }

    public void putShort(short s) {
        putByte((byte) (s >> 8));
        putByte((byte) s);
    }


    private long getUInt32LE(int position) {
        return ((long) bytes[position++] & 0xFF) |
                ((long) bytes[position++] & 0xFF) << 8 |
                ((long) bytes[position++] & 0xFF) << 16 |
                ((long) bytes[position] & 0xFF) << 24;
    }

    private long getUInt32LE() {
        return getUInt32LE(0);
    }

    public long readUInt32LE() {
        long l = getUInt32LE();
        shift(4);
        return l;
    }

    public void putUInt32LE(long l) {
        putByte((byte) (l & 0xFF));
        putByte((byte) ((l >> 8) & 0xFF));
        putByte((byte) ((l >> 16) & 0xFF));
        putByte((byte) ((l >> 24) & 0xFF));
    }


    private String getUTF16(int position, int stringLength) {
        String string = new String(getBytes(position, stringLength * 2 + 2), StandardCharsets.UTF_16LE);
        return string.substring(0, string.indexOf(new String(new char[]{0, 0})));
    }

    private String getUTF16(int stringLength) {
        return getUTF16(0, stringLength);
    }

    public String readUTF16(int stringLength) {
        String string = getUTF16(stringLength);
        shift(stringLength * 2 + 2);
        return string;
    }

    public void putUTF16(String string, int stringLength) {
        putBytes(string.getBytes(StandardCharsets.UTF_16LE), stringLength * 2);
        putBytes((byte) 0, 2);
    }


    private String getASCII(int position, int stringLength) {
        String string = new String(getBytes(position, stringLength + 1), StandardCharsets.US_ASCII);
        return string.substring(0, string.indexOf(0));
    }

    private String getASCII(int stringLength) {
        return getASCII(0, stringLength);
    }

    public String readASCII(int stringLength) {
        String string = getASCII(stringLength);
        shift(stringLength + 1);
        return string;
    }

    public void putASCII(String string, int stringLength) {
        putBytes(string.getBytes(StandardCharsets.UTF_16LE), stringLength * 2);
        putBytes((byte) 0, 1);
    }


    public boolean getBoolean(int position) {
        return getUInt16LE(position) == 75;
    }

    public boolean getBoolean() {
        return getBoolean(0);
    }

    public boolean readBoolean() {
        boolean bool = getBoolean();
        shift(2);
        return bool;
    }

    public void putBoolean(boolean bool) {
        putUInt16LE(bool ? 75 : 180);
    }


    public static ByteBuf from(byte[] bytes, int length) {
        ByteBuf byteBuf = new ByteBuf(length);
        byteBuf.putBytes(bytes, length);
        return byteBuf;
    }

    public static ByteBuf from(byte[] bytes) {
        return from(bytes, bytes.length);
    }

    public int getSize() {
        return this.size;
    }

    public void clear() {
        shift(size);
    }
}
