package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
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
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 28.11.2017.
 */

public class SourceDexcomG5Plugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceDexcomG5Plugin plugin = null;

    public static SourceDexcomG5Plugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDexcomG5Plugin();
        return plugin;
    }

    private SourceDexcomG5Plugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.DexcomG5)
                .shortName(R.string.dexcomG5_shortname)
                .preferencesId(R.xml.pref_bgsource)
                .description(R.string.description_source_dexcom_g5)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return true;
    }

    @Override
    public void handleNewData(Intent intent) {
        // onHandleIntent Bundle{ data => [{"m_time":1511939180,"m_trend":"NotComputable","m_value":335}]; android.support.content.wakelockid => 95; }Bundle

        if (!isEnabled(PluginType.BGSOURCE)) return;

        if (intent.getAction().equals(Intents.DEXCOMG5_BG))
            handleNewDataOld(intent);

        if (intent.getAction().equals(Intents.DEXCOMG5_BG_NEW))
            handleNewDataNew(intent);
    }

    public void handleNewDataOld(Intent intent) {
        // onHandleIntent Bundle{ data => [{"m_time":1511939180,"m_trend":"NotComputable","m_value":335}]; android.support.content.wakelockid => 95; }Bundle

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        String data = bundle.getString("data");
        if (L.isEnabled(L.BGSOURCE))
            log.debug("Received Dexcom Data", data);

        if (data == null) return;

        try {
            JSONArray jsonArray = new JSONArray(data);
            if (L.isEnabled(L.BGSOURCE))
                log.debug("Received Dexcom Data size:" + jsonArray.length());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                bgReading.value = json.getInt("m_value");
                bgReading.direction = json.getString("m_trend");
                bgReading.date = json.getLong("m_time") * 1000L;
                bgReading.raw = 0;
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "DexcomG5");
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                    NSUpload.uploadBg(bgReading, "AndroidAPS-DexcomG5");
                }
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                    NSUpload.sendToXdrip(bgReading);
                }
            }

        } catch (JSONException e) {
            log.error("Exception: ", e);
        }
    }

    public void handleNewDataNew(Intent intent) {

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (L.isEnabled(L.BGSOURCE)) {
            if (bundle.containsKey("transmitterSystemTime"))
                log.debug("transmitterSystemTime: " + DateUtil.dateAndTimeFullString(bundle.getLong("transmitterSystemTime")));
            if (bundle.containsKey("transmitterRemainingTime"))
                log.debug("transmitterRemainingTime: " + DateUtil.dateAndTimeFullString(bundle.getLong("transmitterRemainingTime")));
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
        }

        if (bundle.containsKey("sensorInsertionTime")) {
            long sensorInsertionTime = bundle.getLong("sensorInsertionTime");
            if (L.isEnabled(L.BGSOURCE))
                log.debug("sensorInsertionTime: " + DateUtil.dateAndTimeFullString(sensorInsertionTime));
            if (SP.getBoolean(R.string.key_dexcom_lognssensorchange, false)) {
                try {
                    if (MainApp.getDbHelper().getCareportalEventFromTimestamp(sensorInsertionTime) == null) {
                        JSONObject data = new JSONObject();
                        data.put("enteredBy", "AndroidAPS-DexcomG5");
                        data.put("created_at", DateUtil.toISOString(sensorInsertionTime));
                        data.put("eventType", CareportalEvent.SENSORCHANGE);
                        NSUpload.uploadCareportalEntryToNS(data);
                    }
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
        }

        if (bundle.containsKey("glucoseValues")) {
            int[] glucoseValues = bundle.getIntArray("glucoseValues");
            int[] glucoseRecordIDs = bundle.getIntArray("glucoseRecordIDs");
            long[] glucoseRecordedTimestamps = bundle.getLongArray("glucoseRecordedTimestamps");
            long[] glucoseSessionStartTimes = bundle.getLongArray("glucoseSessionStartTimes");
            long[] glucoseSystemTimestamps = bundle.getLongArray("glucoseSystemTimestamps");
            String[] glucoseTransmitterIDS = bundle.getStringArray("glucoseTransmitterIDS");
            long[] glucoseTransmitterTimestamps = bundle.getLongArray("glucoseTransmitterTimestamps");
            String[] glucoseTrendsArrows = bundle.getStringArray("glucoseTrendsArrows");
            boolean[] glucoseWasBackfilled = bundle.getBooleanArray("glucoseWasBackfilled");

            if (L.isEnabled(L.BGSOURCE)) {
                log.debug("glucoseValues", Arrays.toString(glucoseValues));
                log.debug("glucoseRecordIDs", Arrays.toString(glucoseRecordIDs));
                log.debug("glucoseRecordedTimestamps", Arrays.toString(glucoseRecordedTimestamps));
                log.debug("glucoseSessionStartTimes", Arrays.toString(glucoseSessionStartTimes));
                log.debug("glucoseSystemTimestamps", Arrays.toString(glucoseSystemTimestamps));
                log.debug("glucoseTransmitterIDS", Arrays.toString(glucoseTransmitterIDS));
                log.debug("glucoseTransmitterTimestamps", Arrays.toString(glucoseTransmitterTimestamps));
                log.debug("glucoseTrendsArrows", Arrays.toString(glucoseTrendsArrows));
                log.debug("glucoseWasBackfilled", Arrays.toString(glucoseWasBackfilled));
            }

            for (int i = 0; i < glucoseValues.length; i++) {
                BgReading bgReading = new BgReading();
                bgReading.value = glucoseValues[i];
                bgReading.direction = glucoseTrendsArrows[i];
                bgReading.date = glucoseTransmitterTimestamps[i];
                bgReading.raw = 0;
                boolean isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "DexcomG5");
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                    NSUpload.uploadBg(bgReading, "AndroidAPS-DexcomG5");
                }
                if (isNew && SP.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                    NSUpload.sendToXdrip(bgReading);
                }
            }
        }

        if (bundle.containsKey("meterValues")) {
            String[] meterEntryTypes = bundle.getStringArray("meterEntryTypes");
            long[] meterTimestamps = bundle.getLongArray("meterTimestamps");
            int[] meterValues = bundle.getIntArray("meterValues");
            long[] meterRecordedTimestamps = bundle.getLongArray("meterRecordedTimestamps");
            int[] meterTransmitterIDs = bundle.getIntArray("meterTransmitterIDs");
            long[] meterTransmitterTimestamps = bundle.getLongArray("meterTransmitterTimestamps");

            if (L.isEnabled(L.BGSOURCE)) {
                log.debug("meterValues", Arrays.toString(meterValues));
                log.debug("meterEntryTypes", Arrays.toString(meterEntryTypes));
                log.debug("meterTimestamps", Arrays.toString(meterTimestamps));
                log.debug("meterTransmitterTimestamps", Arrays.toString(meterTransmitterTimestamps));
                log.debug("meterRecordedTimestamps", Arrays.toString(meterRecordedTimestamps));
                log.debug("meterTransmitterIDs", Arrays.toString(meterTransmitterIDs));
            }

            for (int i = 0; i < meterValues.length; i++) {
                try {
                    if (MainApp.getDbHelper().getCareportalEventFromTimestamp(meterTimestamps[i]) == null) {
                        JSONObject data = new JSONObject();
                        data.put("enteredBy", "AndroidAPS-DexcomG5");
                        data.put("created_at", DateUtil.toISOString(meterTimestamps[i]));
                        data.put("eventType", CareportalEvent.BGCHECK);
                        data.put("glucoseType", "Finger");
                        data.put("glucose", meterValues[i]);
                        data.put("units", Constants.MGDL);
                        NSUpload.uploadCareportalEntryToNS(data);
                    }
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
        }
    }
}
