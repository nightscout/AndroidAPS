package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE;

import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.CRC;

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
    public byte[] getEncoded() {
        byte[] withCRC = ByteUtil.concat(pkt, CRC.crc8(pkt));
        byte[] encoded = RFTools.encode4b6b(withCRC);
        byte[] withNullTerm = ByteUtil.concat(encoded,(byte)0);
        return withNullTerm;
    }
}
