package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

public class CmdDevicesGet extends BaseSetting {
    public CmdDevicesGet() {
        port = "0000";
    }

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
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        reqCmdIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {

        String firmwareVersion = data[18] + "." + data[19];
        equilManager.setFirmwareVersion(firmwareVersion);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
