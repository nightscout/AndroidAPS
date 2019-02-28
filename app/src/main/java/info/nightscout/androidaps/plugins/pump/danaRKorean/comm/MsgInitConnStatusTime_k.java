package info.nightscout.androidaps.plugins.pump.danaRKorean.comm;

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
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.utils.DateUtil;

public class MsgInitConnStatusTime_k extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgInitConnStatusTime_k() {
        SetCommand(0x0301);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {

        if (bytes.length - 10 < 10) {
            Notification notification = new Notification(Notification.WRONG_DRIVER, MainApp.gs(R.string.pumpdrivercorrected), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
            DanaRKoreanPlugin.getPlugin().disconnect("Wrong Model");
            log.error("Wrong model selected. Switching to export DanaR");
            MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).setPluginEnabled(PluginType.PUMP, false);
            MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).setFragmentVisible(PluginType.PUMP, false);
            MainApp.getSpecificPlugin(DanaRPlugin.class).setPluginEnabled(PluginType.PUMP, true);
            MainApp.getSpecificPlugin(DanaRPlugin.class).setFragmentVisible(PluginType.PUMP, true);

            DanaRPump.reset(); // mark not initialized

            //If profile coming from pump, switch it as well
            if (MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).isEnabled(PluginType.PROFILE)) {
                (MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).setPluginEnabled(PluginType.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PROFILE, true);
            }

            ConfigBuilderPlugin.getPlugin().storeSettings("ChangingKoreanDanaDriver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            return;
        }

        long time = dateTimeSecFromBuff(bytes, 0);
        int versionCode1 = intFromBuff(bytes, 6, 1);
        int versionCode2 = intFromBuff(bytes, 7, 1);
        int versionCode3 = intFromBuff(bytes, 8, 1);
        int versionCode4 = intFromBuff(bytes, 9, 1);

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Pump time: " + DateUtil.dateAndTimeFullString(time));
            log.debug("Version code1: " + versionCode1);
            log.debug("Version code2: " + versionCode2);
            log.debug("Version code3: " + versionCode3);
            log.debug("Version code4: " + versionCode4);
        }
    }
}
