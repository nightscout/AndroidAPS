package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import android.util.Log;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;

/**
 * Created by andy on 11/21/18.
 */
@RunWith(Parameterized.class)
public class RFToolsParametrizedUTest {

    private static final String TAG = "RFToolsUTest";


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays
            .asList(new Object[][] { //
                { ByteUtil.createByteArrayFromCompactString("00"), ByteUtil.createByteArrayFromCompactString("5555") },
                //
                {
                    ByteUtil.createByteArrayFromCompactString("0000"),
                    ByteUtil.createByteArrayFromCompactString("555555") }, //
                {
                    ByteUtil.createByteArrayFromCompactString("A71289865D00BE"),
                    ByteUtil.createByteArrayFromCompactString("A96C726996A694D5552CE5") }, //
                {
                    ByteUtil.createByteArrayFromCompactString("A7128986060015"),
                    ByteUtil.createByteArrayFromCompactString("A96C726996A6566555C655") }, //
                {
                    ByteUtil.createByteArrayFromCompactString("A7128986150956"),
                    ByteUtil.createByteArrayFromCompactString("A96C726996A6C655599665") }, //
                {
                    ByteUtil.createByteArrayFromCompactString("A71289868D00B0"),
                    ByteUtil.createByteArrayFromCompactString("A96C726996A668D5552D55") }, //
                {
                    ByteUtil
                        .createByteArrayFromCompactString("A71289868D090337323200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000039"),
                    ByteUtil
                        .createByteArrayFromCompactString("A96C726996A668D5595638D68F28F25555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555558D95")
                //
                }, //
            });
    }

    @Parameterized.Parameter
    // first data value (0) is default
    public/* NOT private */byte[] decoded;

    @Parameterized.Parameter(1)
    public/* NOT private */byte[] encoded;


    // @Test
    public void testEncodeGeoff() {
        /*
         * {0xa7} -> {0xa9, 0x60}
         * {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
         * {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
         */
        /* test compare */
        // byte[] s1 = { 0, 1, 2 };
        // byte[] s2 = { 2, 1, 0, 3 };
        // byte[] s3 = { 0, 1, 2, 3 };
        // if (ByteUtil.compare(s1, s1) != 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s1, s2) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s2, s1) <= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s1, s3) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        byte[] bs = RFTools.encode4b6b(new byte[] { (byte)0xa7 });
        byte[] out = new byte[] { (byte)(0xa9), 0x65 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }
        bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }
        bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

    }


    // @Test
    public void testEncodeGo() {
        /*
         * {0xa7} -> {0xa9, 0x60}
         * {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
         * {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
         */
        /* test compare */
        // byte[] s1 = { 0, 1, 2 };
        // byte[] s2 = { 2, 1, 0, 3 };
        // byte[] s3 = { 0, 1, 2, 3 };
        // if (ByteUtil.compare(s1, s1) != 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s1, s2) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s2, s1) <= 0) {
        // LOG.error("test: compare failed.");
        // }
        // if (ByteUtil.compare(s1, s3) >= 0) {
        // LOG.error("test: compare failed.");
        // }
        // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        byte[] bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7 });
        byte[] out = new byte[] { (byte)(0xa9), 0x65 };

        System.out.println("EncodeGo: " + ByteUtil.getHex(bs));

        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

        bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7, 0x12 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

        bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

    }


    // @Test
    public void testDecodeGo() {

        // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        byte[] bs = RFTools.encode4b6b(new byte[] { (byte)0xa7 });
        byte[] out = new byte[] { (byte)(0xa9), 0x65 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
        }

        byte[] back = RFTools.decode4b6b(out);

        if (ByteUtil.compare(back, bs) != 0) {
            Log.e(
                TAG,
                "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

        bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
        }

        back = RFTools.decode4b6b(out);

        if (ByteUtil.compare(back, bs) != 0) {
            Log.e(
                TAG,
                "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

        bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        if (ByteUtil.compare(bs, out) != 0) {
            Log.e(
                TAG,
                "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
        }

        back = RFTools.decode4b6b(out);

        if (ByteUtil.compare(back, bs) != 0) {
            Log.e(
                TAG,
                "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
                    + ByteUtil.shortHexString(bs));
            Assert.fail();
        }

        return;
    }


    // @Test
    // public void ttt_decodeGo() {
    //
    // RFTools.DecodeResponseDto decodeResponseDto = RFTools.decode6b4b_go(new byte[] {
    // (byte)0xF9, (byte)0xE9, 0x63, (byte)0x9E, 0x7F, (byte)0xE6, 0x79, 0x5F, -1, (byte)0xCF, (byte)0xF0 });
    //
    // if (decodeResponseDto.errorData != null) {
    // Log.e(TAG, decodeResponseDto.errorData);
    // Assert.assertTrue(false);
    // } else {
    // Assert.assertTrue(true);
    // System.out.println("Response: " + ByteUtil.getHex(decodeResponseDto.data));
    // }
    //
    // }

    // @Test
    public void testParametrizedGeoffEncode() {
        byte[] encodedX = RFTools.encode4b6b(this.decoded);

        // if (ByteUtil.compare(encodedX, this.encoded) != 0) {
        // Assert.assertEquals(encodedX, encoded);
        // }

        Assert.assertArrayEquals(encodedX, encoded);
    }


    @Test
    public void geoffDecode() {
        byte[] decodedX = RFTools.decode4b6b(this.encoded);

        Assert.assertArrayEquals(decoded, decodedX);
    }


    @Test
    public void goDecode() {
        RFTools.DecodeResponseDto decodeResponseDto = RFTools.decode4b6b_go(this.encoded);

        Assert.assertNull(decodeResponseDto.errorData);
        System.out.println("Result:   " + ByteUtil.getHex(decodeResponseDto.data));
        System.out.println("Expected: " + ByteUtil.getHex(decoded));
        Assert.assertArrayEquals(decoded, decodeResponseDto.data);
    }


    // @Test
    public void loopDecode() {
        byte[] data = RFTools.decode4b6b_loop(this.encoded);

        // RFTools.DecodeResponseDto decodeResponseDto

        // Assert.assertNull(decodeResponseDto.errorData);
        System.out.println("Result:   " + ByteUtil.getHex(data));
        System.out.println("Expected: " + ByteUtil.getHex(decoded));
        Assert.assertArrayEquals(decoded, data);
    }


    @Test
    public void geoffEncode() {
        byte[] encodedX = RFTools.encode4b6b(this.decoded);

        Assert.assertArrayEquals(encoded, encodedX);
    }


    @Test
    public void goEncode() {
        byte[] encodedX = RFTools.encode4b6b_go(this.decoded);
        System.out.println("Result:   " + ByteUtil.getHex(encodedX));
        System.out.println("Expected: " + ByteUtil.getHex(encoded));
        Assert.assertArrayEquals(encoded, encodedX);
    }


    @Test
    public void loopEncode() {
        byte[] encodedX = RFTools.encode4b6b_loop(this.decoded);
        System.out.println("Result:   " + ByteUtil.getHex(encodedX));
        System.out.println("Expected: " + ByteUtil.getHex(encoded));
        Assert.assertArrayEquals(encoded, encodedX);
    }


    private short[] createShortArray(byte[] data) {

        short[] outData = new short[data.length];

        for (int i = 0; i < data.length; i++) {
            short d = data[i];

            if (d < 0) {
                d += 256;
            }

            outData[i] = d;
        }

        return outData;
    }
}
