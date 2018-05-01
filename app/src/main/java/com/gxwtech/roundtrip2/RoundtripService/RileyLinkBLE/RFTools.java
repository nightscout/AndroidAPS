package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE;

import android.util.Log;

import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.CRC;

import java.util.ArrayList;

/**
 * Created by geoff on 7/31/15.
 */
public class RFTools {
    private static final String TAG = "RFTools";
    /*
     CodeSymbols is an ordered list of translations
     6bits -> 4 bits, in order from 0x0 to 0xF
     The 6 bit codes are what is used on the RF side of the RileyLink
     to communicate with a Medtronic pump.

     */
    public static byte[] CodeSymbols = {
            0x15,
            0x31,
            0x32,
            0x23,
            0x34,
            0x25,
            0x26,
            0x16,
            0x1a,
            0x19,
            0x2a,
            0x0b,
            0x2c,
            0x0d,
            0x0e,
            0x1c
    };

    public static byte[] appendChecksum(final byte[] input) {
        if (input == null) {
            return null;
        }
        if (input.length == 0) {
            return null;
        }
        byte[] rval = new byte[input.length+1];
        System.arraycopy(input, 0, rval, 0, input.length);
        byte mycrc = CRC.crc8(input);
        Log.d(TAG,String.format("Adding checksum 0x%02X to %d byte array from 0x%02X to 0x%02X",mycrc,input.length,input[0],input[input.length-1]));
        rval[input.length] = mycrc;
        return rval;
    }

    public static ArrayList<Byte> fromBytes(byte[] data) {
        ArrayList<Byte> rval = new ArrayList<>();
        for (int i=0; i<data.length; i++) {
            rval.add(data[i]);
        }
        return rval;
    }

    public static byte[] toBytes(ArrayList<Byte> data) {
        byte[] rval = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            rval[i] = data.get(i);
        }
        return rval;
    }

/*
    + (NSData*)encode4b6b:(NSData*)data {
        NSMutableData *outData = [NSMutableData data];
        NSMutableData *dataPlusCrc = [data mutableCopy];
        unsigned char crc = [MinimedPacket crcForData:data];
        [dataPlusCrc appendBytes:&crc length:1];
        char codes[16] = {21,49,50,35,52,37,38,22,26,25,42,11,44,13,14,28};
        const unsigned char *inBytes = [dataPlusCrc bytes];
        unsigned int acc = 0x0;
        int bitcount = 0;
        for (int i=0; i < dataPlusCrc.length; i++) {
            acc <<= 6;
            acc |= codes[inBytes[i] >> 4];
            bitcount += 6;

            acc <<= 6;
            acc |= codes[inBytes[i] & 0x0f];
            bitcount += 6;

            while (bitcount >= 8) {
                unsigned char outByte = acc >> (bitcount-8) & 0xff;
                [outData appendBytes:&outByte length:1];
                bitcount -= 8;
                acc &= (0xffff >> (16-bitcount));
            }
        }
        if (bitcount > 0) {
            acc <<= (8-bitcount);
            unsigned char outByte = acc & 0xff;
            [outData appendBytes:&outByte length:1];
        }
        return outData;
    }
*/

    public static final byte[] codes = new byte[] {21,49,50,35,52,37,38,22,26,25,42,11,44,13,14,28 };

    /* O(n) lookup.  Run on an O(n) translation of a byte-stream, gives O(n**2) performance. Sigh. */
    public static int codeIndex(byte b) {
        for (int i=0; i< codes.length; i++) {
            if (b == codes[i]) {
                return i;
            }
        }
        return -1;
    }

    public static byte[] encode4b6b(byte[] data) {
        if ((data.length % 2)!=0) {
           // Log.e(TAG,"Warning: data is odd number of bytes");
        }
        // use arraylists because byte[] is annoying.
        ArrayList<Byte> inData = fromBytes(data);
        ArrayList<Byte> outData = new ArrayList<>();

        int acc = 0;
        int bitcount = 0;
        int i;
        for (i=0; i<inData.size(); i++) {
            acc <<= 6;
            acc |= codes[(inData.get(i) >> 4) & 0x0f];
            bitcount += 6;

            acc <<= 6;
            acc |= codes[inData.get(i) & 0x0f];
            bitcount += 6;

            while (bitcount >= 8) {
                byte outByte = (byte)(acc >> (bitcount-8) & 0xff);
                outData.add(outByte);
                bitcount -= 8;
                acc &= (0xffff >> (16 - bitcount));
            }
        }
        if (bitcount > 0) {
            acc <<= 6;
            acc |= 0x14; // marks uneven packet boundary.
            bitcount += 6;
            if (bitcount >= 8) {
                byte outByte = (byte)((acc >> (bitcount - 8)) & 0xff);
                outData.add(outByte);
                bitcount -=8;
                // acc &= (0xffff >> (16 - bitcount));
            }
            while(bitcount >=8) {
                outData.add((byte)0);
                bitcount -= 8;
            }
        }


        // convert back to byte[]
        byte[] rval = toBytes(outData);

        return rval;

    }

    public static void test() {
        /*
        {0xa7} -> {0xa9, 0x60}
        {0xa7, 0x12} -> {0xa9, 0x6c, 0x72}
        {0xa7, 0x12, 0xa7} -> {0xa9, 0x6c, 0x72, 0xa9, 0x60}
        */
        /* test compare */
        byte[] s1 = {0,1,2};
        byte[] s2 = {2,1,0,3};
        byte[] s3 = {0,1,2,3};
        if (ByteUtil.compare(s1,s1)!=0) {
            Log.e(TAG,"test: compare failed.");
        }
        if (ByteUtil.compare(s1,s2)>=0) {
            Log.e(TAG,"test: compare failed.");
        }
        if (ByteUtil.compare(s2,s1)<=0) {
            Log.e(TAG,"test: compare failed.");
        }
        if (ByteUtil.compare(s1,s3)>=0) {
            Log.e(TAG,"test: compare failed.");
        }
        //testCompose(new byte[] {(byte)0xa7, (byte)0xa7});
        byte[] bs = encode4b6b(new byte[]{(byte) 0xa7});
        byte[] out = new byte[] {(byte)(0xa9),0x65};
        if (ByteUtil.compare(bs,out)!=0) {
            Log.e(TAG,"encode Data failed: expected "+ByteUtil.shortHexString(out)+" but got "+ByteUtil.shortHexString(bs));
        }
        bs = encode4b6b(new byte[]{(byte) 0xa7, 0x12});
        out = new byte[] {(byte)(0xa9),0x6c,0x72};
        if (ByteUtil.compare(bs,out)!=0) {
            Log.e(TAG,"encode Data failed: expected "+ByteUtil.shortHexString(out)+" but got "+ByteUtil.shortHexString(bs));
        }
        bs = encode4b6b(new byte[]{(byte) 0xa7, 0x12, (byte) 0xa7});
        out = new byte[] {(byte)(0xa9),0x6c,0x72,(byte)0xa9,0x65};
        if (ByteUtil.compare(bs,out)!=0) {
            Log.e(TAG,"encode Data failed: expected "+ByteUtil.shortHexString(out)+" but got "+ByteUtil.shortHexString(bs));
        }
        return;
    }

    public static byte[] decode4b6b(byte[] raw) throws NumberFormatException {
        /*
        if ((raw.length % 2) != 0) {
            Log.e(TAG,"Warning: data is odd number of bytes");
        }
        */
        byte[] rval = new byte[]{};
        int availableBits = 0;
        int codingErrors = 0;
        int x = 0;
        //Log.w(TAG,"decode4b6b: untested code");
        //Log.w(TAG,String.format("Decoding %d bytes: %s",raw.length,ByteUtil.shortHexString(raw)));
        for (int i=0; i<raw.length; i++) {
            int unsignedValue = raw[i];
            if (unsignedValue < 0) {
                unsignedValue += 256;
            }
            x = (x << 8) + unsignedValue;
            availableBits += 8;
            if (availableBits >= 12) {
                // take top six
                int highcode = (x >> (availableBits - 6)) & 0x3F;
                int highIndex = codeIndex((byte) (highcode));
                // take bottom six
                int lowcode = (x >> (availableBits - 12)) & 0x3F;
                int lowIndex = codeIndex((byte) (lowcode));
                // special case at end of transmission on uneven boundaries:
                if ((highIndex >= 0) && (lowIndex >= 0)) {
                    byte decoded = (byte) ((highIndex << 4) + lowIndex);
                    rval = ByteUtil.concat(rval, decoded);
                    /*
                    Log.d(TAG,String.format("i=%d,x=0x%08X,0x%02X->0x%02X, 0x%02X->0x%02X, result: 0x%02X, %d bits remaining, errors %d, bytes remaining: %s",
                            i,x,highcode,highIndex, lowcode, lowIndex,decoded,availableBits,codingErrors,ByteUtil.shortHexString(ByteUtil.substring(raw,i+1,raw.length-i-1))));
                    */
                } else {
                    //Log.d(TAG,String.format("i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining",i,x,highcode,lowcode,availableBits));
                    codingErrors++;
                }

                availableBits -= 12;
                x = x & (0x0000ffff >> (16 - availableBits));
            } else {
                //Log.d(TAG,String.format("i=%d, skip: x=0x%08X, available bits %d",i,x,availableBits));
            }
        }
        if (availableBits !=0) {
            if ((availableBits == 4) && (x == 0x05)) {
                // normal end
            } else {
                Log.e(TAG, "decode4b6b: failed clean decode -- extra bits available (not marker)(" + availableBits + ")");
                codingErrors++;
            }
        } else {
            // also normal end.
        }
        if (codingErrors>0) {
            Log.e(TAG, "decode4b6b: "+codingErrors+" coding errors encountered.");
            throw new NumberFormatException();
        }
        return rval;
    }

    public static String toHexString(byte[] array) {
        return toHexString(array, 0, array.length);
    }

    private final static char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static String toHexString(byte[] array, int offset, int length) {
        char[] buf = new char[length * 2];

        int bufIndex = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = array[i];
            buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
        }

        return new String(buf);
    }


}
