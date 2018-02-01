package info.nightscout.androidaps.plugins.PumpInsight.connector;

import android.content.Intent;
import android.os.PowerManager;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver;
import info.nightscout.androidaps.plugins.PumpInsight.history.LiveHistory;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
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

    private static final String TAG = "InsightConnector";
    private static final String COMPANION_APP_PACKAGE = "sugar.free.sightremote";
    private final static long FRESH_MS = 70000;
    private static volatile Connector instance;
    private static volatile HistoryReceiver historyReceiver;
    private volatile SightServiceConnector serviceConnector;
    private volatile Status lastStatus = null;
    private String compatabilityMessage = null;
    private volatile long lastStatusTime = -1;
    private volatile long lastContactTime = -1;
    private boolean companionAppInstalled = false;
    private int serviceReconnects = 0;
    private StatusCallback statusCallback = new StatusCallback() {
        @Override
        public synchronized void onStatusChange(Status status) {

            log("Status change: " + status);
            lastStatus = status;
            lastStatusTime = Helpers.tsl();
            if (status == Status.CONNECTED) {
                lastContactTime = lastStatusTime;
            }

            MainApp.bus().post(new EventInsightPumpUpdateGui());
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
                    statusCallback.onStatusChange(Status.INCOMPATIBLE);
                    compatabilityMessage = gs(R.string.insight_incompatible_compantion_app_we_need_version) + " " + getLocalVersion();
                    serviceConnector.disconnectFromService();

                }
            } catch (NullPointerException e) {
                log("ERROR: null pointer when trying to connect to pump");
            }
            statusCallback.onStatusChange(safeGetStatus());
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
        log("Attempting to connect to pump");
        get().getServiceConnector().connect();
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
                return gs(R.string.connecting).toUpperCase();
            case WAITING_FOR_CODE_CONFIRMATION:
                return gs(R.string.insight_waiting_for_code).toUpperCase();
            case CODE_REJECTED:
                return gs(R.string.insight_code_rejected).toUpperCase();
            case APP_BINDING:
                return gs(R.string.insight_app_binding).toUpperCase();
            case CONNECTING:
                return gs(R.string.connecting).toUpperCase();
            case CONNECTED:
                return gs(R.string.connected).toUpperCase();
            case DISCONNECTED:
                return gs(R.string.disconnected).toUpperCase();
            case NOT_AUTHORIZED:
                return gs(R.string.insight_not_authorized).toUpperCase();
            case INCOMPATIBLE:
                return gs(R.string.insight_incompatible).toUpperCase();

            default:
                return status.toString();
        }
    }

    private static String gs(int id) {
        return MainApp.instance().getString(id);
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
            return gs(R.string.insight_companion_app_not_installed);
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
                    return gs(R.string.insight_not_connected_to_companion_app);
                }
            }
        }

        if (lastStatus == null) {
            return gs(R.string.insight_unknown);
        }

        switch (lastStatus) {
            case CONNECTED:
                if (Helpers.msSince(lastStatusTime) > (60 * 10 * 1000)) {
                    tryToGetPumpStatusAgain();
                }
                break;
            case INCOMPATIBLE:
                return statusToString(lastStatus) + " " + gs(R.string.insight_needs) + " " + getLocalVersion();
        }
        return statusToString(lastStatus);
    }

    public String getNiceLastStatusTime() {
        if (lastStatusTime < 1) {
            return gs(R.string.insight_startup_uppercase);
        } else {
            return Helpers.niceTimeScalar(Helpers.msSince(lastStatusTime)) + " " + gs(R.string.ago);
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

}
