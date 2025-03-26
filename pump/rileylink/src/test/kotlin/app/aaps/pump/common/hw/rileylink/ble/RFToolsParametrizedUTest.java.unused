package app.aaps.pump.common.hw.rileylink.ble;

import android.util.Log;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff;

/**
 * Created by andy on 11/21/18.
 */
@RunWith(Parameterized.class)
public class RFToolsParametrizedUTest {

    private static final String TAG = "RFToolsUTest";
    @Parameterized.Parameter
    // first data value (0) is default
    public/* NOT private */ byte[] decoded;
    @Parameterized.Parameter(1)
    public/* NOT private */ byte[] encoded;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays
                .asList(new Object[][]{ //
                        {ByteUtil.INSTANCE.createByteArrayFromCompactString("00"), ByteUtil.INSTANCE.createByteArrayFromCompactString("5555")},
                        //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("0000"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("555555")}, //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A71289865D00BE"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A96C726996A694D5552CE5")}, //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A7128986060015"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A96C726996A6566555C655")}, //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A7128986150956"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A96C726996A6C655599665")}, //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A71289868D00B0"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A96C726996A668D5552D55")}, //
                        {
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A71289868D090337323200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000039"),
                                ByteUtil.INSTANCE.createByteArrayFromCompactString("A96C726996A668D5595638D68F28F25555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555558D95")
                                //
                        }, //
                });
    }

    /**
     * @noinspection JUnit3StyleTestMethodInJUnit4Class, unused
     */ // @Test
    public void testEncodeGeoff() {

        Encoding4b6bGeoff decoder = new Encoding4b6bGeoff(null);

        /*
         * {0xa7} -> {0xa9, 0x60}
         * {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
         * {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
         */
        /* test compare */
        // byte[] s1 = { 0, 1, 2 };
        // byte[] s2 = { 2, 1, 0, 3 };
        // byte[] s3 = { 0, 1, 2, 3 };
        // if (ByteUtil.INSTANCE.compare(s1, s1) != 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.INSTANCE.compare(s1, s2) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.INSTANCE.compare(s2, s1) <= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.INSTANCE.compare(s1, s3) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        byte[] bs = decoder.encode4b6b(new byte[]{(byte) 0xa7});
        byte[] out = new byte[]{(byte) (0xa9), 0x65};
        if (ByteUtil.INSTANCE.compare(bs, out) != 0) {
            Log.e(
                    TAG,
                    "encode Data failed: expected " + ByteUtil.INSTANCE.shortHexString(out) + " but got "
                            + ByteUtil.INSTANCE.shortHexString(bs));
            Assert.fail();
        }
        bs = decoder.encode4b6b(new byte[]{(byte) 0xa7, 0x12});
        out = new byte[]{(byte) (0xa9), 0x6c, 0x72};
        if (ByteUtil.INSTANCE.compare(bs, out) != 0) {
            Log.e(
                    TAG,
                    "encode Data failed: expected " + ByteUtil.INSTANCE.shortHexString(out) + " but got "
                            + ByteUtil.INSTANCE.shortHexString(bs));
            Assert.fail();
        }
        bs = decoder.encode4b6b(new byte[]{(byte) 0xa7, 0x12, (byte) 0xa7});
        out = new byte[]{(byte) (0xa9), 0x6c, 0x72, (byte) 0xa9, 0x65};
        if (ByteUtil.INSTANCE.compare(bs, out) != 0) {
            Log.e(
                    TAG,
                    "encode Data failed: expected " + ByteUtil.INSTANCE.shortHexString(out) + " but got "
                            + ByteUtil.INSTANCE.shortHexString(bs));
            Assert.fail();
        }

    }


    /**
     * @noinspection JUnit3StyleTestMethodInJUnit4Class, unused
     */ // @Test
    public void testParametrizedGeoffEncode() {

        Encoding4b6bGeoff decoder = new Encoding4b6bGeoff(null);

        byte[] encodedX = decoder.encode4b6b(this.decoded);

        // if (ByteUtil.INSTANCE.compare(encodedX, this.encoded) != 0) {
        // Assert.assertEquals(encodedX, encoded);
        // }

        Assert.assertArrayEquals(encodedX, encoded);
    }


    /**
     * @noinspection unused
     */ // @Test
    public void geoffDecode() throws Exception {
        Encoding4b6bGeoff decoder = new Encoding4b6bGeoff(null);

        byte[] decodedX = decoder.decode4b6b(this.encoded);

        Assert.assertArrayEquals(decoded, decodedX);
    }


    /**
     * @noinspection unused
     */ // @Test
    public void geoffEncode() {

        Encoding4b6bGeoff decoder = new Encoding4b6bGeoff(null);

        byte[] encodedX = decoder.encode4b6b(this.decoded);

        Assert.assertArrayEquals(encoded, encodedX);
    }


}
