package info.nightscout.androidaps.plugins.Source;

import android.os.Bundle;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

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
public class SourceGlimpPlugin extends PluginBase implements BgSourceInterface {

    private static SourceGlimpPlugin plugin = null;

    public static SourceGlimpPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceGlimpPlugin();
        return plugin;
    }

    private SourceGlimpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.Glimp)
                .description(R.string.description_source_glimp)
        );
    }

    @Override
    public List<BgReading> processNewData(Bundle bundle) {
        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble("mySGV");
        bgReading.direction = bundle.getString("myTrend");
        bgReading.date = bundle.getLong("myTimestamp");
        bgReading.raw = 0;
        bgReading.isFiltered = false;
        bgReading.sourcePlugin = getName();

        boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, getName());
        return isNew ? Lists.newArrayList(bgReading) : Collections.emptyList();
    }
}
