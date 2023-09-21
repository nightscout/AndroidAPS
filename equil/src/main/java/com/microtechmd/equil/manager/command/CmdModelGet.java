package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

public class CmdModelGet extends BaseSetting {
    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x02, 0x00};
        byte[] data = Utils.concat(indexByte, data2);
        reqCmdIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(reqCmdIndex);
        byte[] data2 = new byte[]{0x00, 0x02, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        reqCmdIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {

        int mode = data[6] & 0xff;
        aapsLogger.debug(LTag.EQUILBLE, "CmdGetModel====" + mode);
        equilManager.setModel(mode);
        cmdStatus = true;
        synchronized (this) {
            notify();
        }

    }
}
