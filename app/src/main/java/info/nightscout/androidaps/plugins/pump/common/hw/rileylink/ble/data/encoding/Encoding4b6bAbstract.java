package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding;

import org.slf4j.Logger;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;


/**
 * Created by andy on 11/24/18.
 */

public abstract class Encoding4b6bAbstract implements Encoding4b6b {

    /**
     * encode4b6bMap is an ordered list of translations 6bits -> 4 bits, in order from 0x0 to 0xF
     * The 6 bit codes are what is used on the RF side of the RileyLink to communicate
     * with a Medtronic pump.
     */
    public static final byte[] encode4b6bList = new byte[] {
        0x15, 0x31, 0x32, 0x23, 0x34, 0x25, 0x26, 0x16, 0x1a, 0x19, 0x2a, 0x0b, 0x2c, 0x0d, 0x0e, 0x1c };


    // 21, 49, 50, 35, 52, 37, 38, 22, 26, 25, 42, 11, 44, 13, 14, 28

    public abstract byte[] encode4b6b(byte[] data);


    public abstract byte[] decode4b6b(byte[] data) throws RileyLinkCommunicationException;


    protected short convertUnsigned(byte x) {
        short ss = x;

        if (ss < 0) {
            ss += 256;
        }

        return ss;
    }


    /* O(n) lookup. Run on an O(n) translation of a byte-stream, gives O(n**2) performance. Sigh. */
    public static int encode4b6bListIndex(byte b) {
        for (int i = 0; i < encode4b6bList.length; i++) {
            if (b == encode4b6bList[i]) {
                return i;
            }
        }
        return -1;
    }


    public void writeError(Logger LOG, byte[] raw, String errorData) {

        LOG.error("\n=============================================================================\n" + //
                  " Decoded payload length is zero.\n" +
                  " encodedPayload: {}\n" +
                  " errors: {}\n" +
                  "=============================================================================", //
                ByteUtil.getHex(raw), errorData);

        //FabricUtil.createEvent("MedtronicDecode4b6bError", null);

        return;

    }

}
