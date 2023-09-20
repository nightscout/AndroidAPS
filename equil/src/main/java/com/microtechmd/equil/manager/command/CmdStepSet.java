package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdStepSet extends BaseSetting {
    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x01, 0x07};
        byte[] data3 = Utils.intToBytes(120);
        byte[] data = Utils.concat(indexByte, data2, data3);
        index2++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x07, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        index2++;
        return data;
    }

    public void decodeConfirmData(byte[] data) {
//        byte[] byteData = Crc.hexStringToBytes(data);
//        int status = byteData[6] & 0xff;
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
