package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class DanaRS_Packet_Bolus_Get_Bolus_Option extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Get_Bolus_Option.class);

    public DanaRS_Packet_Bolus_Get_Bolus_Option() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION;
    }

    @Override
    public void handleMessage(byte[] data) {
        DanaRPump pump = DanaRPump.getInstance();

        int dataIndex = DATA_START;
        int dataSize = 1;
        pump.isExtendedBolusEnabled = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 1;

        dataIndex += dataSize;
        dataSize = 1;
        pump.bolusCalculationOption = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        pump.missedBolusConfig = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus01StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus01StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus01EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus01EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus02StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus02StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus02EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus02EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus03StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus03StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus03EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus03EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus04StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus04StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus04EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        dataIndex += dataSize;
        dataSize = 1;
        int missedBolus04EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (!pump.isExtendedBolusEnabled) {
            Notification notification = new Notification(Notification.EXTENDED_BOLUS_DISABLED, MainApp.gs(R.string.danar_enableextendedbolus), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.EXTENDED_BOLUS_DISABLED));
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Extended bolus enabled: " + pump.isExtendedBolusEnabled);
            log.debug("Missed bolus config: " + pump.missedBolusConfig);
            log.debug("missedBolus01StartHour: " + missedBolus01StartHour);
            log.debug("missedBolus01StartMin: " + missedBolus01StartMin);
            log.debug("missedBolus01EndHour: " + missedBolus01EndHour);
            log.debug("missedBolus01EndMin: " + missedBolus01EndMin);
            log.debug("missedBolus02StartHour: " + missedBolus02StartHour);
            log.debug("missedBolus02StartMin: " + missedBolus02StartMin);
            log.debug("missedBolus02EndHour: " + missedBolus02EndHour);
            log.debug("missedBolus02EndMin: " + missedBolus02EndMin);
            log.debug("missedBolus03StartHour: " + missedBolus03StartHour);
            log.debug("missedBolus03StartMin: " + missedBolus03StartMin);
            log.debug("missedBolus03EndHour: " + missedBolus03EndHour);
            log.debug("missedBolus03EndMin: " + missedBolus03EndMin);
            log.debug("missedBolus04StartHour: " + missedBolus04StartHour);
            log.debug("missedBolus04StartMin: " + missedBolus04StartMin);
            log.debug("missedBolus04EndHour: " + missedBolus04EndHour);
            log.debug("missedBolus04EndMin: " + missedBolus04EndMin);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__GET_BOLUS_OPTION";
    }
}
