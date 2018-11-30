package info.nightscout.androidaps.plugins.Source;

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
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

/**
 * Created by mike on 30.11.2018.
 */

public class SourceDexcomG6Plugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceDexcomG6Plugin plugin = null;

    public static SourceDexcomG6Plugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDexcomG6Plugin();
        return plugin;
    }

    private SourceDexcomG6Plugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.DexcomG6)
                .shortName(R.string.dexcomG6_shortname)
                .preferencesId(R.xml.pref_dexcomg5)
                .description(R.string.description_source_dexcom_g6)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return true;
    }

    @Override
    public void handleNewData(Intent intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (L.isEnabled(L.BGSOURCE)) {
            if (bundle.containsKey("transmitterSystemTime"))
                log.debug("transmitterSystemTime: " + DateUtil.dateAndTimeFullString(bundle.getLong("transmitterSystemTime")));
            log.debug("transmitterId: " + bundle.getString("transmitterId"));
            if (bundle.containsKey("transmitterActivatedOn"))
                log.debug("transmitterActivatedOn: " + DateUtil.dateAndTimeFullString(bundle.getLong("transmitterActivatedOn")));
            log.debug("transmitterVersion: " + bundle.getString("transmitterVersion"));
            log.debug("transmitterSoftwareNumber: " + bundle.getString("transmitterSoftwareNumber"));
            log.debug("transmitterStorageTimeDays: " + bundle.getInt("transmitterStorageTimeDays"));
            log.debug("transmitterApiVersion: " + bundle.getInt("transmitterApiVersion"));
            log.debug("transmitterMaxRuntimeDays: " + bundle.getInt("transmitterMaxRuntimeDays"));
            log.debug("transmitterMaxStorageTimeDays: " + bundle.getInt("transmitterMaxStorageTimeDays"));
            log.debug("transmitterCGMProcessorFirmwareVersion: " + bundle.getString("transmitterCGMProcessorFirmwareVersion"));
            log.debug("transmitterBleRadioFirmwareVersion: " + bundle.getString("transmitterBleRadioFirmwareVersion"));
            log.debug("transmitterHardwareVersion: " + bundle.getInt("transmitterHardwareVersion"));
            log.debug("transmitterBleSoftDeviceVersion: " + bundle.getString("transmitterBleSoftDeviceVersion"));
            log.debug("transmitterNordicAsicHwID: " + bundle.getInt("transmitterNordicAsicHwID"));
            log.debug("transmitterSessionTimeDays: " + bundle.getInt("transmitterSessionTimeDays"));
            log.debug("transmitterFeatureFlags: " + bundle.getInt("transmitterFeatureFlags"));

            if (bundle.containsKey("sensorInsertionTime"))
                log.debug("sensorInsertionTime: " + DateUtil.dateAndTimeFullString(bundle.getLong("sensorInsertionTime")));

            if (bundle.containsKey("sensorCode"))
                log.debug("sensorCode: " + bundle.getString("sensorCode"));

            if (bundle.containsKey("evgTimestamps")) {
                long[] timestamps = bundle.getLongArray("evgTimestamps");
                long[] transmitterTimes = bundle.getLongArray("transmitterTimes");
                int[] evgs = bundle.getIntArray("evgs");
                int[] predictiveEVGs = bundle.getIntArray("predictiveEVGs");
                String[] trendArrows = bundle.getStringArray("trendArrows");

                for (int i = 0; i < transmitterTimes.length; i++) {
                    BgReading bgReading = new BgReading();
                    bgReading.value = evgs[i];
                    bgReading.direction = trendArrows[i];
                    bgReading.date = timestamps[i];
                    bgReading.raw = 0;
                    boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "DexcomG6");
                    if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                        NSUpload.uploadBg(bgReading, "AndroidAPS-DexcomG6");
                    }
                    if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                        NSUpload.sendToXdrip(bgReading);
                    }
                }
            }

            if (bundle.containsKey("meterValues")) {
                int[] meterValues = bundle.getIntArray("meterValues");
                String[] meterEntryTypes = bundle.getStringArray("meterEntryTypes");
                long[] meterTimestamps = bundle.getLongArray("meterTimestamps");
                long[] meterTransmitterTimestamps = bundle.getLongArray("meterTransmitterTimestamps");
                long[] meterRecordedTimestamps = bundle.getLongArray("meterRecordedTimestamps");
                int[] meterRecordIDs = bundle.getIntArray("meterRecordIDs");

                for (int i = 0; i < meterValues.length; i++) {
                    if (L.isEnabled(L.BGSOURCE)) {
                        log.debug("meterValues: " + meterValues[i] + " meterEntryTypes: " + meterEntryTypes[i] + " meterTimestamps: " + meterTimestamps[i] + " meterTransmitterTimestamps: " +
                                meterTransmitterTimestamps[i] + " meterRecordedTimestamps: " + meterRecordedTimestamps[i] + " meterRecordIDs: " + meterRecordIDs[i]);
                    }
                }
            }

        }
    }
}
