package info.nightscout.androidaps.plugins.pump.insight.utils;

import java.math.BigInteger;

public class Nonce {

    private BigInteger bigInteger;

    public Nonce() {
        bigInteger = BigInteger.ZERO;
    }

    public Nonce(byte[] storageValue) {
        bigInteger = new BigInteger(storageValue);
    }

    public byte[] getStorageValue() {
        return bigInteger.toByteArray();
    }

    public ByteBuf getProductionalBytes() {
        ByteBuf byteBuf = new ByteBuf(13);
        byteBuf.putBytesLE(bigInteger.toByteArray());
        byteBuf.putBytes((byte) 0x00, 13 - byteBuf.getSize());
        return byteBuf;
    }

    public static Nonce fromProductionalBytes(byte[] bytes) {
        ByteBuf byteBuf = new ByteBuf(14);
        byteBuf.putByte((byte) 0x00);
        byteBuf.putBytesLE(bytes);
        return new Nonce(byteBuf.getBytes());
    }

    public void increment() {
        bigInteger = bigInteger.add(BigInteger.ONE);
    }

    public void increment(int count) {
        bigInteger = bigInteger.add(BigInteger.valueOf(count));
    }

    public boolean isSmallerThan(Nonce greater) {
        return bigInteger.compareTo(greater.bigInteger) < 0;
    }

}
