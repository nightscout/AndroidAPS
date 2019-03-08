package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.BundleLogger;
import info.nightscout.androidaps.logging.L;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceGlimpPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

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
    public boolean advancedFilteringSupported() {
        return false;
    }

    @Override
    public void handleNewData(Intent intent) {

        if (!isEnabled(PluginType.BGSOURCE)) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (L.isEnabled(L.BGSOURCE))
            log.debug("Received Glimp Data: " + BundleLogger.log(bundle));

        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble("mySGV");
        bgReading.direction = bundle.getString("myTrend");
        bgReading.date = bundle.getLong("myTimestamp");
        bgReading.raw = 0;

        MainApp.getDbHelper().createIfNotExists(bgReading, "GLIMP");
    }
}
