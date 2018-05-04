package info.nightscout.androidaps.plugins.PumpInsight.history;

import android.content.Intent;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import org.json.JSONException;
import org.json.JSONObject;
import sugar.free.sightparser.handling.HistoryBroadcast;

import java.util.Date;

import static info.nightscout.androidaps.plugins.PumpInsight.history.PumpIdCache.updatePumpSerialNumber;

/**
 * Created by jamorham on 27/01/2018.
 * <p>
 * Parse inbound logbook intents
 */

class HistoryIntentAdapter {

    private HistoryLogAdapter logAdapter = new HistoryLogAdapter();

    private static Date getDateExtra(Intent intent, String name) {
        return (Date) intent.getSerializableExtra(name);
    }

    private static void log(String msg) {
        android.util.Log.e("HistoryIntentAdapter", msg);
    }

    static long getRecordUniqueID(long pump_serial_number, long pump_record_id) {
        updatePumpSerialNumber(pump_serial_number);
        return (pump_serial_number * 10000000) + pump_record_id;
    }

    void processTBRIntent(Intent intent) {

        final int pump_tbr_duration = intent.getIntExtra(HistoryBroadcast.EXTRA_DURATION, -1);
        final int pump_tbr_percent = intent.getIntExtra(HistoryBroadcast.EXTRA_TBR_AMOUNT, -1);
        long pump_record_id = intent.getLongExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        if (pump_record_id == -1) {
            pump_record_id = intent.getIntExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        }
        final long pump_serial_number = Long.parseLong(intent.getStringExtra(HistoryBroadcast.EXTRA_PUMP_SERIAL_NUMBER));
        final Date event_time = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        final Date start_time = getDateExtra(intent, HistoryBroadcast.EXTRA_START_TIME);

        if ((pump_tbr_duration == -1) || (pump_tbr_percent == -1) || (pump_record_id == -1)) {
            log("Invalid TBR record!!!");
            return;
        }

        final long record_unique_id = getRecordUniqueID(pump_serial_number, pump_record_id);

        // other sanity checks
        if ((pump_tbr_percent == 90) && (pump_tbr_duration <= 1)) {
            log("Not creating TBR record for faux cancel");
        } else {
            log("Creating TBR record: " + pump_tbr_percent + "% " + pump_tbr_duration + "m" + " id:" + record_unique_id);
            logAdapter.createTBRrecord(start_time, pump_tbr_percent, pump_tbr_duration, record_unique_id);
        }
    }

    void processDeliveredBolusIntent(Intent intent) {

        final String bolus_type = intent.getStringExtra(HistoryBroadcast.EXTRA_BOLUS_TYPE);
        final int bolus_id = intent.getIntExtra(HistoryBroadcast.EXTRA_BOLUS_ID, -1);
        long pump_record_id = intent.getLongExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        if (pump_record_id == -1) {
            pump_record_id = intent.getIntExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        }
        final long pump_serial_number = Long.parseLong(intent.getStringExtra(HistoryBroadcast.EXTRA_PUMP_SERIAL_NUMBER));
        final Date event_time = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        final Date start_time = getDateExtra(intent, HistoryBroadcast.EXTRA_START_TIME);
        final double immediate_amount = intent.getDoubleExtra(HistoryBroadcast.EXTRA_IMMEDIATE_AMOUNT, -1);
        final double extended_insulin = intent.getDoubleExtra(HistoryBroadcast.EXTRA_EXTENDED_AMOUNT, -1);
        final int extended_minutes = intent.getIntExtra(HistoryBroadcast.EXTRA_DURATION, -1);

        final long record_unique_id = getRecordUniqueID(pump_serial_number, bolus_id > -1 ? bolus_id : pump_record_id);

        switch (bolus_type) {
            case "STANDARD":
                if (immediate_amount == -1) {
                    log("ERROR Standard bolus fails sanity check");
                    return;
                }
                LiveHistory.setStatus(bolus_type + " BOLUS\n" + immediate_amount + "U ", event_time.getTime());
                logAdapter.createStandardBolusRecord(start_time, immediate_amount, record_unique_id);
                break;

            case "EXTENDED":
                if ((extended_insulin == -1) || (extended_minutes == -1)) {
                    log("ERROR: Extended bolus fails sanity check");
                    return;
                }
                LiveHistory.setStatus(bolus_type + " BOLUS\n" + extended_insulin + "U over " + extended_minutes + " min, ", event_time.getTime());
                logAdapter.createExtendedBolusRecord(start_time, extended_insulin, extended_minutes, record_unique_id);
                break;

            case "MULTIWAVE":
                if ((immediate_amount == -1) || (extended_insulin == -1) || (extended_minutes == -1)) {
                    log("ERROR: Multiwave bolus fails sanity check");
                    return;
                }
                LiveHistory.setStatus(bolus_type + " BOLUS\n" + immediate_amount + "U + " + extended_insulin + "U over " + extended_minutes + " min, ", event_time.getTime());
                logAdapter.createStandardBolusRecord(start_time, immediate_amount, pump_serial_number + pump_record_id);
                logAdapter.createExtendedBolusRecord(start_time, extended_insulin, extended_minutes, record_unique_id);
                break;
            default:
                log("ERROR, UNKNWON BOLUS TYPE: " + bolus_type);
        }
    }

    void processDailyTotalIntent(Intent intent) {
        Date date = getDateExtra(intent, HistoryBroadcast.EXTRA_TOTAL_DATE);
        double basal = intent.getDoubleExtra(HistoryBroadcast.EXTRA_BASAL_TOTAL, 0D);
        double bolus = intent.getDoubleExtra(HistoryBroadcast.EXTRA_BOLUS_TOTAL, 0D);
        TDD tdd = new TDD(date.getTime(), bolus, basal, bolus + basal);
        MainApp.getDbHelper().createOrUpdateTDD(tdd);
    }

    void processCannulaFilledIntent(Intent intent) {
        Date date = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        uploadCareportalEvent(date, CareportalEvent.SITECHANGE);
    }

    void processCartridgeInsertedIntent(Intent intent) {
        Date date = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        uploadCareportalEvent(date, CareportalEvent.INSULINCHANGE);
    }

    void processBatteryInsertedIntent(Intent intent) {
        Date date = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        uploadCareportalEvent(date, CareportalEvent.PUMPBATTERYCHANGE);
    }

    private void uploadCareportalEvent(Date date, String event) {
        if (SP.getBoolean("insight_automatic_careportal_events", false)) {
            if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date.getTime()) != null) return;
            try {
                JSONObject data = new JSONObject();
                String enteredBy = SP.getString("careportal_enteredby", "");
                if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
                data.put("created_at", DateUtil.toISOString(date));
                data.put("eventType", event);
                NSUpload.uploadCareportalEntryToNS(data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    void processOccurenceOfAlertIntent(Intent intent) {
        if (SP.getBoolean("insight_automatic_careportal_events", false)) {
            Date date = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
            String alertType = intent.getStringExtra(HistoryBroadcast.EXTRA_ALERT_TYPE);
            if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date.getTime()) != null) return;
            logNote(date, MainApp.gs(getAlertText(alertType)));
        }
    }

    void processPumpStatusChangedIntent(Intent intent) {
        Date newStatusTime = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        if (SP.getBoolean("insight_automatic_careportal_events", false)) {
            String newStatus = intent.getStringExtra(HistoryBroadcast.EXTRA_NEW_STATUS);
            switch (newStatus) {
                case "STARTED":
                    logNote(newStatusTime, MainApp.gs(R.string.pump_started));
                    break;
                case "STOPPED":
                    logNote(newStatusTime, MainApp.gs(R.string.pump_stopped));
                    break;
                case "PAUSED":
                    logNote(newStatusTime, MainApp.gs(R.string.pump_paused));
                    break;
            }
        }
        if (intent.hasExtra(HistoryBroadcast.EXTRA_OLD_STATUS_TIME)) {
            String oldStatus = intent.getStringExtra(HistoryBroadcast.EXTRA_OLD_STATUS);
            if (oldStatus.equals("STOPPED")) {
                Date oldStatusTime = getDateExtra(intent, HistoryBroadcast.EXTRA_OLD_STATUS_TIME);
                int duration = (int) ((newStatusTime.getTime() - oldStatusTime.getTime()) / 60000);

                long serialNumber = Long.parseLong(intent.getStringExtra(HistoryBroadcast.EXTRA_PUMP_SERIAL_NUMBER));
                long recordId = intent.getLongExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
                long uniqueRecordId = getRecordUniqueID(serialNumber, recordId);

                logAdapter.createTBRrecord(oldStatusTime, 0, duration, uniqueRecordId);
            }
        }
    }

    private void logNote(Date date, String note) {
        try {
            if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date.getTime()) != null) return;
            JSONObject data = new JSONObject();
            String enteredBy = SP.getString("careportal_enteredby", "");
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            data.put("created_at", DateUtil.toISOString(date));
            data.put("eventType", CareportalEvent.NOTE);
            data.put("notes", note);
            NSUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private int getAlertText(String type) {
        if (type.equals("Error6MechanicalError")) return R.string.alert_e6;
        if (type.equals("Error7ElectronicError")) return R.string.alert_e7;
        if (type.equals("Error10RewindError")) return R.string.alert_e10;
        if (type.equals("Error13LanguageError")) return R.string.alert_e13;
        if (type.equals("Maintenance20CartridgeNotInserted")) return R.string.alert_m20;
        if (type.equals("Maintenance21CartridgeEmpty")) return R.string.alert_m21;
        if (type.equals("Maintenance22BatteryEmpty")) return R.string.alert_m22;
        if (type.equals("Maintenance23AutomaticOff")) return R.string.alert_m23;
        if (type.equals("Maintenance24Occlusion")) return R.string.alert_m24;
        if (type.equals("Maintenance25LoantimeOver")) return R.string.alert_m25;
        if (type.equals("Maintenance26CartridgeChangeNotCompleted")) return R.string.alert_m26;
        if (type.equals("Maintenance27DataDownloadFailed")) return R.string.alert_m27;
        if (type.equals("Maintenance28PauseModeTimeout")) return R.string.alert_m28;
        if (type.equals("Maintenance29BatteryTypeNotSet")) return R.string.alert_m29;
        if (type.equals("Maintenance30CartridgeTypeNotSet")) return R.string.alert_m30;
        if (type.equals("Warning31CartridgeLow")) return R.string.alert_w31;
        if (type.equals("Warning32BatteryLow")) return R.string.alert_w32;
        if (type.equals("Warning33InvalidDateTime")) return R.string.alert_w33;
        if (type.equals("Warning34EndOfWarranty")) return R.string.alert_w34;
        if (type.equals("Warning36TBRCancelled")) return R.string.alert_w36;
        if (type.equals("Warning38BolusCancelled")) return R.string.alert_w38;
        if (type.equals("Warning39LoantimeWarning")) return R.string.alert_w39;
        return 0;
    }
}
