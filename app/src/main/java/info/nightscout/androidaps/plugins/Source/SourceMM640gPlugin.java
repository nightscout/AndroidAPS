package info.nightscout.androidaps.plugins.Source;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceMM640gPlugin extends PluginBase implements BgSourceInterface {
    private static final Logger log = LoggerFactory.getLogger(SourceMM640gPlugin.class);

    private static SourceMM640gPlugin plugin = null;

    public static SourceMM640gPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceMM640gPlugin();
        return plugin;
    }

    private SourceMM640gPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.MM640g)
                .description(R.string.description_source_mm640g)
        );
    }

    @Override
    public List<BgReading> processNewData(Bundle bundle) {
        List<BgReading> bgReadings = new ArrayList<>();

        if (Objects.equals(bundle.getString("collection"), "entries")) {
            final String data = bundle.getString("data");

            if ((data != null) && (data.length() > 0)) {
                try {
                    final JSONArray json_array = new JSONArray(data);
                    for (int i = 0; i < json_array.length(); i++) {
                        final JSONObject json_object = json_array.getJSONObject(i);
                        final String type = json_object.getString("type");
                        switch (type) {
                            case "sgv":
                                BgReading bgReading = new BgReading();

                                bgReading.value = json_object.getDouble("sgv");
                                bgReading.direction = json_object.getString("direction");
                                bgReading.date = json_object.getLong("date");
                                bgReading.raw = json_object.getDouble("sgv");
                                bgReading.isFiltered = true;
                                bgReading.sourcePlugin = getName();

                                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, getName());
                                if (isNew) {
                                    bgReadings.add(bgReading);
                                }
                                break;
                            default:
                                log.debug("Unknown entries type: " + type);
                        }
                    }
                } catch (JSONException e) {
                    log.error("Got JSON exception: " + e);
                }
            }
        }

        return bgReadings;
    }
}
