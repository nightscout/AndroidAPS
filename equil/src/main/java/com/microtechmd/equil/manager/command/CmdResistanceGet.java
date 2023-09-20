package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdResistanceGet extends BaseSetting {
    public CmdResistanceGet() {
        port = "1515";
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(index2);
        byte[] data2 = new byte[]{0x02, 0x02};
        byte[] data = Utils.concat(indexByte, data2);
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

    @Override
    public void decodeConfirmData(byte[] data) {
        int value = Utils.bytesToInt(data[7], data[6]);
        cmdStatus = true;
        if (value < 500) {
            setEnacted(false);
        } else {
            setEnacted(true);
        }
        synchronized (this) {
            notify();
        }
    }
}
