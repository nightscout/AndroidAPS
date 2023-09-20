package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdTimeGet extends BaseSetting {
    public CmdTimeGet() {
        port = "0505";
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x02, 0x00};
        byte[] data = Utils.concat(indexByte, data2);
        index2++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        index2++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
//        67631679050017070e101319
        int index = data[4];
        int year = data[6] & 0xff;
        int month = data[7] & 0xff;
        int day = data[8] & 0xff;
        int hour = data[9] & 0xff;
        int min = data[10] & 0xff;
        int s = data[11] & 0xff;
    }
}
