package info.nightscout.androidaps.plugins.pump.danaRKorean.comm;

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
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusBolus_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgInitConnStatusBolus_k() {
        SetCommand(0x0302);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
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

        if (L.isEnabled(L.PUMPCOMM)) {
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

        // This is last message of initial sequence
        if (ConfigBuilderPlugin.getPlugin().getActivePump() != null)
            ConfigBuilderPlugin.getPlugin().getActivePump().finishHandshaking();
    }
}
