package info.nightscout.androidaps.plugins.PumpInsight.history;

import android.content.Intent;

import java.util.Date;

import sugar.free.sightparser.handling.HistoryBroadcast;

/**
 * Created by jamorham on 27/01/2018.
 *
 * Parse inbound logbook intents
 *
 */

public class HistoryIntentAdapter {

    private HistoryLogAdapter logAdapter = new HistoryLogAdapter();

    private static Date getDateExtra(Intent intent, String name) {
        return (Date) intent.getSerializableExtra(name);
    }

    private static void log(String msg) {
        android.util.Log.e("HistoryIntentAdapter", msg);
    }

    private static long getRecordUniqueID(long pump_serial_number, long pump_record_id) {
        return (pump_serial_number * 10000000) + pump_record_id;
    }

    void processTBRIntent(Intent intent) {

        final int pump_tbr_duration = intent.getIntExtra(HistoryBroadcast.EXTRA_DURATION, -1);
        final int pump_tbr_percent = intent.getIntExtra(HistoryBroadcast.EXTRA_TBR_AMOUNT, -1);
        final int pump_record_id = intent.getIntExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        final long pump_serial_number = Long.parseLong(intent.getStringExtra(HistoryBroadcast.EXTRA_PUMP_SERIAL_NUMBER));
        final Date event_time = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        final Date start_time = getDateExtra(intent, HistoryBroadcast.EXTRA_START_TIME);

        if ((pump_tbr_duration == -1) || (pump_tbr_percent == -1) || (pump_record_id == -1)) {
            log("Invalid TBR record!!!");
            return;
        }

        final long record_unique_id = getRecordUniqueID(pump_serial_number, pump_record_id);

        // other sanity checks
        log("Creating TBR record: " + pump_tbr_percent + "% " + pump_tbr_duration + "m" + " id:" + record_unique_id);
        logAdapter.createTBRrecord(start_time, pump_tbr_percent, pump_tbr_duration, record_unique_id);

    }

    void processDeliveredBolusIntent(Intent intent) {

        final String bolus_type = intent.getStringExtra(HistoryBroadcast.EXTRA_BOLUS_TYPE);
        final int pump_record_id = intent.getIntExtra(HistoryBroadcast.EXTRA_EVENT_NUMBER, -1);
        final long pump_serial_number = Long.parseLong(intent.getStringExtra(HistoryBroadcast.EXTRA_PUMP_SERIAL_NUMBER));
        final Date event_time = getDateExtra(intent, HistoryBroadcast.EXTRA_EVENT_TIME);
        final Date start_time = getDateExtra(intent, HistoryBroadcast.EXTRA_START_TIME);
        final float immediate_amount = intent.getFloatExtra(HistoryBroadcast.EXTRA_IMMEDIATE_AMOUNT, -1);
        final float extended_insulin = intent.getFloatExtra(HistoryBroadcast.EXTRA_EXTENDED_AMOUNT, -1);
        final int extended_minutes = intent.getIntExtra(HistoryBroadcast.EXTRA_DURATION, -1);

        final long record_unique_id = getRecordUniqueID(pump_serial_number, pump_record_id);

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
}
