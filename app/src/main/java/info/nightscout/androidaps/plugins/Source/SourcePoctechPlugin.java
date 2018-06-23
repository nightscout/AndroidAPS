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
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.utils.JsonHelper;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SourcePoctechPlugin extends PluginBase implements BgSourceInterface {
    private static final Logger log = LoggerFactory.getLogger(SourcePoctechPlugin.class);

    private static SourcePoctechPlugin plugin = null;

    public static SourcePoctechPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourcePoctechPlugin();
        return plugin;
    }

    private SourcePoctechPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.poctech)
                .showInList(!Config.NSCLIENT)
                .preferencesId(R.xml.pref_poctech)
                .description(R.string.description_source_poctech)
        );
    }

    @Override
    public List<BgReading> processNewData(Bundle bundle) {
        List<BgReading> bgReadings = new ArrayList<>();

        String data = bundle.getString("data");
        log.debug("Received Poctech Data", data);

        try {
            JSONArray jsonArray = new JSONArray(data);
            log.debug("Received Poctech Data size:" + jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                BgReading bgReading = new BgReading();
                bgReading.value = json.getDouble("current");
                bgReading.direction = json.getString("direction");
                bgReading.date = json.getLong("date");
                bgReading.raw = json.getDouble("raw");
                bgReading.isFiltered = false;
                bgReading.sourcePlugin = getName();
                if (JsonHelper.safeGetString(json, "utils", Constants.MGDL).equals("mmol/L"))
                    bgReading.value = bgReading.value * Constants.MMOLL_TO_MGDL;
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
