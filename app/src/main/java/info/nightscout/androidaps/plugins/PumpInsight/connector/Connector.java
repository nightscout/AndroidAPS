package info.nightscout.androidaps.plugins.PumpInsight.connector;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
import sugar.free.sightparser.handling.ServiceConnectionCallback;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.StatusCallback;
import sugar.free.sightparser.pipeline.Status;

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
    private static volatile Connector instance;
    private volatile SightServiceConnector serviceConnector;
    private volatile Status lastStatus = null;
    private volatile long lastStatusTime = -1;
    private boolean companionAppInstalled = false;
    private int serviceReconnects = 0;

    private StatusCallback statusCallback = new StatusCallback() {
        @Override
        public void onStatusChange(Status status) {

            synchronized (this) {
                log("Status change: " + status);
                lastStatus = status;
                lastStatusTime = Helpers.tsl();
                switch (status) {
                    // TODO automated reactions to change in status
                }
                MainApp.bus().post(new EventInsightPumpUpdateGui());
            }
        }
    };

    private ServiceConnectionCallback connectionCallback = new ServiceConnectionCallback() {
        @Override
        public void onServiceConnected() {
            log("On service connected");
            serviceConnector.connect();
            statusCallback.onStatusChange(safeGetStatus());
        }

        @Override
        public void onServiceDisconnected() {
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

    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMP", msg);
    }


    public synchronized void init() {
        log("Connector::init()");
        if (serviceConnector == null) {
            companionAppInstalled = isCompanionAppInstalled();
            if (companionAppInstalled) {
                serviceConnector = new SightServiceConnector(MainApp.instance());
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
        if (isConnected()) return serviceConnector.getStatus();
        return Status.DISCONNECTED;
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

    public String getLastStatusMessage() {

        if (!companionAppInstalled) {
            return "Companion app does not appear to be installed!";
        }

        if (!isConnected()) {
            log("Not connected to companion");
            if (Helpers.ratelimit("insight-app-not-connected", 5)) {
                init();
            }
            return "Not connected to companion app!";
        }

        if (lastStatus == null) {
            return "Unknown";
        }

        switch (lastStatus) {

            case CONNECTED:
                if (lastStatusTime < 1) {
                    tryToGetStatusAgain();
                }

            default:
                return lastStatus.toString();
        }
    }

    private void tryToGetStatusAgain() {
        if (Helpers.ratelimit("insight-retry-status-request", 5)) {
            try {
                MainApp.getConfigBuilder().getCommandQueue().readStatus("Insight. Status missing", null);
            } catch (NullPointerException e) {
                //
            }
        }
    }

    public boolean lastStatusRecent() {
        return true; // TODO evaluate whether current
    }

}
