package com.microtechmd.equil.manager.command;


import com.microtechmd.equil.manager.Utils;

import info.nightscout.shared.logging.LTag;

public class CmdDevicesGet extends BaseSetting {
    public CmdDevicesGet() {
        port = "0000";
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
        int value = Utils.bytesToInt(data[7], data[6]);

        String firmwareVersion = data[18] + "." + data[19];
        aapsLogger.error(LTag.EQUILBLE, "CmdGetDevices====" +
                Utils.bytesToHex(data) + "=====" + value + "===" + firmwareVersion);
        equilManager.setFirmwareVersion(firmwareVersion);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }
}
