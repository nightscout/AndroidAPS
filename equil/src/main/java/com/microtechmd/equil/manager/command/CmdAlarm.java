package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdAlarm extends BaseSetting {
    int mode;

    public CmdAlarm(int mode) {
        this.mode = mode;
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x01, 0x0b};
        byte[] data3 = Utils.intToBytes(mode);
        byte[] data = Utils.concat(indexByte, data2, data3);
        reqCmdIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x00, 0x0b, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        reqCmdIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
