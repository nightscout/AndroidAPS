package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 28.11.2017.
 */

public class SourceEversensePlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceEversensePlugin plugin = null;

    public static SourceEversensePlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceEversensePlugin();
        return plugin;
    }

    private SourceEversensePlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.eversense)
                .shortName(R.string.eversense_shortname)
                .preferencesId(R.xml.pref_poctech)
                .description(R.string.description_source_eversense)
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

        if (L.isEnabled(L.BGSOURCE)) {
            if (bundle.containsKey("currentCalibrationPhase"))
                log.debug("currentCalibrationPhase: " + bundle.getString("currentCalibrationPhase"));
            if (bundle.containsKey("placementModeInProgress"))
                log.debug("placementModeInProgress: " + bundle.getBoolean("placementModeInProgress"));
            if (bundle.containsKey("glucoseLevel"))
                log.debug("glucoseLevel: " + bundle.getInt("glucoseLevel"));
            if (bundle.containsKey("glucoseTrendDirection"))
                log.debug("glucoseTrendDirection: " + bundle.getString("glucoseTrendDirection"));
            if (bundle.containsKey("glucoseTimestamp"))
                log.debug("glucoseTimestamp: " + DateUtil.dateAndTimeFullString(bundle.getLong("glucoseTimestamp")));
            if (bundle.containsKey("batteryLevel"))
                log.debug("batteryLevel: " + bundle.getString("batteryLevel"));
            if (bundle.containsKey("signalStrength"))
                log.debug("signalStrength: " + bundle.getString("signalStrength"));
            if (bundle.containsKey("transmitterVersionNumber"))
                log.debug("transmitterVersionNumber: " + bundle.getString("transmitterVersionNumber"));
            if (bundle.containsKey("isXLVersion"))
                log.debug("isXLVersion: " + bundle.getBoolean("isXLVersion"));
            if (bundle.containsKey("transmitterModelNumber"))
                log.debug("transmitterModelNumber: " + bundle.getString("transmitterModelNumber"));
            if (bundle.containsKey("transmitterSerialNumber"))
                log.debug("transmitterSerialNumber: " + bundle.getString("transmitterSerialNumber"));
            if (bundle.containsKey("transmitterAddress"))
                log.debug("transmitterAddress: " + bundle.getString("transmitterAddress"));
            if (bundle.containsKey("sensorInsertionTimestamp"))
                log.debug("sensorInsertionTimestamp: " + DateUtil.dateAndTimeFullString(bundle.getLong("sensorInsertionTimestamp")));
            if (bundle.containsKey("transmitterVersionNumber"))
                log.debug("transmitterVersionNumber: " + bundle.getString("transmitterVersionNumber"));
            if (bundle.containsKey("transmitterConnectionState"))
                log.debug("transmitterConnectionState: " + bundle.getString("transmitterConnectionState"));
        }

        if (bundle.containsKey("glucoseLevels")) {
            int[] glucoseLevels = bundle.getIntArray("glucoseLevels");
            int[] glucoseRecordNumbers = bundle.getIntArray("glucoseRecordNumbers");
            long[] glucoseTimestamps = bundle.getLongArray("glucoseTimestamps");

            if (L.isEnabled(L.BGSOURCE)) {
                log.debug("glucoseLevels", Arrays.toString(glucoseLevels));
                log.debug("glucoseRecordNumbers", Arrays.toString(glucoseRecordNumbers));
                log.debug("glucoseTimestamps", Arrays.toString(glucoseTimestamps));
            }

            for (int i = 0; i < glucoseLevels.length; i++) {
                BgReading bgReading = new BgReading();
                bgReading.value = glucoseLevels[i];
                bgReading.date = glucoseTimestamps[i];
                bgReading.raw = 0;
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Eversense");
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                    NSUpload.uploadBg(bgReading, "AndroidAPS-Eversense");
                }
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                    NSUpload.sendToXdrip(bgReading);
                }
            }
        }

        if (bundle.containsKey("calibrationGlucoseLevels")) {
            int[] calibrationGlucoseLevels = bundle.getIntArray("calibrationGlucoseLevels");
            long[] calibrationTimestamps = bundle.getLongArray("calibrationTimestamps");
            long[] calibrationRecordNumbers = bundle.getLongArray("calibrationRecordNumbers");

            if (L.isEnabled(L.BGSOURCE)) {
                log.debug("calibrationGlucoseLevels", Arrays.toString(calibrationGlucoseLevels));
                log.debug("calibrationTimestamps", Arrays.toString(calibrationTimestamps));
                log.debug("calibrationRecordNumbers", Arrays.toString(calibrationRecordNumbers));
            }

            for (int i = 0; i < calibrationGlucoseLevels.length; i++) {
                try {
                    if (MainApp.getDbHelper().getCareportalEventFromTimestamp(calibrationTimestamps[i]) == null) {
                        JSONObject data = new JSONObject();
                        data.put("enteredBy", "AndroidAPS-Eversense");
                        data.put("created_at", DateUtil.toISOString(calibrationTimestamps[i]));
                        data.put("eventType", CareportalEvent.BGCHECK);
                        data.put("glucoseType", "Finger");
                        data.put("glucose", calibrationGlucoseLevels[i]);
                        data.put("units", Constants.MGDL);
                        CareportalEvent careportalEvent = new CareportalEvent();
                        careportalEvent.date = calibrationTimestamps[i];
                        careportalEvent.source = Source.USER;
                        careportalEvent.eventType = CareportalEvent.BGCHECK;
                        careportalEvent.json = data.toString();
                        MainApp.getDbHelper().createOrUpdate(careportalEvent);
                        NSUpload.uploadCareportalEntryToNS(data);
                    }
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
        }
    }
}
