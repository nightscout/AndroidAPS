package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Notify_Missed_Bolus_Alarm extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public DanaRS_Packet_Notify_Missed_Bolus_Alarm() {
        super();
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY;
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] data) {
        int startHour;
        int startMin;
        int endHour;
        int endMin;

        int dataIndex = DATA_START;
        int dataSize = 1;
        startHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        startMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        endHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        endMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if(endMin == 1 && endMin == endHour && startHour == endHour &&startHour == startMin )
            failed = true;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Start hour: " + startHour);
            log.debug("Start min: " + startMin);
            log.debug("End hour: " + endHour);
            log.debug("End min: " + endMin);
        }
    }

    @Override
    public String getFriendlyName() {
        return "NOTIFY__MISSED_BOLUS_ALARM";
    }

}
