package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.utils.DateUtil;

public class MsgInitConnStatusTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgInitConnStatusTime() {
        SetCommand(0x0301);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 > 7) {
            Notification notification = new Notification(Notification.WRONG_DRIVER, MainApp.gs(R.string.pumpdrivercorrected), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
            MainApp.getSpecificPlugin(DanaRPlugin.class).disconnect("Wrong Model");
            log.error("Wrong model selected. Switching to Korean DanaR");
            MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).setPluginEnabled(PluginType.PUMP, true);
            MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).setFragmentVisible(PluginType.PUMP, true);
            MainApp.getSpecificPlugin(DanaRPlugin.class).setPluginEnabled(PluginType.PUMP, false);
            MainApp.getSpecificPlugin(DanaRPlugin.class).setFragmentVisible(PluginType.PUMP, false);

            DanaRPump.reset(); // mark not initialized

            //If profile coming from pump, switch it as well
            if (MainApp.getSpecificPlugin(DanaRPlugin.class).isEnabled(PluginType.PROFILE)) {
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).setPluginEnabled(PluginType.PROFILE, true);
            }

            ConfigBuilderPlugin.getPlugin().storeSettings("ChangingDanaDriver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            failed = false;
            return;
        } else {
            failed = true;
        }

        long time = dateTimeSecFromBuff(bytes, 0);
        int versionCode = intFromBuff(bytes, 6, 1);

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump time: " + DateUtil.dateAndTimeFullString(time));
            log.debug("Version code: " + versionCode);
        }
    }
}
