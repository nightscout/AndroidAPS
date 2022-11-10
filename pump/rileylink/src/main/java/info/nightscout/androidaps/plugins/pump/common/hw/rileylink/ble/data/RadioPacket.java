package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

import org.apache.commons.lang3.NotImplementedException;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;
import info.nightscout.pump.core.utils.ByteUtil;

/**
 * Created by geoff on 5/22/16.
 */

public class RadioPacket {

    @Inject RileyLinkUtil rileyLinkUtil;

    private final byte[] pkt;


    public RadioPacket(HasAndroidInjector injector, byte[] pkt) {
        injector.androidInjector().inject(this);
        this.pkt = pkt;
    }


    public byte[] getRaw() {
        return pkt;
    }


    private byte[] getWithCRC() {
        byte[] withCRC = ByteUtil.concat(pkt, CRC.crc8(pkt));
        return withCRC;
    }


    public byte[] getEncoded() {

        switch (rileyLinkUtil.getEncoding()) {
            case Manchester: { // We have this encoding in RL firmware
                return pkt;
            }

            case FourByteSixByteLocal: {
                byte[] withCRC = getWithCRC();

                byte[] encoded = rileyLinkUtil.getEncoding4b6b().encode4b6b(withCRC);
                return ByteUtil.concat(encoded, (byte) 0);
            }

            case FourByteSixByteRileyLink: {
                return getWithCRC();
            }

            default:
                throw new NotImplementedException(("Encoding not supported: " + rileyLinkUtil.getEncoding().toString()));
        }
    }
}
