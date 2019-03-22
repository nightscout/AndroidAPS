package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

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
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;

/**
 * Created by mike on 30.06.2016.
 */
public class MsgCheckValue_v2 extends MessageBase {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgCheckValue_v2() {
        SetCommand(0xF0F1);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();

        pump.isNewPump = true;
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New firmware confirmed");

        pump.model = intFromBuff(bytes, 0, 1);
        pump.protocol = intFromBuff(bytes, 1, 1);
        pump.productCode = intFromBuff(bytes, 2, 1);
        if (pump.model != DanaRPump.EXPORT_MODEL) {
            pump.lastConnection = 0;
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

            ConfigBuilderPlugin.getPlugin().storeSettings("ChangingDanaRv2Driver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            return;
        }

        if (pump.protocol != 2) {
            pump.lastConnection = 0;
            Notification notification = new Notification(Notification.WRONG_DRIVER, MainApp.gs(R.string.pumpdrivercorrected), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
            DanaRKoreanPlugin.getPlugin().disconnect("Wrong Model");
            log.error("Wrong model selected. Switching to non APS DanaR");
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setPluginEnabled(PluginType.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setFragmentVisible(PluginType.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PUMP, true);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentVisible(PluginType.PUMP, true);

            //If profile coming from pump, switch it as well
            if (MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginType.PROFILE)) {
                (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setPluginEnabled(PluginType.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setPluginEnabled(PluginType.PROFILE, true);
            }

            ConfigBuilderPlugin.getPlugin().storeSettings("ChangingDanaRv2Driver");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("PumpDriverChange", null); // force new connection
            return;
        }
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Model: " + String.format("%02X ", pump.model));
            log.debug("Protocol: " + String.format("%02X ", pump.protocol));
            log.debug("Product Code: " + String.format("%02X ", pump.productCode));
        }
    }

}
