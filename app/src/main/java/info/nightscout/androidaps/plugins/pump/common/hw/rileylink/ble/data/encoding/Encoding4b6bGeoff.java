package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by andy on 11/24/18.
 */

public class Encoding4b6bGeoff extends Encoding4b6bAbstract {

    public static final Logger LOG = LoggerFactory.getLogger(Encoding4b6bGeoff.class);


    public byte[] encode4b6b(byte[] data) {
        // if ((data.length % 2) != 0) {
        // LOG.error("Warning: data is odd number of bytes");
        // }
        // use arraylists because byte[] is annoying.
        List<Byte> inData = ByteUtil.getListFromByteArray(data);
        List<Byte> outData = new ArrayList<>();

        int acc = 0;
        int bitcount = 0;
        int i;
        for (i = 0; i < inData.size(); i++) {
            acc <<= 6;
            acc |= encode4b6bList[(inData.get(i) >> 4) & 0x0f];
            bitcount += 6;

            acc <<= 6;
            acc |= encode4b6bList[inData.get(i) & 0x0f];
            bitcount += 6;

            while (bitcount >= 8) {
                byte outByte = (byte) (acc >> (bitcount - 8) & 0xff);
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
                byte outByte = (byte) ((acc >> (bitcount - 8)) & 0xff);
                outData.add(outByte);
                bitcount -= 8;
                // acc &= (0xffff >> (16 - bitcount));
            }
            while (bitcount >= 8) {
                outData.add((byte) 0);
                bitcount -= 8;
            }
        }

        // convert back to byte[]
        byte[] rval = ByteUtil.getByteArrayFromList(outData);

        return rval;

    }


    /**
     * Decode by Geoff
     *
     * @param raw
     * @return
     * @throws NumberFormatException
     */
    public byte[] decode4b6b(byte[] raw) throws RileyLinkCommunicationException {

        StringBuilder errorMessageBuilder = new StringBuilder();

        errorMessageBuilder.append("Input data: " + ByteUtil.shortHexString(raw) + "\n");

        if ((raw.length % 2) != 0) {
            errorMessageBuilder.append("Warn: odd number of bytes.\n");
        }

        byte[] rval = new byte[]{};
        int availableBits = 0;
        int codingErrors = 0;
        int x = 0;
        // Log.w(TAG,"decode4b6b: untested code");
        // Log.w(TAG,String.format("Decoding %d bytes: %s",raw.length,ByteUtil.shortHexString(raw)));
        for (int i = 0; i < raw.length; i++) {
            int unsignedValue = raw[i];
            if (unsignedValue < 0) {
                unsignedValue += 256;
            }
            x = (x << 8) + unsignedValue;
            availableBits += 8;
            if (availableBits >= 12) {
                // take top six
                int highcode = (x >> (availableBits - 6)) & 0x3F;
                int highIndex = encode4b6bListIndex((byte) (highcode));
                // take bottom six
                int lowcode = (x >> (availableBits - 12)) & 0x3F;
                int lowIndex = encode4b6bListIndex((byte) (lowcode));
                // special case at end of transmission on uneven boundaries:
                if ((highIndex >= 0) && (lowIndex >= 0)) {
                    byte decoded = (byte) ((highIndex << 4) + lowIndex);
                    rval = ByteUtil.concat(rval, decoded);
                    /*
                     * LOG.debug(String.format(
                     * "i=%d,x=0x%08X,0x%02X->0x%02X, 0x%02X->0x%02X, result: 0x%02X, %d bits remaining, errors %d, bytes remaining: %s"
                     * ,
                     * i,x,highcode,highIndex, lowcode,
                     * lowIndex,decoded,availableBits,codingErrors,ByteUtil.shortHexString
                     * (ByteUtil.substring(raw,i+1,raw.length-i-1))));
                     */
                } else {
                    // LOG.debug(String.format("i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining",i,x,highcode,lowcode,availableBits));
                    errorMessageBuilder.append(String.format(
                            "decode4b6b: i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining.\n",
                            i, x, highcode, lowcode, availableBits));
                    codingErrors++;
                }

                availableBits -= 12;
                x = x & (0x0000ffff >> (16 - availableBits));
            }
        }

        if (availableBits != 0) {
            if ((availableBits == 4) && (x == 0x05)) {
                // normal end
            } else {
                // LOG.error("decode4b6b: failed clean decode -- extra bits available (not marker)(" + availableBits +
                // ")");
                errorMessageBuilder.append("decode4b6b: failed clean decode -- extra bits available (not marker)("
                        + availableBits + ")\n");
                codingErrors++;
            }
        } else {
            // also normal end.
        }

        if (codingErrors > 0) {
            // LOG.error("decode4b6b: " + codingErrors + " coding errors encountered.");
            errorMessageBuilder.append("decode4b6b: " + codingErrors + " coding errors encountered.");
            writeError(LOG, raw, errorMessageBuilder.toString());
            throw new RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, errorMessageBuilder.toString());
        }
        return rval;
    }

    // public static RFTools.DecodeResponseDto decode4b6bWithoutException(byte[] raw) {
    // /*
    // * if ((raw.length % 2) != 0) {
    // * LOG.error("Warning: data is odd number of bytes");
    // * }
    // */
    //
    // RFTools.DecodeResponseDto response = new RFTools.DecodeResponseDto();
    //
    // StringBuilder errorMessageBuilder = new StringBuilder();
    //
    // errorMessageBuilder.append("Input data: " + ByteUtil.getHex(raw) + "\n");
    //
    // if ((raw.length % 2) != 0) {
    // errorMessageBuilder.append("Warn: odd number of bytes.");
    // }
    //
    // byte[] rval = new byte[] {};
    // int availableBits = 0;
    // int codingErrors = 0;
    // int x = 0;
    // // Log.w(TAG,"decode4b6b: untested code");
    // // Log.w(TAG,String.format("Decoding %d bytes: %s",raw.length,ByteUtil.shortHexString(raw)));
    // for (int i = 0; i < raw.length; i++) {
    // int unsignedValue = raw[i];
    // if (unsignedValue < 0) {
    // unsignedValue += 256;
    // }
    // x = (x << 8) + unsignedValue;
    // availableBits += 8;
    // if (availableBits >= 12) {
    // // take top six
    // int highcode = (x >> (availableBits - 6)) & 0x3F;
    // int highIndex = encode4b6bListIndex((byte)(highcode));
    // // take bottom six
    // int lowcode = (x >> (availableBits - 12)) & 0x3F;
    // int lowIndex = encode4b6bListIndex((byte)(lowcode));
    // // special case at end of transmission on uneven boundaries:
    // if ((highIndex >= 0) && (lowIndex >= 0)) {
    // byte decoded = (byte)((highIndex << 4) + lowIndex);
    // rval = ByteUtil.concat(rval, decoded);
    // /*
    // * LOG.debug(String.format(
    // *
    // "i=%d,x=0x%08X,0x%02X->0x%02X, 0x%02X->0x%02X, result: 0x%02X, %d bits remaining, errors %d, bytes remaining: %s"
    // * ,
    // * i,x,highcode,highIndex, lowcode,
    // * lowIndex,decoded,availableBits,codingErrors,ByteUtil.shortHexString
    // * (ByteUtil.substring(raw,i+1,raw.length-i-1))));
    // */
    // } else {
    // //
    // LOG.debug(String.format("i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining",i,x,highcode,lowcode,availableBits));
    // errorMessageBuilder.append(String.format(
    // "decode4b6b: i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining.\n",
    // i, x, highcode, lowcode, availableBits));
    // codingErrors++;
    // }
    //
    // availableBits -= 12;
    // x = x & (0x0000ffff >> (16 - availableBits));
    // } else {
    // // LOG.debug(String.format("i=%d, skip: x=0x%08X, available bits %d",i,x,availableBits));
    // }
    // }
    //
    // if (availableBits != 0) {
    // if ((availableBits == 4) && (x == 0x05)) {
    // // normal end
    // } else {
    // LOG.error("decode4b6b: failed clean decode -- extra bits available (not marker)(" + availableBits + ")");
    // errorMessageBuilder.append("decode4b6b: failed clean decode -- extra bits available (not marker)("
    // + availableBits + ")\n");
    // codingErrors++;
    // }
    // } else {
    // // also normal end.
    // }
    //
    // if (codingErrors > 0) {
    // LOG.error("decode4b6b: " + codingErrors + " coding errors encountered.");
    // errorMessageBuilder.append("decode4b6b: " + codingErrors + " coding errors encountered.");
    //
    // response.errorData = errorMessageBuilder.toString();
    // } else {
    // response.data = rval;
    // }
    //
    // return response;
    // }

}
