package info.nightscout.androidaps.plugins.DanaR.History;

import com.j256.ormlite.dao.Dao;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Calendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.DanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRSyncStatus;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 20.07.2016.
 */

public class NSHistorySync {
    private static Logger log = LoggerFactory.getLogger(NSHistorySync.class);
    private Dao<DanaRHistoryRecord, String> daoHistoryRecords;

    public final int SYNC_BOLUS = 0b00000001;
    public final int SYNC_ERROR = 0b00000010;
    public final int SYNC_REFILL = 0b00000100;
    public final int SYNC_GLUCOSE = 0b00001000;
    public final int SYNC_CARBO = 0b00010000;
    public final int SYNC_ALARM = 0b00100000;
    public final int SYNC_BASALOURS = 0b01000000;
    public final int SYNC_ALL = 0b11111111;

    public NSHistorySync(Dao<DanaRHistoryRecord, String> daoHistoryRecords) {
        this.daoHistoryRecords = daoHistoryRecords;
    }


    public void sync(int what) {
        try {
            ConfigBuilderFragment configBuilderFragment = MainApp.getConfigBuilder();
            NSProfile profile = configBuilderFragment.getActiveProfile().getProfile();
            if (profile == null) {
                ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.noprofile));
                return;
            }
            Calendar cal = Calendar.getInstance();
            long records = daoHistoryRecords.countOf();
            long processing = 0;
            long uploaded = 0;
            log.debug("Database contains " + records + " records");
            EventDanaRSyncStatus ev = new EventDanaRSyncStatus();
            for (DanaRHistoryRecord record : daoHistoryRecords) {
                processing++;
                if (record.get_id() != null) continue;
                //log.debug(record.getBytes());
                JSONObject nsrec = new JSONObject();
                ev.message = "Uploading " + processing + "/" + records + " "; // TODO: translations
                switch (record.getRecordCode()) {
                    case RecordTypes.RECORD_TYPE_BOLUS:
                        if ((what & SYNC_BOLUS) == 0) break;
                        switch (record.getBolusType()) {
                            case "S":
                                log.debug("Syncing standard bolus record " + record.getRecordValue() + "U " + DateUtil.toISOString(record.getRecordDate()));
                                nsrec.put("DANARMESSAGE", record.getBytes());
                                nsrec.put("eventType", "Meal Bolus");
                                nsrec.put("insulin", record.getRecordValue());
                                nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                                nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                                ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                                uploaded++;
                                ev.message += "S bolus";
                                break;
                            case "E":
                                if (record.getRecordDuration() > 0) {
                                    log.debug("Syncing extended bolus record " + record.getRecordValue() + "U " + DateUtil.toISOString(record.getRecordDate()));
                                    nsrec.put("DANARMESSAGE", record.getBytes());
                                    nsrec.put("eventType", "Combo Bolus");
                                    nsrec.put("insulin", 0);
                                    nsrec.put("duration", record.getRecordDuration());
                                    nsrec.put("relative", record.getRecordValue() / record.getRecordDuration() * 60);
                                    nsrec.put("splitNow", 0);
                                    nsrec.put("splitExt", 100);
                                    cal.setTimeInMillis(record.getRecordDate());
                                    cal.add(Calendar.MINUTE, -1 * record.getRecordDuration());
                                    nsrec.put("created_at", DateUtil.toISOString(cal.getTime()));
                                    nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                                    ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                                    uploaded++;
                                    ev.message += "E bolus";
                                } else {
                                    log.debug("NOT Syncing extended bolus record " + record.getRecordValue() + "U " + DateUtil.toISOString(record.getRecordDate()) + " zero duration");
                                }
                                break;
                            case "DS":
                                log.debug("Syncing dual(S) bolus record " + record.getRecordValue() + "U " + DateUtil.toISOString(record.getRecordDate()));
                                nsrec.put("DANARMESSAGE", record.getBytes());
                                nsrec.put("eventType", "Combo Bolus");
                                nsrec.put("insulin", record.getRecordValue());
                                nsrec.put("splitNow", 100);
                                nsrec.put("splitExt", 0);
                                nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                                nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                                ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                                uploaded++;
                                ev.message += "DS bolus";
                                break;
                            case "DE":
                                log.debug("Syncing dual(E) bolus record " + record.getRecordValue() + "U " + DateUtil.toISOString(record.getRecordDate()));
                                nsrec.put("DANARMESSAGE", record.getBytes());
                                nsrec.put("eventType", "Combo Bolus");
                                nsrec.put("duration", record.getRecordDuration());
                                nsrec.put("relative", record.getRecordValue() / record.getRecordDuration() * 60);
                                nsrec.put("splitNow", 0);
                                nsrec.put("splitExt", 100);
                                cal.setTimeInMillis(record.getRecordDate());
                                cal.add(Calendar.MINUTE, -1 * record.getRecordDuration());
                                nsrec.put("created_at", DateUtil.toISOString(cal.getTime()));
                                nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                                ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                                uploaded++;
                                ev.message += "DE bolus";
                                break;
                            default:
                                log.debug("Unknown bolus record");
                                break;
                        }
                        break;
                    case RecordTypes.RECORD_TYPE_ERROR:
                        if ((what & SYNC_ERROR) == 0) break;
                        log.debug("Syncing error record " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "Note");
                        nsrec.put("notes", "Error");
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "error";
                        break;
                    case RecordTypes.RECORD_TYPE_REFILL:
                        if ((what & SYNC_REFILL) == 0) break;
                        log.debug("Syncing refill record " + record.getRecordValue() + " " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "Insulin Change");
                        nsrec.put("notes", "Refill " + record.getRecordValue() + "U");
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "refill";
                        break;
                    case RecordTypes.RECORD_TYPE_BASALHOUR:
                        if ((what & SYNC_BASALOURS) == 0) break;
                        log.debug("Syncing basal hour record " + record.getRecordValue() + " " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "Temp Basal");
                        nsrec.put("absolute", record.getRecordValue());
                        nsrec.put("duration", 60);
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "basal hour";
                        break;
                    case RecordTypes.RECORD_TYPE_TB:
                        //log.debug("Ignoring TB record " + record.getBytes() + " " + DateUtil.toISOString(record.getRecordDate()));
                        break;
                    case RecordTypes.RECORD_TYPE_GLUCOSE:
                        if ((what & SYNC_GLUCOSE) == 0) break;
                        log.debug("Syncing glucose record " + record.getRecordValue() + " " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "BG Check");
                        nsrec.put("glucose", NSProfile.fromMgdlToUnits(record.getRecordValue(), profile.getUnits()));
                        nsrec.put("glucoseType", "Finger");
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "glucose";
                        break;
                    case RecordTypes.RECORD_TYPE_CARBO:
                        if ((what & SYNC_CARBO) == 0) break;
                        log.debug("Syncing carbo record " + record.getRecordValue() + "g " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "Meal Bolus");
                        nsrec.put("carbs", record.getRecordValue());
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "carbo";
                        break;
                    case RecordTypes.RECORD_TYPE_ALARM:
                        if ((what & SYNC_ALARM) == 0) break;
                        log.debug("Syncing alarm record " + record.getRecordAlarm() + " " + DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("DANARMESSAGE", record.getBytes());
                        nsrec.put("eventType", "Note");
                        nsrec.put("notes", "Alarm: " + record.getRecordAlarm());
                        nsrec.put("created_at", DateUtil.toISOString(record.getRecordDate()));
                        nsrec.put("enteredBy", MainApp.sResources.getString(R.string.app_name));
                        ConfigBuilderFragment.uploadCareportalEntryToNS(nsrec);
                        uploaded++;
                        ev.message += "alarm";
                        break;
                    case RecordTypes.RECORD_TYPE_SUSPEND: // TODO: this too
                    case RecordTypes.RECORD_TYPE_DAILY:
                    case RecordTypes.RECORD_TYPE_PRIME:
                        // Ignore
                        break;
                    default:
                        log.error("Unknown record type");
                        break;
                }
                MainApp.bus().post(ev);
            }
            ev.message = "Total " + uploaded + " records uploaded";
            MainApp.bus().post(ev);

        } catch (JSONException | SQLException e) {
            e.printStackTrace();
        }
    }
}
