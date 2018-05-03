package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;

public class MsgInitConnStatusBasic_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBasic_k.class);

    public MsgInitConnStatusBasic_k() {
        SetCommand(0x0303);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 > 6) {
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();
        pump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1;
        int isUtilityEnable = intFromBuff(bytes, 1, 1);
        pump.isEasyModeEnabled = intFromBuff(bytes, 2, 1) == 1;
        int easyUIMode = intFromBuff(bytes, 3, 1);
        pump.password = intFromBuff(bytes, 4, 2) ^ 0x3463;
        if (Config.logDanaMessageDetail) {
            log.debug("isStatusSuspendOn: " + pump.pumpSuspended);
            log.debug("isUtilityEnable: " + isUtilityEnable);
            log.debug("Is EasyUI Enabled: " + pump.isEasyModeEnabled);
            log.debug("easyUIMode: " + easyUIMode);
            log.debug("Pump password: " + pump.password);
        }

        if (pump.isEasyModeEnabled) {
            Notification notification = new Notification(Notification.EASYMODE_ENABLED, MainApp.gs(R.string.danar_disableeasymode), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.EASYMODE_ENABLED));
        }
    }
}
