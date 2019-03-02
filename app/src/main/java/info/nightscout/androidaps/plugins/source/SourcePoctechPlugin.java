package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SourcePoctechPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

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
                .preferencesId(R.xml.pref_poctech)
                .description(R.string.description_source_poctech)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return false;
    }

    @Override
    public void handleNewData(Intent intent) {

        if (!isEnabled(PluginType.BGSOURCE)) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        String data = bundle.getString("data");
        if (L.isEnabled(L.BGSOURCE))
            log.debug("Received Poctech Data", data);

        try {
            JSONArray jsonArray = new JSONArray(data);
            if (L.isEnabled(L.BGSOURCE))
                log.debug("Received Poctech Data size:" + jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                bgReading.value = json.getDouble("current");
                bgReading.direction = json.getString("direction");
                bgReading.date = json.getLong("date");
                bgReading.raw = json.getDouble("raw");
                if (JsonHelper.safeGetString(json, "units", Constants.MGDL).equals("mmol/L"))
                    bgReading.value = bgReading.value * Constants.MMOLL_TO_MGDL;
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Poctech");
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                    NSUpload.uploadBg(bgReading, "AndroidAPS-Poctech");
                }
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                    NSUpload.sendToXdrip(bgReading);
                }
            }

        } catch (JSONException e) {
            log.error("Exception: ", e);
        }
    }

}
