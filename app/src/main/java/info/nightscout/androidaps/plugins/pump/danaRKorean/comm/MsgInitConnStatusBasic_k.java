package info.nightscout.androidaps.plugins.pump.danaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

public class MsgInitConnStatusBasic_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgInitConnStatusBasic_k() {
        SetCommand(0x0303);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
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
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("isStatusSuspendOn: " + pump.pumpSuspended);
            log.debug("isUtilityEnable: " + isUtilityEnable);
            log.debug("Is EasyUI Enabled: " + pump.isEasyModeEnabled);
            log.debug("easyUIMode: " + easyUIMode);
            log.debug("Pump password: " + pump.password);
        }

        if (pump.isEasyModeEnabled) {
            Notification notification = new Notification(Notification.EASYMODE_ENABLED, MainApp.gs(R.string.danar_disableeasymode), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        } else {
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.EASYMODE_ENABLED));
        }

        if (!DanaRPump.getInstance().isPasswordOK()) {
            Notification notification = new Notification(Notification.WRONG_PUMP_PASSWORD, MainApp.gs(R.string.wrongpumppassword), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        } else {
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.WRONG_PUMP_PASSWORD));
        }
    }
}
