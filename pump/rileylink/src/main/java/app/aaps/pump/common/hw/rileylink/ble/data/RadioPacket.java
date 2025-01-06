package app.aaps.pump.common.hw.rileylink.ble.data;

import org.apache.commons.lang3.NotImplementedException;

import javax.inject.Inject;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil;
import dagger.android.HasAndroidInjector;
import app.aaps.pump.common.utils.CRC;

/**
 * Created by geoff on 5/22/16.
 */

public class RadioPacket {

    private final byte[] pkt;
    @Inject RileyLinkUtil rileyLinkUtil;


    public RadioPacket(HasAndroidInjector injector, byte[] pkt) {
        injector.androidInjector().inject(this);
        this.pkt = pkt;
    }


    public byte[] getRaw() {
        return pkt;
    }


    private byte[] getWithCRC() {
        return ByteUtil.INSTANCE.concat(pkt, CRC.crc8(pkt));
    }


    public byte[] getEncoded() {

        switch (rileyLinkUtil.getEncoding()) {
            case Manchester: { // We have this encoding in RL firmware
                return pkt;
            }

            case FourByteSixByteLocal: {
                byte[] withCRC = getWithCRC();

                byte[] encoded = rileyLinkUtil.getEncoding4b6b().encode4b6b(withCRC);
                return ByteUtil.INSTANCE.concat(encoded, (byte) 0);
            }

            case FourByteSixByteRileyLink: {
                return getWithCRC();
            }

            default:
                throw new NotImplementedException(("Encoding not supported: " + rileyLinkUtil.getEncoding().toString()));
        }
    }
}
