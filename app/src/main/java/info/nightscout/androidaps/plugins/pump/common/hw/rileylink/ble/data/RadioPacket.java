package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

import org.apache.commons.lang3.NotImplementedException;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;

/**
 * Created by geoff on 5/22/16.
 */

public class RadioPacket {

    protected byte[] pkt;


    public RadioPacket(byte[] pkt) {
        this.pkt = pkt;
    }


    public byte[] getRaw() {
        return pkt;
    }


    public byte[] getWithCRC() {
        byte[] withCRC = ByteUtil.concat(pkt, CRC.crc8(pkt));
        return withCRC;
    }


    public byte[] getEncoded() {

        switch (RileyLinkUtil.getEncoding()) {
            case Manchester: { // We have this encoding in RL firmware
                return pkt;
            }

            case FourByteSixByteLocal: {
                byte[] withCRC = getWithCRC();

                byte[] encoded = RileyLinkUtil.getEncoding4b6b().encode4b6b(withCRC);
                return ByteUtil.concat(encoded, (byte)0);
            }

            case FourByteSixByteRileyLink: {
                return getWithCRC();
            }

            default:
                throw new NotImplementedException(("Encoding not supported: " + RileyLinkUtil.getEncoding().toString()));
        }
    }

}
