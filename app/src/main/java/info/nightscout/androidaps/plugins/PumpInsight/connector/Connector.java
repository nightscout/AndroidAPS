package info.nightscout.androidaps.plugins.PumpInsight.connector;

import android.content.Intent;
import android.os.PowerManager;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventFeatureRunning;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver;
import info.nightscout.androidaps.plugins.PumpInsight.history.LiveHistory;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
import info.nightscout.androidaps.plugins.PumpInsight.utils.StatusItem;
import info.nightscout.utils.SP;
import sugar.free.sightparser.handling.ServiceConnectionCallback;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.StatusCallback;
import sugar.free.sightparser.pipeline.Status;

import static sugar.free.sightparser.handling.HistoryBroadcast.ACTION_START_RESYNC;
import static sugar.free.sightparser.handling.HistoryBroadcast.ACTION_START_SYNC;
import static sugar.free.sightparser.handling.SightService.COMPATIBILITY_VERSION;

/**
 * Created by jamorham on 23/01/2018.
 *
 * Connects to SightRemote app service using SightParser library
 *
 * SightRemote and SightParser created by Tebbe Ubben
 *
 * Original proof of concept SightProxy by jamorham
 *
 */

public class Connector {

    // TODO connection statistics

    private static final String TAG = "InsightConnector";
    private static final String COMPANION_APP_PACKAGE = "sugar.free.sightremote";
    private final static long FRESH_MS = 70000;
    private static final Map<Status, Long> statistics = new HashMap<>();
    private static volatile Connector instance;
    private static volatile HistoryReceiver historyReceiver;
    private static volatile long stayConnectedTill = -1;
    private static volatile long stayConnectedTime = 0;
    private static volatile boolean disconnect_thread_running = false;
    private volatile SightServiceConnector serviceConnector;
    private volatile Status lastStatus = null;
    private String compatabilityMessage = null;
    private volatile long lastStatusTime = -1;
    private volatile long lastContactTime = -1;
    private boolean companionAppInstalled = false;
    private int serviceReconnects = 0;
    private StatusCallback statusCallback = new StatusCallback() {
        @Override
        public synchronized void onStatusChange(Status status, long statusTime, long waitTime) {

            if ((status != lastStatus) || (Helpers.msSince(lastStatusTime) > 2000)) {
                log("Status change: " + status);

                updateStatusStatistics(lastStatus, lastStatusTime);
                lastStatus = status;
                lastStatusTime = Helpers.tsl();

                if (status == Status.CONNECTED) {
                    lastContactTime = lastStatusTime;
                    extendKeepAliveIfActive();
                }

                MainApp.bus().post(new EventInsightUpdateGui());
            } else {
                log("Same status as before: " + status);
            }
        }

    };
    private ServiceConnectionCallback connectionCallback = new ServiceConnectionCallback() {

        @Override
        public synchronized void onServiceConnected() {
            log("On service connected");
            try {
                final String remoteVersion = serviceConnector.getRemoteVersion();
                if (remoteVersion.equals(COMPATIBILITY_VERSION)) {
                    serviceConnector.connect();
                } else {
                    log("PROTOCOL VERSION MISMATCH!  local: " + COMPATIBILITY_VERSION + " remote: " + remoteVersion);
                    statusCallback.onStatusChange(Status.INCOMPATIBLE, 0, 0);
                    compatabilityMessage = MainApp.gs(R.string.insight_incompatible_compantion_app_we_need_version) + " " + getLocalVersion();
                    serviceConnector.disconnectFromService();

                }
            } catch (NullPointerException e) {
                log("ERROR: null pointer when trying to connect to pump");
            }
            statusCallback.onStatusChange(safeGetStatus(), 0, 0);
        }

        @Override
        public synchronized void onServiceDisconnected() {
            log("Disconnected from service");
            if (Helpers.ratelimit("insight-automatic-reconnect", 30)) {
                log("Scheduling automatic service reconnection");
                Helpers.runOnUiThreadDelayed(new Runnable() {
                    @Override
                    public void run() {
                        init();
                    }
                }, 20000);
            }
        }
    };

    private Connector() {
        initializeHistoryReceiver();
        MainApp.bus().register(this);
    }

    public static Connector get() {
        if (instance == null) {
            init_instance();
        }
        return instance;
    }

    private synchronized static void init_instance() {
        if (instance == null) {
            instance = new Connector();
        }
    }

    private static boolean isCompanionAppInstalled() {
        return Helpers.checkPackageExists(MainApp.instance(), TAG, COMPANION_APP_PACKAGE);
    }

    public static void connectToPump() {
        connectToPump(0);
    }

    public synchronized static void connectToPump(long keep_alive) {
        log("Attempting to connect to pump.");
        if (keep_alive > 0 && Helpers.tsl() + keep_alive > stayConnectedTill) {
            stayConnectedTime = keep_alive;
            stayConnectedTill = Helpers.tsl() + keep_alive;
            log("Staying connected till: " + Helpers.dateTimeText(stayConnectedTill));
            delayedDisconnectionThread();
        }
        get().getServiceConnector().connect();
    }

    public static void disconnectFromPump() {
        if (Helpers.tsl() >= stayConnectedTill) {
            log("Requesting real pump disconnect");
            get().getServiceConnector().disconnect();
        } else {
            log("Cannot disconnect as due to keep alive till: " + Helpers.dateTimeText(stayConnectedTill));
            // TODO set a disconnection timer?
        }
    }

    static void log(String msg) {
        android.util.Log.e("INSIGHTPUMP", msg);
    }

    static String getLocalVersion() {
        return COMPATIBILITY_VERSION;
    }

    private static String statusToString(Status status) {
        switch (status) {

            case EXCHANGING_KEYS:
                return MainApp.gs(R.string.connecting).toUpperCase();
            case WAITING_FOR_CODE_CONFIRMATION:
                return MainApp.gs(R.string.insight_waiting_for_code).toUpperCase();
            case CODE_REJECTED:
                return MainApp.gs(R.string.insight_code_rejected).toUpperCase();
            case APP_BINDING:
                return MainApp.gs(R.string.insight_app_binding).toUpperCase();
            case CONNECTING:
                return MainApp.gs(R.string.connecting).toUpperCase();
            case CONNECTED:
                return MainApp.gs(R.string.connected).toUpperCase();
            case DISCONNECTED:
                return MainApp.gs(R.string.disconnected).toUpperCase();
            case NOT_AUTHORIZED:
                return MainApp.gs(R.string.insight_not_authorized).toUpperCase();
            case INCOMPATIBLE:
                return MainApp.gs(R.string.insight_incompatible).toUpperCase();

            default:
                return status.toString();
        }
    }

    private static synchronized void extendKeepAliveIfActive() {
        if (keepAliveActive()) {
            if (Helpers.ratelimit("extend-insight-keepalive", 10)) {
                stayConnectedTill = Helpers.tsl() + stayConnectedTime;
                log("Keep-alive extended until: " + Helpers.dateTimeText(stayConnectedTill));
            }
        }
    }

    private static boolean keepAliveActive() {
        return Helpers.tsl() <= stayConnectedTill;
    }

    public static String getKeepAliveString() {
        if (keepAliveActive()) {
            return MainApp.gs(R.string.insight_keepalive_format_string,
                    stayConnectedTime / 1000, Helpers.hourMinuteSecondString(stayConnectedTill));

        } else {
            return null;
        }
    }

    private static synchronized void delayedDisconnectionThread() {
        if (keepAliveActive()) {
            if (!disconnect_thread_running) {
                disconnect_thread_running = true;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final PowerManager.WakeLock wl = Helpers.getWakeLock("insight-disconnection-timer", 600000);
                        try {
                            while (disconnect_thread_running && keepAliveActive()) {
                                if (Helpers.ratelimit("insight-expiry-notice", 5)) {
                                    log("Staying connected thread expires: " + Helpers.dateTimeText(stayConnectedTill));
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    //
                                }
                            }

                            if (disconnect_thread_running) {
                                log("Sending the real delayed disconnect");
                                get().getServiceConnector().disconnect();
                            } else {
                                log("Disconnect thread already terminating");
                            }
                        } finally {
                            Helpers.releaseWakeLock(wl);
                            disconnect_thread_running = false;
                        }
                    }
                }).start();
            } else {
                log("Disconnect thread already running");
            }
        }
    }

    private static long percentage(long t, long total) {
        return (long) (Helpers.roundDouble(((double) t * 100) / total, 0));
    }

    public synchronized void shutdown() {
        if (instance != null) {
            log("Attempting to shut down connector");
            try {
                disconnect_thread_running = false;
                try {
                    instance.serviceConnector.setConnectionCallback(null);
                } catch (Exception e) {
                    //
                }
                try {
                    instance.serviceConnector.removeStatusCallback(statusCallback);
                } catch (Exception e) {
                    //
                }
                try {
                    instance.serviceConnector.disconnect();
                } catch (Exception e) {
                    log("Exception disconnecting: " + e);
                }
                try {
                    instance.serviceConnector.disconnectFromService();
                } catch (Exception e) {
                    log("Excpetion disconnecting service: " + e);
                }
                instance.serviceConnector = null;
                instance = null;
            } catch (Exception e) {
                log("Exception shutting down: " + e);
            }
        }
    }

    @SuppressWarnings("AccessStaticViaInstance")
    private synchronized void initializeHistoryReceiver() {
        if (historyReceiver == null) {
            historyReceiver = new HistoryReceiver();
        }
        historyReceiver.registerHistoryReceiver();
    }

    public synchronized void init() {
        log("Connector::init()");
        if (serviceConnector == null) {
            companionAppInstalled = isCompanionAppInstalled();
            if (companionAppInstalled) {
                serviceConnector = new SightServiceConnector(MainApp.instance());
                serviceConnector.removeStatusCallback(statusCallback);
                serviceConnector.addStatusCallback(statusCallback);
                serviceConnector.setConnectionCallback(connectionCallback);
                serviceConnector.connectToService();
                log("Trying to connect");
            } else {
                log("Not trying init due to missing companion app");
            }
        } else {
            if (!serviceConnector.isConnectedToService()) {
                if (serviceReconnects > 0) {
                    serviceConnector = null;
                    init();
                } else {
                    log("Trying to reconnect to service (" + serviceReconnects + ")");
                    serviceConnector.connectToService();
                    serviceReconnects++;
                }
            } else {
                serviceReconnects = 0; // everything ok
            }
        }
    }

    public SightServiceConnector getServiceConnector() {
        init();
        return serviceConnector;
    }

    public String getCurrent() {
        init();
        return safeGetStatus().toString();
    }

    public Status safeGetStatus() {
        try {
            if (isConnected()) return serviceConnector.getStatus();
            return Status.DISCONNECTED;
        } catch (IllegalArgumentException e) {
            return Status.INCOMPATIBLE;
        }
    }

    public Status getLastStatus() {
        return lastStatus;
    }

    public boolean isConnected() {
        return serviceConnector != null && serviceConnector.isConnectedToService();
    }

    public boolean isPumpConnected() {
        return isConnected() && getLastStatus() == Status.CONNECTED;
    }

    public boolean isPumpConnecting() {
        return isConnected() && getLastStatus() == Status.CONNECTING;
    }

    public long getLastContactTime() {
        return lastContactTime;
    }

    public String getLastStatusMessage() {

        if (!companionAppInstalled) {
            return MainApp.gs(R.string.insight_companion_app_not_installed);
        }

        if (!isConnected()) {
            log("Not connected to companion");
            if (Helpers.ratelimit("insight-app-not-connected", 5)) {
                init();
            }

            if ((lastStatus == null) || (lastStatus != Status.INCOMPATIBLE)) {
                if (compatabilityMessage != null) {
                    // if disconnected but previous state was incompatible
                    return compatabilityMessage;
                } else {
                    return MainApp.gs(R.string.insight_not_connected_to_companion_app);
                }
            }
        }

        if (lastStatus == null) {
            return MainApp.gs(R.string.insight_unknown);
        }

        switch (lastStatus) {
            case CONNECTED:
                if (Helpers.msSince(lastStatusTime) > (60 * 10 * 1000)) {
                    tryToGetPumpStatusAgain();
                }
                break;
            case INCOMPATIBLE:
                return statusToString(lastStatus) + " " + MainApp.gs(R.string.insight_needs) + " " + getLocalVersion();
        }
        return statusToString(lastStatus);
    }

    public String getNiceLastStatusTime() {
        if (lastStatusTime < 1) {
            return MainApp.gs(R.string.insight_startup_uppercase);
        } else {
            return Helpers.niceTimeScalar(Helpers.msSince(lastStatusTime)) + " " + MainApp.gs(R.string.ago);
        }
    }

    public boolean uiFresh() {
        // todo check other changes

        if (Helpers.msSince(lastStatusTime) < FRESH_MS) {
            return true;
        }
        if (Helpers.msSince(LiveHistory.getStatusTime()) < FRESH_MS) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("AccessStaticViaInstance")
    public void tryToGetPumpStatusAgain() {
        if (Helpers.ratelimit("insight-retry-status-request", 5)) {
            try {
                MainApp.getConfigBuilder().getCommandQueue().readStatus("Insight. Status missing", null);
            } catch (NullPointerException e) {
                //
            }
        }
    }

    public void requestHistorySync() {
        requestHistorySync(0);
    }

    public void requestHistoryReSync() {
        requestHistoryReSync(0);
    }

    public void requestHistorySync(long delay) {
        if (Helpers.ratelimit("insight-history-sync-request", 10)) {
            final Intent intent = new Intent(ACTION_START_SYNC);
            sendBroadcastToCompanion(intent, delay);
        }
    }

    public void requestHistoryReSync(long delay) {
        if (Helpers.ratelimit("insight-history-resync-request", 300)) {
            final Intent intent = new Intent(ACTION_START_RESYNC);
            sendBroadcastToCompanion(intent, delay);
        }
    }

    private void sendBroadcastToCompanion(final Intent intent, final long delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final PowerManager.WakeLock wl = Helpers.getWakeLock("insight-companion-delay", 60000);
                intent.setPackage(COMPANION_APP_PACKAGE);
                intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                try {
                    if (delay > 0) {

                        Thread.sleep(delay);
                    }
                } catch (InterruptedException e) {
                    //
                } finally {
                    Helpers.releaseWakeLock(wl);
                }
                MainApp.instance().sendBroadcast(intent);
            }
        }).start();
    }

    public boolean lastStatusRecent() {
        return true; // TODO evaluate whether current
    }

    private void updateStatusStatistics(Status last, long since) {
        if ((last != null) && (since > 0)) {
            Long total = statistics.get(last);
            if (total == null) total = 0L;
            statistics.put(last, total + Helpers.msSince(since));
            log("Updated statistics for: " + last + " total: " + Helpers.niceTimeScalar(statistics.get(last)));
            // TODO persist data
        }
    }

    public List<StatusItem> getStatusStatistics() {
        final List<StatusItem> l = new ArrayList<>();
        long total = 0;
        for (Map.Entry entry : statistics.entrySet()) {
            total += getEntryTime(entry);
        }
        for (Map.Entry entry : statistics.entrySet()) {
            if ((long) entry.getValue() > 1000) {
                l.add(new StatusItem(MainApp.gs(R.string.statistics) + " " + Helpers.capitalize(entry.getKey().toString()),
                        new Formatter().format("%4s %12s",
                                percentage(getEntryTime(entry), total) + "%",
                                Helpers.niceTimeScalar(getEntryTime(entry))).toString()));
            }
        }
        return l;
    }

    private long getEntryTime(Map.Entry entry) {
        return (long) entry.getValue() + (entry.getKey().equals(lastStatus) ? Helpers.msSince(lastStatusTime) : 0);
    }

    @Subscribe
    public void onStatusEvent(final EventFeatureRunning ev) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    if (SP.getBoolean("insight_preemptive_connect", true)) {
                        switch (ev.getFeature()) {
                            case WIZARD:
                                log("Wizard feature detected, preconnecting to pump");
                                connectToPump(120 * 1000);
                                break;
                            case MAIN:
                                log("Main feature detected, preconnecting to pump");
                                connectToPump(30 * 1000);
                                break;
                        }
                    }
                }
            }
        }).start();
    }
}
