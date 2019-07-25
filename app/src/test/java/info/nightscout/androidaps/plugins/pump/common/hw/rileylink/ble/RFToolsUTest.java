package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import org.junit.Test;

/**
 * Created by andy on 11/21/18.
 */

public class RFToolsUTest {

    private static final String TAG = "RFToolsUTest";


    @Test
    public void testEncodeGeoff() {
        // /*
        // * {0xa7} -> {0xa9, 0x60}
        // * {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
        // * {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
        // */
        // /* test compare */
        // // byte[] s1 = { 0, 1, 2 };
        // // byte[] s2 = { 2, 1, 0, 3 };
        // // byte[] s3 = { 0, 1, 2, 3 };
        // // if (ByteUtil.compare(s1, s1) != 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s1, s2) >= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s2, s1) <= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s1, s3) >= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        // byte[] bs = RFTools.encode4b6b(new byte[] { (byte)0xa7 });
        // byte[] out = new byte[] { (byte)(0xa9), 0x65 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        // bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        // bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }

    }


    @Test
    public void testEncodeGo() {
        // /*
        // * {0xa7} -> {0xa9, 0x60}
        // * {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
        // * {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
        // */
        // /* test compare */
        // // byte[] s1 = { 0, 1, 2 };
        // // byte[] s2 = { 2, 1, 0, 3 };
        // // byte[] s3 = { 0, 1, 2, 3 };
        // // if (ByteUtil.compare(s1, s1) != 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s1, s2) >= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s2, s1) <= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // if (ByteUtil.compare(s1, s3) >= 0) {
        // // LOG.error("test: compare failed.");
        // // }
        // // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        // byte[] bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7 });
        // byte[] out = new byte[] { (byte)(0xa9), 0x65 };
        //
        // System.out.println("EncodeGo: " + ByteUtil.getHex(bs));
        //
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        //
        // bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7, 0x12 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        //
        // bs = RFTools.encode4b6b_go(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }

    }


    @Test
    public void testDecodeGo() {

        // // testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        // byte[] bs = RFTools.encode4b6b(new byte[] { (byte)0xa7 });
        // byte[] out = new byte[] { (byte)(0xa9), 0x65 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // }
        //
        // byte[] back = RFTools.decode4b6b(out);
        //
        // if (ByteUtil.compare(back, bs) != 0) {
        // Log.e(
        // TAG,
        // "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        //
        // bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // }
        //
        // back = RFTools.decode4b6b(out);
        //
        // if (ByteUtil.compare(back, bs) != 0) {
        // Log.e(
        // TAG,
        // "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }
        //
        // bs = RFTools.encode4b6b(new byte[] { (byte)0xa7, 0x12, (byte)0xa7 });
        // out = new byte[] { (byte)(0xa9), 0x6c, 0x72, (byte)0xa9, 0x65 };
        // if (ByteUtil.compare(bs, out) != 0) {
        // Log.e(
        // TAG,
        // "encode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // }
        //
        // back = RFTools.decode4b6b(out);
        //
        // if (ByteUtil.compare(back, bs) != 0) {
        // Log.e(
        // TAG,
        // "decode Data failed: expected " + ByteUtil.shortHexString(out) + " but got "
        // + ByteUtil.shortHexString(bs));
        // Assert.fail();
        // }

        return;
    }


    @Test
    public void ttt_decodeGo() {

        // RFTools.DecodeResponseDto decodeResponseDto = RFTools
        // .decode4b6b_go(new byte[] {
        // (byte)0xF9, (byte)0xE9, 0x63, (byte)0x9E, 0x7F, (byte)0xE6, 0x79, 0x5F, (byte)0xFF, (byte)0xCF,
        // (byte)0xF0 });
        //
        // if (decodeResponseDto.errorData != null) {
        // Log.e(TAG, decodeResponseDto.errorData);
        // Assert.assertTrue(false);
        // } else {
        // Assert.assertTrue(true);
        // System.out.println("Response: " + ByteUtil.getHex(decodeResponseDto.data));
        // }

    }


    @Test
    public void goTest() {
        // System.out.println(RFTools.hi(4, (short)0xa7));
    }

}
