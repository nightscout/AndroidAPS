package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by andy on 11/24/18.
 */

public class Encoding4b6bGo extends Encoding4b6bAbstract {

    public static final Logger LOG = LoggerFactory.getLogger(Encoding4b6bGo.class);
    private static Map<Short, Short> decodeGoMap;


    public byte[] encode4b6b(byte[] src) {
        // 2 input bytes produce 3 output bytes.
        // Odd final input byte, if any, produces 2 output bytes.
        int n = src.length;
        byte[] dst = new byte[3 * (n / 2) + 2 * (n % 2)];
        int j = 0;

        for (int i = 0; i < n; i += 2, j = j + 3) {
            short x = convertUnsigned(src[i]);
            short a = encode4b6bList[hi(4, x)];
            short b = encode4b6bList[lo(4, x)];
            dst[j] = (byte)(a << 2 | hi(4, b));
            if (i + 1 < n) {
                short y = convertUnsigned(src[i + 1]);
                short c = encode4b6bList[hi(4, y)];
                short d = encode4b6bList[lo(4, y)];
                dst[j + 1] = (byte)(lo(4, b) << 4 | hi(6, c));
                dst[j + 2] = (byte)(lo(2, c) << 6 | d);
            } else {
                // Fill final nibble with 5 to match pump behavior.
                dst[j + 1] = (byte)(lo(4, b) << 4 | 0x5);
            }

        }
        return dst;
    }


    /**
     * Decode from Go code by ecc1. NOT WORKING
     *
     * @param src
     * @return
     */
    public byte[] decode4b6b(byte[] src) throws RileyLinkCommunicationException {
        int n = src.length;

        if (decodeGoMap == null)
            initDecodeGo();

        StringBuilder errorMessageBuilder = new StringBuilder();

        errorMessageBuilder.append("Input data: " + ByteUtil.getHex(src) + "\n");
        int codingErrors = 0;

        // Check for valid packet length.
        if (n % 3 == 1) {
            errorMessageBuilder.append("Invalid package length " + n);
            codingErrors++;
            // return nil, ErrDecoding
        }
        // 3 input bytes produce 2 output bytes.
        // Final 2 input bytes, if any, produce 1 output byte.
        byte[] dst = new byte[2 * (n / 3) + (n % 3) / 2];

        int j = 0;
        for (int i = 0; i < n; i = i + 3, j = j + 2) {
            if (i + 1 >= n) {
                errorMessageBuilder.append("Overflow in i (" + i + ")");
            }
            short x = convertUnsigned(src[i]);
            short y = convertUnsigned(src[i + 1]);
            short a = decode6b_goMap(hi(6, x));
            short b = decode6b_goMap(lo(2, x) << 4 | hi(4, y));
            if (a == 0xFF || b == 0xFF) {
                errorMessageBuilder.append("Error decoding ");
                codingErrors++;
            }
            dst[j] = (byte)(a << 4 | b);
            if (i + 2 < n) {
                short z = convertUnsigned(src[i + 2]);
                short c = decode6b_goMap(lo(4, y) << 2 | hi(2, z));
                short d = decode6b_goMap(lo(6, z));
                if (c == 0xFF || d == 0xFF) {
                    errorMessageBuilder.append("Error decoding ");
                    codingErrors++;
                }
                dst[j + 1] = (byte)(c << 4 | d);
            }
        }

        if (codingErrors > 0) {
            errorMessageBuilder.append("decode4b6b: " + codingErrors + " coding errors encountered.");
            writeError(LOG, dst, errorMessageBuilder.toString());
            throw new RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, errorMessageBuilder.toString());
        }

        return dst;
    }


    static short hi(int n, short x) {
        // x = convertUnsigned(x);
        return (short)(x >> (8 - n));
    }


    static short lo(int n, short x) {
        // byte b = (byte)x;
        return (short)(x & ((1 << n) - 1));
    }


    public static void initDecodeGo() {

        decodeGoMap = new HashMap<>();

        putToMap(0x0B, 0x0B);
        putToMap(0x0D, 0x0D);
        putToMap(0x0E, 0x0E);
        putToMap(0x15, 0x00);
        putToMap(0x16, 0x07);
        putToMap(0x19, 0x09);
        putToMap(0x1A, 0x08);
        putToMap(0x1C, 0x0F);
        putToMap(0x23, 0x03);
        putToMap(0x25, 0x05);
        putToMap(0x26, 0x06);
        putToMap(0x2A, 0x0A);
        putToMap(0x2C, 0x0C);
        putToMap(0x31, 0x01);
        putToMap(0x32, 0x02);
        putToMap(0x34, 0x04);

    }


    private static short decode6b_goMap(int value) {
        short val = (short)value;
        if (decodeGoMap.containsKey(val))
            return decodeGoMap.get(val);
        else
            return (short)0xff;
    }


    private static void putToMap(int val1, int val2) {
        decodeGoMap.put((short)val1, (short)val2);
    }

}
