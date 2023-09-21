package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdExtendedBolusSet extends BaseSetting {
    double insulin;
    int step;
    int time;


    public CmdExtendedBolusSet(double insulin, int time) {
        this.insulin = insulin;
        if (insulin != 0) {
            step = (int) (insulin / 0.05d * 8);
            this.time = time * 60;
        }
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x01, 0x03};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data4 = Utils.intToBytes(time);
        byte[] data5 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data5, data5, data3, data4);
        reqCmdIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x00, 0x03, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        reqCmdIndex++;
        return data;
    }
    public void decodeConfirmData(byte[] data) {
        int index = data[4];
        int status = data[6] & 0xff;
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
