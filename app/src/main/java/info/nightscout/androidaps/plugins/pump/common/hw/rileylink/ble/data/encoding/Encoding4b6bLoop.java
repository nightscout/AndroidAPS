package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by andy on 11/24/18.
 */

public class Encoding4b6bLoop extends Encoding4b6bAbstract {
    private static final Logger log = LoggerFactory.getLogger(Encoding4b6bLoop.class);

    public static final Logger LOG = LoggerFactory.getLogger(Encoding4b6bLoop.class);
    public Map<Integer, Byte> codesRev = null;


    public Encoding4b6bLoop() {
        createCodeRev();
    }


    /**
     * This method is almost 1:1 with same method from Loop, only change is unsigning of element and |05 added for
     * last byte. It should work better than original one, which is really different than this one.
     *
     * @param data
     * @return
     */
    public byte[] encode4b6b(byte[] data) {

        List<Byte> buffer = new ArrayList<Byte>();
        int bitAccumulator = 0x0;
        int bitcount = 0;

        for (byte element : data) {

            short element2 = element;

            if (element2 < 0) {
                element2 += 256;
            }

            bitAccumulator <<= 6;
            bitAccumulator |= encode4b6bList[element2 >> 4];
            bitcount += 6;

            bitAccumulator <<= 6;
            bitAccumulator |= encode4b6bList[element2 & 0x0f];
            bitcount += 6;

            while (bitcount >= 8) {
                buffer.add((byte)((bitAccumulator >> (bitcount - 8)) & 0xff));
                bitcount -= 8;
                bitAccumulator &= (0xffff >> (16 - bitcount));
            }
        }

        if (bitcount > 0) {
            bitAccumulator <<= (8 - bitcount);
            buffer.add((byte)((bitAccumulator | 0x5) & 0xff));
        }

        return ByteUtil.getByteArrayFromList(buffer);
    }


    /**
     * DOESN'T WORK YET
     * 
     * @param data
     * @return
     * @throws RileyLinkCommunicationException
     */
    public byte[] decode4b6b(byte[] data) throws RileyLinkCommunicationException {
        List<Byte> buffer = new ArrayList<Byte>();
        int availBits = 0;
        int bitAccumulator = 0;

        for (byte element2 : data) {

            short element = convertUnsigned(element2);

            // if (element < 0) {
            // element += 255;
            // }

            if (element == 0) {
                break;
            }

            bitAccumulator = (bitAccumulator << 8) + element;
            availBits += 8;

            if (availBits >= 12) {

                int hiNibble;
                int loNibble;

                try {
                    int index = (bitAccumulator >> (availBits - 6));
                    int index2 = ((bitAccumulator >> (availBits - 12)) & 0b111111);
                    hiNibble = codesRev.get((bitAccumulator >> (availBits - 6)));
                    loNibble = codesRev.get(((bitAccumulator >> (availBits - 12)) & 0b111111));
                } catch (Exception e) {
                    log.error("Unhandled exception", e);
                    return null;
                }

                int decoded = ((hiNibble << 4) + loNibble);
                buffer.add((byte)decoded);
                availBits -= 12;
                bitAccumulator = bitAccumulator & (0xffff >> (16 - availBits));
            }
        }

        return ByteUtil.getByteArrayFromList(buffer);
    }


    private void createCodeRev() {
        codesRev = new HashMap<>();

        for (int i = 0; i < encode4b6bList.length; i++) {
            codesRev.put(i, encode4b6bList[i]);
        }
    }

}
