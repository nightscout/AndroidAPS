package info.nightscout.androidaps.plugins.Source;

import android.os.Bundle;

import org.json.JSONArray;
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
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.utils.JsonHelper;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceNSClientPlugin extends PluginBase implements BgSourceInterface {
    private static final Logger log = LoggerFactory.getLogger(SourceNSClientPlugin.class);

    private static SourceNSClientPlugin plugin = null;

    public static SourceNSClientPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceNSClientPlugin();
        return plugin;
    }

    private SourceNSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.nsclientbg)
                .showInList(!Config.NSCLIENT)
                .alwaysEnabled(Config.NSCLIENT)
                .description(R.string.description_source_ns_client)
        );
    }

    @Override
    public List<BgReading> processNewData(Bundle bundle) {
        List<BgReading> sgvs = new ArrayList<>();
        try {
            if (bundle.containsKey("sgv")) {
                String sgvstring = bundle.getString("sgv");
                JSONObject sgvJson = new JSONObject(sgvstring);
                BgReading bgReading = new BgReading(new NSSgv(sgvJson));
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, getName());
                if (isNew) {
                    sgvs.add(bgReading);
                }
            }

            if (bundle.containsKey("sgvs")) {
                String sgvstring = bundle.getString("sgvs");
                JSONArray jsonArray = new JSONArray(sgvstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject sgvJson = jsonArray.getJSONObject(i);
                    BgReading bgReading = new BgReading(new NSSgv(sgvJson));
                    String sourceDescription = JsonHelper.safeGetString(sgvJson, "device");
                    bgReading.isFiltered = sourceDescription != null
                            && (sourceDescription.contains("G5 Native") || sourceDescription.contains("AndroidAPS-DexcomG5"));
                    bgReading.sourcePlugin = getName();
                    boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, getName());
                    if (isNew) {
                        sgvs.add(bgReading);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return sgvs;
    }

}
