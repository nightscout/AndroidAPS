package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusOption extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgInitConnStatusOption() {
        SetCommand(0x0304);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int status1224Clock = intFromBuff(bytes, 0, 1);
        int isStatusButtonScroll = intFromBuff(bytes, 1, 1);
        int soundVibration = intFromBuff(bytes, 2, 1);
        int glucoseUnit = intFromBuff(bytes, 3, 1);
        int lcdTimeout = intFromBuff(bytes, 4, 1);
        int backlightgTimeout = intFromBuff(bytes, 5, 1);
        int languageOption = intFromBuff(bytes, 6, 1);
        int lowReservoirAlarmBoundary = intFromBuff(bytes, 7, 1);
        //int none = intFromBuff(bytes, 8, 1);
        if (bytes.length >= 21) {
            DanaRPump.getInstance().password = intFromBuff(bytes, 9, 2) ^ 0x3463;
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Pump password: " + DanaRPump.getInstance().password);
        } else {
            failed = true;
        }

        if (!DanaRPump.getInstance().isPasswordOK()) {
            Notification notification = new Notification(Notification.WRONG_PUMP_PASSWORD, MainApp.gs(R.string.wrongpumppassword), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.WRONG_PUMP_PASSWORD));
        }

        // This is last message of initial sequence
        if (ConfigBuilderPlugin.getPlugin().getActivePump() != null )
            ConfigBuilderPlugin.getPlugin().getActivePump().finishHandshaking();
    }

}
