package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

public class CmdLargeBasalSet extends BaseSetting {
    double insulin;
    int step;
    int stepTime;

    public int getStepTime() {
        return stepTime;
    }


    public CmdLargeBasalSet(double insulin) {
        this.insulin = insulin;
        if (insulin != 0) {
            step = (int) (insulin / 0.05d * 8);
            stepTime = (int) (insulin / 0.05d * 2);
        }
    }

    @Override
    public byte[] getFirstData() {
        aapsLogger.error(LTag.EQUILBLE, "step===" + step + "=====" + stepTime);
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x01, 0x03};
        byte[] data3 = Utils.intToBytes(step);
        byte[] data4 = Utils.intToBytes(stepTime);
        byte[] data5 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3, data4, data5, data5);
        index2++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x00, 0x03, 0x01};
        byte[] data3 = Utils.intToBytes(0);
        byte[] data = Utils.concat(indexByte, data2, data3);
        index2++;
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
