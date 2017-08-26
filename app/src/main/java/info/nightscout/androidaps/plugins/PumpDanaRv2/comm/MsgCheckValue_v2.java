package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.utils.ToastUtils;

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
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.wrongpumpdriverselected), R.raw.error);
            DanaRv2Plugin.doDisconnect("Wrong Model");
            log.debug("Wrong model selected");
        }

        if (pump.protocol != 2) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.wrongpumpdriverselected), R.raw.error);
            DanaRKoreanPlugin.doDisconnect("Wrong Model");
            log.debug("Wrong model selected. Switching to non APS DanaR");
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setFragmentEnabled(PluginBase.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setFragmentVisible(PluginBase.PUMP, false);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentEnabled(PluginBase.PUMP, true);
            (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentVisible(PluginBase.PUMP, true);

            //If profile coming from pump, switch it as well
            if(MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginBase.PROFILE)){
                (MainApp.getSpecificPlugin(DanaRv2Plugin.class)).setFragmentEnabled(PluginBase.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentEnabled(PluginBase.PROFILE, true);
            }

            MainApp.getConfigBuilder().storeSettings();
            MainApp.bus().post(new EventRefreshOverview("MsgCheckValue_v2"));
            return;
        }
        if (Config.logDanaMessageDetail) {
            log.debug("Model: " + String.format("%02X ", pump.model));
            log.debug("Protocol: " + String.format("%02X ", pump.protocol));
            log.debug("Product Code: " + String.format("%02X ", pump.productCode));
        }
    }

}
