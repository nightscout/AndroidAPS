package info.nightscout.androidaps.plugins.PumpInsight.history;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import static info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver.Status.BUSY;
import static info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver.Status.SYNCED;
import static info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver.Status.SYNCING;
import static sugar.free.sightparser.handling.HistoryBroadcast.*;

/**
 * Created by jamorham on 27/01/2018.
 */

public class HistoryReceiver {

    private static BroadcastReceiver historyReceiver;
    private volatile static Status status = Status.IDLE;
    private volatile HistoryIntentAdapter intentAdapter;

    public HistoryReceiver() {
        initializeHistoryReceiver();
    }

    public static synchronized void registerHistoryReceiver() {
        try {
            MainApp.instance().unregisterReceiver(historyReceiver);
        } catch (Exception e) {
            //
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PUMP_STATUS_CHANGED);
        filter.addAction(ACTION_BOLUS_PROGRAMMED);
        filter.addAction(ACTION_BOLUS_DELIVERED);
        filter.addAction(ACTION_END_OF_TBR);
        filter.addAction(ACTION_DAILY_TOTAL);
        filter.addAction(ACTION_SYNC_STARTED);
        filter.addAction(ACTION_STILL_SYNCING);
        filter.addAction(ACTION_SYNC_FINISHED);
        filter.addAction(ACTION_CANNULA_FILLED);
        filter.addAction(ACTION_CARTRIDGE_INSERTED);
        filter.addAction(ACTION_BATTERY_INSERTED);
        filter.addAction(ACTION_OCCURENCE_OF_ALERT);
        filter.addAction(ACTION_PUMP_STATUS_CHANGED);

        MainApp.instance().registerReceiver(historyReceiver, filter);
    }

    // History

    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMPHR", msg);
    }

    public static String getStatusString() {
        return status.toString();
    }

    private synchronized void initializeHistoryReceiver() {
        historyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {

                final String action = intent.getAction();
                if (action == null) return;

                if (intentAdapter == null) {
                    synchronized (this) {
                        if (intentAdapter == null) {
                            intentAdapter = new HistoryIntentAdapter();
                        }
                    }
                }

                switch (action) {
                    case ACTION_SYNC_STARTED:
                        status = SYNCING;
                        break;
                    case ACTION_STILL_SYNCING:
                        status = BUSY;
                        break;
                    case ACTION_SYNC_FINISHED:
                        status = SYNCED;
                        break;
                    case ACTION_BOLUS_DELIVERED:
                        intentAdapter.processDeliveredBolusIntent(intent);
                        break;
                    case ACTION_END_OF_TBR:
                        intentAdapter.processTBRIntent(intent);
                        break;
                    case ACTION_DAILY_TOTAL:
                        intentAdapter.processDailyTotalIntent(intent);
                        break;
                    case ACTION_CANNULA_FILLED:
                        intentAdapter.processCannulaFilledIntent(intent);
                        break;
                    case ACTION_CARTRIDGE_INSERTED:
                        intentAdapter.processCartridgeInsertedIntent(intent);
                        break;
                    case ACTION_BATTERY_INSERTED:
                        intentAdapter.processBatteryInsertedIntent(intent);
                        break;
                    case ACTION_OCCURENCE_OF_ALERT:
                        intentAdapter.processOccurenceOfAlertIntent(intent);
                        break;
                    case ACTION_PUMP_STATUS_CHANGED:
                        intentAdapter.processPumpStatusChangedIntent(intent);
                        break;
                }
            }
        };
    }

    enum Status {
        IDLE(R.string.insight_history_idle),
        SYNCING(R.string.insight_history_syncing),
        BUSY(R.string.insight_history_busy),
        SYNCED(R.string.insight_history_synced);

        private final int string_id;

        Status(int string_id)  {
            this.string_id = string_id;
        }

        @Override
        public String toString() {
            return MainApp.gs(string_id);
        }
    }

}
