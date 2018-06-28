package info.nightscout.androidaps.plugins.Source;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 28.11.2017.
 */

public class SourceDexcomG5Plugin extends PluginBase implements BgSourceInterface {
    private static final Logger log = LoggerFactory.getLogger(SourceDexcomG5Plugin.class);

    private static SourceDexcomG5Plugin plugin = null;

    public static SourceDexcomG5Plugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDexcomG5Plugin();
        return plugin;
    }

    private SourceDexcomG5Plugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.DexcomG5)
                .shortName(R.string.dexcomG5_shortname)
                .showInList(!Config.NSCLIENT)
                .preferencesId(R.xml.pref_dexcomg5)
                .description(R.string.description_source_dexcom_g5)
        );
    }

    @Override
    public List<BgReading> processNewData(Bundle bundle) {
        List<BgReading> bgReadings = new ArrayList<>();

        String data = bundle.getString("data");
        // onHandleIntent Bundle{ data => [{"m_time":1511939180,"m_trend":"NotComputable","m_value":335}]; android.support.content.wakelockid => 95; }Bundle
        log.debug("Received Dexcom Data", data);

        try {
            JSONArray jsonArray = new JSONArray(data);
            log.debug("Received Dexcom Data size:" + jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                BgReading bgReading = new BgReading();
                bgReading.value = json.getInt("m_value");
                bgReading.direction = json.getString("m_trend");
                bgReading.date = json.getLong("m_time") * 1000L;
                bgReading.raw = 0;
                bgReading.isFiltered = true;
                bgReading.sourcePlugin = getName();
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, getName());
                if (isNew) {
                    bgReadings.add(bgReading);
                    if (SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                        NSUpload.uploadBg(bgReading);
                    }
                    if (SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                        NSUpload.sendToXdrip(bgReading);
                    }
                }
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return bgReadings;
    }
}
