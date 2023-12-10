package app.aaps.pump.equil.manager.command;


import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.equil.database.EquilHistoryRecord;
import app.aaps.pump.equil.manager.Utils;


public class CmdDevicesGet extends BaseSetting {
    public CmdDevicesGet() {
        super(System.currentTimeMillis());
        port = "0000";
    }

    @Override
    public byte[] getFirstData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x02, 0x00};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    public byte[] getNextData() {
        byte[] indexByte = Utils.intToBytes(pumpReqIndex);
        byte[] data2 = new byte[]{0x00, 0x00, 0x01};
        byte[] data = Utils.concat(indexByte, data2);
        pumpReqIndex++;
        return data;
    }

    @Override
    public void decodeConfirmData(byte[] data) {
        int value = Utils.bytesToInt(data[7], data[6]);

        String firmwareVersion = data[18] + "." + data[19];
        aapsLogger.debug(LTag.PUMPCOMM, "CmdGetDevices====" +
                Utils.bytesToHex(data) + "=====" + value + "===" + firmwareVersion);
        equilManager.setFirmwareVersion(firmwareVersion);
        synchronized (this) {
            setCmdStatus(true);
            notify();
        }
    }

    @Override public EquilHistoryRecord.EventType getEventType() {
        return EquilHistoryRecord.EventType.READ_DEVICES;
    }
}
