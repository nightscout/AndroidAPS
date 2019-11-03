package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceNSClientPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceNSClientPlugin plugin = null;

    public static SourceNSClientPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceNSClientPlugin();
        return plugin;
    }

    private long lastBGTimeStamp = 0;
    private boolean isAdvancedFilteringEnabled = false;

    private SourceNSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.nsclientbg)
                .description(R.string.description_source_ns_client)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return isAdvancedFilteringEnabled;
    }

    @Override
    public void handleNewData(Intent intent) {

        if (!isEnabled(PluginType.BGSOURCE) && !SP.getBoolean(R.string.key_ns_autobackfill, true))
            return;

        Bundle bundles = intent.getExtras();

        try {
            if (bundles.containsKey("sgv")) {
                String sgvstring = bundles.getString("sgv");
                if (L.isEnabled(L.BGSOURCE))
                    log.debug("Received NS Data: " + sgvstring);

                JSONObject sgvJson = new JSONObject(sgvstring);
                storeSgv(sgvJson);
            }

            if (bundles.containsKey("sgvs")) {
                String sgvstring = bundles.getString("sgvs");
                if (L.isEnabled(L.BGSOURCE))
                    log.debug("Received NS Data: " + sgvstring);
                JSONArray jsonArray = new JSONArray(sgvstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject sgvJson = jsonArray.getJSONObject(i);
                    storeSgv(sgvJson);
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

        // Objectives 0
        SP.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true);
    }

    private void storeSgv(JSONObject sgvJson) {
        NSSgv nsSgv = new NSSgv(sgvJson);
        BgReading bgReading = new BgReading(nsSgv);
        MainApp.getDbHelper().createIfNotExists(bgReading, "NS");
        SourceNSClientPlugin.getPlugin().detectSource(JsonHelper.safeGetString(sgvJson, "device", "none"), JsonHelper.safeGetLong(sgvJson, "mills"));
    }

    public void detectSource(String source, long timeStamp) {
        if (timeStamp > lastBGTimeStamp) {
            if (source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-DexcomG5") || source.contains("AndroidAPS-DexcomG6"))
                isAdvancedFilteringEnabled = true;
            else
                isAdvancedFilteringEnabled = false;
            lastBGTimeStamp = timeStamp;
        }
    }
}
