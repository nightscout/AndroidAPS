package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdTmepBasalGet extends BaseSetting {
    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x02, 0x02};
        byte[] data3 = Utils.intToBytes(120);
        byte[] data4 = Utils.intToBytes(120);
        byte[] data = Utils.concat(indexByte, data2, data3, data4);
        index2++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x02, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        index2++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {

    }
}
