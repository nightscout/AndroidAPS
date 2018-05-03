package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;

/**
 * Created by mike on 30.06.2016.
 */
public class MsgCheckValue_v2 extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgCheckValue_v2.class);

    public MsgCheckValue_v2() {
        SetCommand(0xF0F1);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();

        pump.isNewPump = true;
        log.debug("New firmware confirmed");

        pump.model = intFromBuff(bytes, 0, 1);
        pump.protocol = intFromBuff(bytes, 1, 1);
        pump.productCode = intFromBuff(bytes, 2, 1);
        if (pump.model != DanaRPump.EXPORT_MODEL) {
            pump.lastConnection = 0;
            Notification notification = new Notification(Notification.WRONG_DRIVER, MainApp.gs(R.string.pumpdrivercorrected), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
            MainApp.getSpecificPlugin(DanaRPlugin.class).disconnect("Wrong Model");
            log.debug("Wrong model selected. Switching to Korean DanaR");
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

            MainApp.getConfigBuilder().storeSettings("ChangingDanaRv2Driver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            return;
        }

        if (pump.protocol != 2) {
            pump.lastConnection = 0;
            Notification notification = new Notification(Notification.WRONG_DRIVER, MainApp.gs(R.string.pumpdrivercorrected), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
            DanaRKoreanPlugin.getPlugin().disconnect("Wrong Model");
            log.debug("Wrong model selected. Switching to non APS DanaR");
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setPluginEnabled(PluginType.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setFragmentVisible(PluginType.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PUMP, true);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentVisible(PluginType.PUMP, true);

            //If profile coming from pump, switch it as well
            if (MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginType.PROFILE)) {
                (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setPluginEnabled(PluginType.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PROFILE, true);
            }

            MainApp.getConfigBuilder().storeSettings("ChangingDanaRv2Driver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            return;
        }
        if (Config.logDanaMessageDetail) {
            log.debug("Model: " + String.format("%02X ", pump.model));
            log.debug("Protocol: " + String.format("%02X ", pump.protocol));
            log.debug("Product Code: " + String.format("%02X ", pump.productCode));
        }
    }

}
