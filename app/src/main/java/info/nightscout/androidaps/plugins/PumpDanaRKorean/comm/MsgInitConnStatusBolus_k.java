package info.nightscout.androidaps.plugins.PumpDanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusBolus_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBolus_k.class);

    public MsgInitConnStatusBolus_k() {
        SetCommand(0x0302);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 < 13) {
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();
        int bolusConfig = intFromBuff(bytes, 0, 1);
        pump.isExtendedBolusEnabled = (bolusConfig & 0x01) != 0;

        pump.bolusStep = intFromBuff(bytes, 1, 1) / 100d;
        pump.maxBolus = intFromBuff(bytes, 2, 2) / 100d;
        //int bolusRate = intFromBuff(bytes, 4, 8);
        int deliveryStatus = intFromBuff(bytes, 12, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Is Extended bolus enabled: " + pump.isExtendedBolusEnabled);
            log.debug("Bolus increment: " + pump.bolusStep);
            log.debug("Bolus max: " + pump.maxBolus);
            log.debug("Delivery status: " + deliveryStatus);
        }

        if (!pump.isExtendedBolusEnabled) {
            Notification notification = new Notification(Notification.EXTENDED_BOLUS_DISABLED, MainApp.gs(R.string.danar_enableextendedbolus), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.EXTENDED_BOLUS_DISABLED));
        }
    }
}
