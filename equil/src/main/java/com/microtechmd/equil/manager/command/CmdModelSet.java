package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdModelSet extends BaseSetting {
    int mode;
    public CmdModelSet(int mode){
        this.mode=mode;
    }
    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x01, 0x00};
        byte[] data3 = Utils.intToBytes(mode);
        byte[] data = Utils.concat(indexByte, data2, data3);
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

    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
