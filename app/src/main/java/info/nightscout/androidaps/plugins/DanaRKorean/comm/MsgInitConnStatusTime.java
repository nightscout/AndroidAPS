package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.utils.ToastUtils;

public class MsgInitConnStatusTime extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusTime.class);

    public MsgInitConnStatusTime() {
        SetCommand(0x0301);
    }

    @Override
    public void handleMessage(byte[] bytes) {

        if (bytes.length - 10 < 10) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.wrongpumpdriverselected), R.raw.error);
            ((DanaRKoreanPlugin)MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).doDisconnect("Wrong Model");
            log.debug("Wrong model selected. Switching to export DanaR");
            ((DanaRKoreanPlugin)MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).setFragmentEnabled(PluginBase.PUMP, false);
            ((DanaRKoreanPlugin)MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).setFragmentVisible(PluginBase.PUMP, false);
            ((DanaRPlugin)MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentEnabled(PluginBase.PUMP, true);
            ((DanaRPlugin)MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentVisible(PluginBase.PUMP, true);

            //If profile coming from pump, switch it as well
            if(MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).isEnabled(PluginBase.PROFILE)){
                (MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).setFragmentEnabled(PluginBase.PROFILE, false);
                (MainApp.getSpecificPlugin(DanaRPlugin.class)).setFragmentEnabled(PluginBase.PROFILE, true);
            }

            MainApp.getConfigBuilder().storeSettings();
            MainApp.bus().post(new EventRefreshGui(false));
            return;
        }

        Date time = dateTimeSecFromBuff(bytes, 0);
        int versionCode1 = intFromBuff(bytes, 6, 1);
        int versionCode2 = intFromBuff(bytes, 7, 1);
        int versionCode3 = intFromBuff(bytes, 8, 1);
        int versionCode4 = intFromBuff(bytes, 9, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Pump time: " + time);
            log.debug("Version code1: " + versionCode1);
            log.debug("Version code2: " + versionCode2);
            log.debug("Version code3: " + versionCode3);
            log.debug("Version code4: " + versionCode4);
        }
    }
}
