package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;

public class DanaRS_Packet_Notify_Alarm extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private int alarmCode;

    public DanaRS_Packet_Notify_Alarm() {
        super();
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY;
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__ALARM;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        alarmCode = byteArrayToInt(getBytes(data, DATA_START, 1));
        String errorString = "";

        switch (alarmCode) {
            case 0x01:
                // Battery 0% Alarm
                errorString = MainApp.gs(R.string.batterydischarged);
                break;
            case 0x02:
                // Pump Error
                errorString = MainApp.gs(R.string.pumperror) + " " + alarmCode;
                break;
            case 0x03:
                // Occlusion
                errorString = MainApp.gs(R.string.occlusion);
                break;
            case 0x04:
                // LOW BATTERY
                errorString = MainApp.gs(R.string.lowbattery);
                break;
            case 0x05:
                // Shutdown
                errorString = MainApp.gs(R.string.lowbattery);
                break;
            case 0x06:
                // Basal Compare
                errorString = "BasalCompare ????";
                break;
            case 0x09:
                // Empty Reservoir
                errorString = MainApp.gs(R.string.emptyreservoir);
                break;

            // BT
            case 0x07:
            case 0xFF:
                // Blood sugar measurement alert
                errorString = MainApp.gs(R.string.bloodsugarmeasurementalert);
                break;

            case 0x08:
            case 0xFE:
                // Remaining insulin level
                errorString = MainApp.gs(R.string.remaininsulinalert);
                break;

            case 0xFD:
                // Blood sugar check miss alarm
                errorString = "Blood sugar check miss alarm ???";
                break;
        }
        // No error no need to upload anything
        if(errorString == "") {
            failed = true;

            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Error detected: " + errorString);
            return;
        }
        NSUpload.uploadError(errorString);
    }

    @Override
    public String getFriendlyName() {
        return "NOTIFY__ALARM";
    }
}
