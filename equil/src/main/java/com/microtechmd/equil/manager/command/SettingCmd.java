package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class SettingCmd extends BaseSetting {
    public static final String TAG = "SettingCmd";


    public byte[] getData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] tzm = Utils.hexStringToBytes(getEquilDevices());
        byte[] data = Utils.concat(indexByte, tzm);
        reqCmdIndex++;
        return data;
    }


    public byte[] getData2() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x01, 0x07};
        byte[] data3 = Utils.intToBytes(120);
        byte[] data = Utils.concat(indexByte, data2, data3);
        reqCmdIndex++;
        return data;
    }

    @Override public byte[] getFirstData() {
        return new byte[0];
    }

    @Override public byte[] getNextData() {
        return new byte[0];
    }

    @Override public void decodeConfirmData(byte[] data) {

    }

    public byte[] getData3() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x00, 0x07, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        reqCmdIndex++;
        return data;
    }

}
