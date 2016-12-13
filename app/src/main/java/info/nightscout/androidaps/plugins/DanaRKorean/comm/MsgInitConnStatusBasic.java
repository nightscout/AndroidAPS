package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;

public class MsgInitConnStatusBasic extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBasic.class);

    public MsgInitConnStatusBasic() {
        SetCommand(0x0303);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 > 6) {
            return;
        }
        DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();
        int isStatusSuspendOn = intFromBuff(bytes, 0, 1);
        int isUtilityEnable = intFromBuff(bytes, 1, 1);
        pump.isEasyModeEnabled = intFromBuff(bytes, 2, 1) == 1;
        int easyUIMode = intFromBuff(bytes, 3, 1);
        pump.password = intFromBuff(bytes, 4, 2) ^ 0x3463;
        if (Config.logDanaMessageDetail) {
            log.debug("isStatusSuspendOn: " + isStatusSuspendOn);
            log.debug("isUtilityEnable: " + isUtilityEnable);
            log.debug("Is EasyUI Enabled: " + pump.isEasyModeEnabled);
            log.debug("easyUIMode: " + easyUIMode);
            log.debug("Pump password: " + pump.password);
        }

        if (pump.isEasyModeEnabled) {
            Notification notification = new Notification(Notification.EASYMODE_ENABLED, MainApp.sResources.getString(R.string.danar_disableeasymode), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.EASYMODE_ENABLED));
        }
    }
}
