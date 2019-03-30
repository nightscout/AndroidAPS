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
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceTomatoPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceTomatoPlugin plugin = null;

    public static SourceTomatoPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceTomatoPlugin();
        return plugin;
    }

    private SourceTomatoPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.tomato)
                .preferencesId(R.xml.pref_poctech)
                .shortName(R.string.tomato_short)
                .description(R.string.description_source_tomato)
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

        if (L.isEnabled(L.BGSOURCE))
            log.debug("Received Tomato Data");

        bgReading.value = bundle.getDouble("com.fanqies.tomatofn.Extras.BgEstimate");
        bgReading.date = bundle.getLong("com.fanqies.tomatofn.Extras.Time");
        boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Tomato");
        if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
            NSUpload.uploadBg(bgReading, "AndroidAPS-Tomato");
        }
        if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
            NSUpload.sendToXdrip(bgReading);
        }
    }

}
