package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_General_Get_Pump_Check extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_General_Get_Pump_Check.class);

    public DanaRS_Packet_General_Get_Pump_Check() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK;
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        pump.model = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.protocol = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.productCode = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Model: " + String.format("%02X ", pump.model));
            log.debug("Protocol: " + String.format("%02X ", pump.protocol));
            log.debug("Product Code: " + String.format("%02X ", pump.productCode));
        }

        if (pump.productCode < 2) {
            MainApp.bus().post(new EventNewNotification(new Notification(Notification.UNSUPPORTED_FIRMWARE, MainApp.gs(R.string.unsupportedfirmware), Notification.URGENT)));
        }
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__GET_PUMP_CHECK";
    }
}
