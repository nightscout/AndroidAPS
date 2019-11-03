package info.nightscout.androidaps.plugins.general.nsclient.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.j256.ormlite.dao.CloseableIterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.nsclient.acks.NSAddAck;
import info.nightscout.androidaps.plugins.general.nsclient.acks.NSAuthAck;
import info.nightscout.androidaps.plugins.general.nsclient.acks.NSUpdateAck;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastAnnouncement;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastCals;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastClearAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastDeviceStatus;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastFood;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastMbgs;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastProfile;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastSgvs;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastStatus;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastTreatment;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastUrgentAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.data.AlarmAck;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSTreatment;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class NSClientService extends Service {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);
    private CompositeDisposable disposable = new CompositeDisposable();

    static public PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new NSClientService.LocalBinder();

    static ProfileStore profileStore;

    static public Handler handler;

    public static Socket mSocket;
    public static boolean isConnected = false;
    public static boolean hasWriteAuth = false;
    private static Integer dataCounter = 0;
    private static Integer connectCounter = 0;


    public static String nightscoutVersionName = "";
    public static Integer nightscoutVersionCode = 0;

    private boolean nsEnabled = false;
    static public String nsURL = "";
    private String nsAPISecret = "";
    private String nsDevice = "";
    private Integer nsHours = 48;

    public long lastResendTime = 0;

    public long latestDateInReceivedData = 0;

    private String nsAPIhashCode = "";

    public static UploadQueue uploadQueue = new UploadQueue();

    private ArrayList<Long> reconnections = new ArrayList<>();
    private int WATCHDOG_INTERVAL_MINUTES = 2;
    private int WATCHDOG_RECONNECT_IN = 15;
    private int WATCHDOG_MAXCONNECTIONS = 5;

    public NSClientService() {
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSClientService.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NSClientService");
        initialize();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock.acquire();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventConfigBuilderChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (nsEnabled != NSClientPlugin.getPlugin().isEnabled(PluginType.GENERAL)) {
                        latestDateInReceivedData = 0;
                        destroy();
                        initialize();
                    }
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (event.isChanged(R.string.key_nsclientinternal_url) ||
                            event.isChanged(R.string.key_nsclientinternal_api_secret) ||
                            event.isChanged(R.string.key_nsclientinternal_paused)
                    ) {
                        latestDateInReceivedData = 0;
                        destroy();
                        initialize();
                    }
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (L.isEnabled(L.NSCLIENT))
                        log.debug("EventAppExit received");
                    destroy();
                    stopSelf();
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNSClientRestart.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    latestDateInReceivedData = 0;
                    restart();
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(NSAuthAck.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> processAuthAck(event), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(NSUpdateAck.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> processUpdateAck(event), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(NSAddAck.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> processAddAck(event), FabricPrivacy::logException)
        );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposable.clear();
        if (mWakeLock.isHeld()) mWakeLock.release();
    }

    public void processAddAck(NSAddAck ack) {
        if (ack.nsClientID != null) {
            uploadQueue.removeID(ack.json);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBADD", "Acked " + ack.nsClientID));
        } else {
            RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "DBADD Unknown response"));
        }
    }

    public void processUpdateAck(NSUpdateAck ack) {
        if (ack.result) {
            uploadQueue.removeID(ack.action, ack._id);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBUPDATE/DBREMOVE", "Acked " + ack._id));
        } else {
            RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "DBUPDATE/DBREMOVE Unknown response"));
        }
    }

    public void processAuthAck(NSAuthAck ack) {
        String connectionStatus = "Authenticated (";
        if (ack.read) connectionStatus += "R";
        if (ack.write) connectionStatus += "W";
        if (ack.write_treatment) connectionStatus += "T";
        connectionStatus += ')';
        isConnected = true;
        hasWriteAuth = ack.write && ack.write_treatment;
        RxBus.INSTANCE.send(new EventNSClientStatus(connectionStatus));
        RxBus.INSTANCE.send(new EventNSClientNewLog("AUTH", connectionStatus));
        if (!ack.write) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "Write permission not granted !!!!"));
        }
        if (!ack.write_treatment) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "Write treatment permission not granted !!!!"));
        }
        if (!hasWriteAuth) {
            Notification noperm = new Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, MainApp.gs(R.string.nowritepermission), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(noperm));
        } else {
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.NSCLIENT_NO_WRITE_PERMISSION));
        }
    }

    public class LocalBinder extends Binder {
        public NSClientService getServiceInstance() {
            return NSClientService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    public void initialize() {
        dataCounter = 0;

        readPreferences();

        if (!nsAPISecret.equals(""))
            nsAPIhashCode = Hashing.sha1().hashString(nsAPISecret, Charsets.UTF_8).toString();

        RxBus.INSTANCE.send(new EventNSClientStatus("Initializing"));
        if (!NSClientPlugin.getPlugin().isAllowed()) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "not allowed"));
            RxBus.INSTANCE.send(new EventNSClientStatus("Not allowed"));
        } else if (NSClientPlugin.getPlugin().paused) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "paused"));
            RxBus.INSTANCE.send(new EventNSClientStatus("Paused"));
        } else if (!nsEnabled) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "disabled"));
            RxBus.INSTANCE.send(new EventNSClientStatus("Disabled"));
        } else if (!nsURL.equals("")) {
            try {
                RxBus.INSTANCE.send(new EventNSClientStatus("Connecting ..."));
                IO.Options opt = new IO.Options();
                opt.forceNew = true;
                opt.reconnection = true;
                mSocket = IO.socket(nsURL, opt);
                mSocket.on(Socket.EVENT_CONNECT, onConnect);
                mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                mSocket.on(Socket.EVENT_PING, onPing);
                RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "do connect"));
                mSocket.connect();
                mSocket.on("dataUpdate", onDataUpdate);
                mSocket.on("announcement", onAnnouncement);
                mSocket.on("alarm", onAlarm);
                mSocket.on("urgent_alarm", onUrgentAlarm);
                mSocket.on("clear_alarm", onClearAlarm);
            } catch (URISyntaxException | RuntimeException e) {
                RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "Wrong URL syntax"));
                RxBus.INSTANCE.send(new EventNSClientStatus("Wrong URL syntax"));
            }
        } else {
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "No NS URL specified"));
            RxBus.INSTANCE.send(new EventNSClientStatus("Not configured"));
        }
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            connectCounter++;
            String socketId = mSocket != null ? mSocket.id() : "NULL";
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "connect #" + connectCounter + " event. ID: " + socketId));
            if (mSocket != null)
                sendAuthMessage(new NSAuthAck());
            watchdog();
        }
    };

    void watchdog() {
        synchronized (reconnections) {
            long now = DateUtil.now();
            reconnections.add(now);
            for (int i = 0; i < reconnections.size(); i++) {
                Long r = reconnections.get(i);
                if (r < now - T.mins(WATCHDOG_INTERVAL_MINUTES).msecs()) {
                    reconnections.remove(r);
                }
            }
            RxBus.INSTANCE.send(new EventNSClientNewLog("WATCHDOG", "connections in last " + WATCHDOG_INTERVAL_MINUTES + " mins: " + reconnections.size() + "/" + WATCHDOG_MAXCONNECTIONS));
            if (reconnections.size() >= WATCHDOG_MAXCONNECTIONS) {
                Notification n = new Notification(Notification.NSMALFUNCTION, MainApp.gs(R.string.nsmalfunction), Notification.URGENT);
                RxBus.INSTANCE.send(new EventNewNotification(n));
                RxBus.INSTANCE.send(new EventNSClientNewLog("WATCHDOG", "pausing for " + WATCHDOG_RECONNECT_IN + " mins"));
                NSClientPlugin.getPlugin().pause(true);
                RxBus.INSTANCE.send(new EventNSClientUpdateGUI());
                new Thread(() -> {
                    SystemClock.sleep(T.mins(WATCHDOG_RECONNECT_IN).msecs());
                    RxBus.INSTANCE.send(new EventNSClientNewLog("WATCHDOG", "reenabling NSClient"));
                    NSClientPlugin.getPlugin().pause(false);
                }).start();
            }
        }
    }

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("disconnect reason: {}", args);
            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "disconnect event"));
        }
    };

    public synchronized void destroy() {
        if (mSocket != null) {
            mSocket.off(Socket.EVENT_CONNECT);
            mSocket.off(Socket.EVENT_DISCONNECT);
            mSocket.off(Socket.EVENT_PING);
            mSocket.off("dataUpdate");
            mSocket.off("announcement");
            mSocket.off("alarm");
            mSocket.off("urgent_alarm");
            mSocket.off("clear_alarm");

            RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "destroy"));
            isConnected = false;
            hasWriteAuth = false;
            mSocket.disconnect();
            mSocket = null;
        }
    }


    public void sendAuthMessage(NSAuthAck ack) {
        JSONObject authMessage = new JSONObject();
        try {
            authMessage.put("client", "Android_" + nsDevice);
            authMessage.put("history", nsHours);
            authMessage.put("status", true); // receive status
            authMessage.put("from", latestDateInReceivedData); // send data newer than
            authMessage.put("secret", nsAPIhashCode);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
            return;
        }
        RxBus.INSTANCE.send(new EventNSClientNewLog("AUTH", "requesting auth"));
        if (mSocket != null)
            mSocket.emit("authorize", authMessage, ack);
    }

    public void readPreferences() {
        nsEnabled = NSClientPlugin.getPlugin().isEnabled(PluginType.GENERAL);
        nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        nsAPISecret = SP.getString(R.string.key_nsclientinternal_api_secret, "");
        nsDevice = SP.getString("careportal_enteredby", "");
    }

    private Emitter.Listener onPing = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("PING", "received"));
            // send data if there is something waiting
            resend("Ping received");
        }
    };

    private Emitter.Listener onAnnouncement = new Emitter.Listener() {
        /*
        {
        "level":0,
        "title":"Announcement",
        "message":"test",
        "plugin":{"name":"treatmentnotify","label":"Treatment Notifications","pluginType":"notification","enabled":true},
        "group":"Announcement",
        "isAnnouncement":true,
        "key":"9ac46ad9a1dcda79dd87dae418fce0e7955c68da"
        }
         */
        @Override
        public void call(final Object... args) {
            JSONObject data;
            try {
                data = (JSONObject) args[0];
            } catch (Exception e) {
                FabricPrivacy.log("Wrong Announcement from NS: " + args[0]);
                log.error("Unhandled exception", e);
                return;
            }
            try {
                RxBus.INSTANCE.send(new EventNSClientNewLog("ANNOUNCEMENT", JsonHelper.safeGetString(data, "message", "received")));
            } catch (Exception e) {
                FabricPrivacy.logException(e);
                log.error("Unhandled exception", e);
            }
            BroadcastAnnouncement.handleAnnouncement(data, getApplicationContext());
            if (L.isEnabled(L.NSCLIENT))
                log.debug(data.toString());
        }
    };

    private Emitter.Listener onAlarm = new Emitter.Listener() {
        /*
        {
        "level":1,
        "title":"Warning HIGH",
        "message":"BG Now: 5 -0.2 → mmol\/L\nRaw BG: 4.8 mmol\/L Čistý\nBG 15m: 4.8 mmol\/L\nIOB: -0.02U\nCOB: 0g",
        "eventName":"high",
        "plugin":{"name":"simplealarms","label":"Simple Alarms","pluginType":"notification","enabled":true},
        "pushoverSound":"climb",
        "debug":{"lastSGV":5,"thresholds":{"bgHigh":180,"bgTargetTop":75,"bgTargetBottom":72,"bgLow":70}},
        "group":"default",
        "key":"simplealarms_1"
        }
         */
        @Override
        public void call(final Object... args) {
            RxBus.INSTANCE.send(new EventNSClientNewLog("ALARM", "received"));
            JSONObject data;
            try {
                data = (JSONObject) args[0];
            } catch (Exception e) {
                FabricPrivacy.log("Wrong alarm from NS: " + args[0]);
                log.error("Unhandled exception", e);
                return;
            }
            BroadcastAlarm.handleAlarm(data, getApplicationContext());
            if (L.isEnabled(L.NSCLIENT))
                log.debug(data.toString());
        }
    };

    private Emitter.Listener onUrgentAlarm = new Emitter.Listener() {
        /*
        {
        "level":2,
        "title":"Urgent HIGH",
        "message":"BG Now: 5.2 -0.1 → mmol\/L\nRaw BG: 5 mmol\/L Čistý\nBG 15m: 5 mmol\/L\nIOB: 0.00U\nCOB: 0g",
        "eventName":"high",
        "plugin":{"name":"simplealarms","label":"Simple Alarms","pluginType":"notification","enabled":true},
        "pushoverSound":"persistent",
        "debug":{"lastSGV":5.2,"thresholds":{"bgHigh":80,"bgTargetTop":75,"bgTargetBottom":72,"bgLow":70}},
        "group":"default",
        "key":"simplealarms_2"
        }
         */
        @Override
        public void call(final Object... args) {
            JSONObject data;
            try {
                data = (JSONObject) args[0];
            } catch (Exception e) {
                FabricPrivacy.log("Wrong Urgent alarm from NS: " + args[0]);
                log.error("Unhandled exception", e);
                return;
            }
            RxBus.INSTANCE.send(new EventNSClientNewLog("URGENTALARM", "received"));
            BroadcastUrgentAlarm.handleUrgentAlarm(data, getApplicationContext());
            if (L.isEnabled(L.NSCLIENT))
                log.debug(data.toString());
        }
    };

    private Emitter.Listener onClearAlarm = new Emitter.Listener() {
        /*
        {
        "clear":true,
        "title":"All Clear",
        "message":"default - Urgent was ack'd",
        "group":"default"
        }
         */
        @Override
        public void call(final Object... args) {
            JSONObject data;
            try {
                data = (JSONObject) args[0];
            } catch (Exception e) {
                FabricPrivacy.log("Wrong Urgent alarm from NS: " + args[0]);
                log.error("Unhandled exception", e);
                return;
            }
            RxBus.INSTANCE.send(new EventNSClientNewLog("CLEARALARM", "received"));
            BroadcastClearAlarm.handleClearAlarm(data, getApplicationContext());
            if (L.isEnabled(L.NSCLIENT))
                log.debug(data.toString());
        }
    };

    private Emitter.Listener onDataUpdate = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            NSClientService.handler.post(new Runnable() {
                @Override
                public void run() {
                    PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "AndroidAPS:NSClientService_onDataUpdate");
                    wakeLock.acquire();
                    try {

                        JSONObject data = (JSONObject) args[0];
                        boolean broadcastProfile = false;
                        try {
                            // delta means only increment/changes are comming
                            boolean isDelta = data.has("delta");
                            boolean isFull = !isDelta;
                            RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "Data packet #" + dataCounter++ + (isDelta ? " delta" : " full")));

                            if (data.has("profiles")) {
                                JSONArray profiles = data.getJSONArray("profiles");
                                if (profiles.length() > 0) {
                                    JSONObject profile = (JSONObject) profiles.get(profiles.length() - 1);
                                    profileStore = new ProfileStore(profile);
                                    broadcastProfile = true;
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("PROFILE", "profile received"));
                                }
                            }

                            if (data.has("status")) {
                                JSONObject status = data.getJSONObject("status");
                                NSSettingsStatus nsSettingsStatus = NSSettingsStatus.getInstance().setData(status);

                                if (!status.has("versionNum")) {
                                    if (status.getInt("versionNum") < Config.SUPPORTEDNSVERSION) {
                                        RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version !!!!"));
                                    }
                                } else {
                                    nightscoutVersionName = nsSettingsStatus.getVersion();
                                    nightscoutVersionCode = nsSettingsStatus.getVersionNum();
                                }
                                BroadcastStatus.handleNewStatus(nsSettingsStatus, MainApp.instance().getApplicationContext(), isDelta);

                    /*  Other received data to 2016/02/10
                        {
                          status: 'ok'
                          , name: env.name
                          , version: env.version
                          , versionNum: versionNum (for ver 1.2.3 contains 10203)
                          , serverTime: new Date().toISOString()
                          , apiEnabled: apiEnabled
                          , careportalEnabled: apiEnabled && env.settings.enable.indexOf('careportal') > -1
                          , boluscalcEnabled: apiEnabled && env.settings.enable.indexOf('boluscalc') > -1
                          , head: env.head
                          , settings: env.settings
                          , extendedSettings: ctx.plugins && ctx.plugins.extendedClientSettings ? ctx.plugins.extendedClientSettings(env.extendedSettings) : {}
                          , activeProfile ..... calculated from treatments or missing
                        }
                     */
                            } else if (!isDelta) {
                                RxBus.INSTANCE.send(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version !!!!"));
                            }

                            // If new profile received or change detected broadcast it
                            if (broadcastProfile && profileStore != null) {
                                BroadcastProfile.handleNewTreatment(profileStore, MainApp.instance().getApplicationContext(), isDelta);
                                RxBus.INSTANCE.send(new EventNSClientNewLog("PROFILE", "broadcasting"));
                            }

                            if (data.has("treatments")) {
                                JSONArray treatments = data.getJSONArray("treatments");
                                JSONArray removedTreatments = new JSONArray();
                                JSONArray updatedTreatments = new JSONArray();
                                JSONArray addedTreatments = new JSONArray();
                                if (treatments.length() > 0)
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + treatments.length() + " treatments"));
                                for (Integer index = 0; index < treatments.length(); index++) {
                                    JSONObject jsonTreatment = treatments.getJSONObject(index);
                                    NSTreatment treatment = new NSTreatment(jsonTreatment);

                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonTreatment);
                                    //Find latest date in treatment
                                    if (treatment.getMills() != null && treatment.getMills() < System.currentTimeMillis())
                                        if (treatment.getMills() > latestDateInReceivedData)
                                            latestDateInReceivedData = treatment.getMills();

                                    if (treatment.getAction() == null) {
                                        addedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("update")) {
                                        updatedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("remove")) {
                                        if (treatment.getMills() != null && treatment.getMills() > System.currentTimeMillis() - 24 * 60 * 60 * 1000L) // handle 1 day old deletions only
                                            removedTreatments.put(jsonTreatment);
                                    }
                                }
                                if (removedTreatments.length() > 0) {
                                    BroadcastTreatment.handleRemovedTreatment(removedTreatments, isDelta);
                                }
                                if (updatedTreatments.length() > 0) {
                                    BroadcastTreatment.handleChangedTreatment(updatedTreatments, isDelta);
                                }
                                if (addedTreatments.length() > 0) {
                                    BroadcastTreatment.handleNewTreatment(addedTreatments, isDelta);
                                }
                            }
                            if (data.has("devicestatus")) {
                                JSONArray devicestatuses = data.getJSONArray("devicestatus");
                                if (devicestatuses.length() > 0) {
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + devicestatuses.length() + " devicestatuses"));
                                    for (Integer index = 0; index < devicestatuses.length(); index++) {
                                        JSONObject jsonStatus = devicestatuses.getJSONObject(index);
                                        // remove from upload queue if Ack is failing
                                        UploadQueue.removeID(jsonStatus);
                                    }
                                    BroadcastDeviceStatus.handleNewDeviceStatus(devicestatuses, MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("food")) {
                                JSONArray foods = data.getJSONArray("food");
                                JSONArray removedFoods = new JSONArray();
                                JSONArray updatedFoods = new JSONArray();
                                JSONArray addedFoods = new JSONArray();
                                if (foods.length() > 0)
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + foods.length() + " foods"));
                                for (Integer index = 0; index < foods.length(); index++) {
                                    JSONObject jsonFood = foods.getJSONObject(index);

                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonFood);

                                    String action = JsonHelper.safeGetString(jsonFood, "action");

                                    if (action == null) {
                                        addedFoods.put(jsonFood);
                                    } else if (action.equals("update")) {
                                        updatedFoods.put(jsonFood);
                                    } else if (action.equals("remove")) {
                                        removedFoods.put(jsonFood);
                                    }
                                }
                                if (removedFoods.length() > 0) {
                                    BroadcastFood.handleRemovedFood(removedFoods, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (updatedFoods.length() > 0) {
                                    BroadcastFood.handleChangedFood(updatedFoods, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (addedFoods.length() > 0) {
                                    BroadcastFood.handleNewFood(addedFoods, MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("mbgs")) {
                                JSONArray mbgs = data.getJSONArray("mbgs");
                                if (mbgs.length() > 0)
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + mbgs.length() + " mbgs"));
                                for (Integer index = 0; index < mbgs.length(); index++) {
                                    JSONObject jsonMbg = mbgs.getJSONObject(index);
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonMbg);
                                }
                                BroadcastMbgs.handleNewMbg(mbgs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("cals")) {
                                JSONArray cals = data.getJSONArray("cals");
                                if (cals.length() > 0)
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + cals.length() + " cals"));
                                // Retreive actual calibration
                                for (Integer index = 0; index < cals.length(); index++) {
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(cals.optJSONObject(index));
                                }
                                BroadcastCals.handleNewCal(cals, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("sgvs")) {
                                JSONArray sgvs = data.getJSONArray("sgvs");
                                if (sgvs.length() > 0)
                                    RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "received " + sgvs.length() + " sgvs"));
                                for (Integer index = 0; index < sgvs.length(); index++) {
                                    JSONObject jsonSgv = sgvs.getJSONObject(index);
                                    // RxBus.INSTANCE.send(new EventNSClientNewLog("DATA", "svg " + sgvs.getJSONObject(index).toString());
                                    NSSgv sgv = new NSSgv(jsonSgv);
                                    // Handle new sgv here
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonSgv);
                                    //Find latest date in sgv
                                    if (sgv.getMills() != null && sgv.getMills() < System.currentTimeMillis())
                                        if (sgv.getMills() > latestDateInReceivedData)
                                            latestDateInReceivedData = sgv.getMills();
                                }
                                // Was that sgv more less 15 mins ago ?
                                boolean lessThan15MinAgo = false;
                                if ((System.currentTimeMillis() - latestDateInReceivedData) / (60 * 1000L) < 15L)
                                    lessThan15MinAgo = true;
                                if (Notification.isAlarmForStaleData() && lessThan15MinAgo) {
                                    RxBus.INSTANCE.send(new EventDismissNotification(Notification.NSALARM));
                                }
                                BroadcastSgvs.handleNewSgv(sgvs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            RxBus.INSTANCE.send(new EventNSClientNewLog("LAST", DateUtil.dateAndTimeString(latestDateInReceivedData)));
                        } catch (JSONException e) {
                            log.error("Unhandled exception", e);
                        }
                        //RxBus.INSTANCE.send(new EventNSClientNewLog("NSCLIENT", "onDataUpdate end");
                    } finally {
                        if (wakeLock.isHeld()) wakeLock.release();
                    }
                }

            });
        }
    };

    public void dbUpdate(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", new JSONObject(dbr.data));
            mSocket.emit("dbUpdate", message, ack);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBUPDATE " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void dbUpdateUnset(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            message.put("data", new JSONObject(dbr.data));
            mSocket.emit("dbUpdateUnset", message, ack);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBUPDATEUNSET " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void dbRemove(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            mSocket.emit("dbRemove", message, ack);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBREMOVE " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void dbAdd(DbRequest dbr, NSAddAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("data", new JSONObject(dbr.data));
            mSocket.emit("dbAdd", message, ack);
            RxBus.INSTANCE.send(new EventNSClientNewLog("DBADD " + dbr.collection, "Sent " + dbr.nsClientID));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void sendAlarmAck(AlarmAck alarmAck) {
        if (!isConnected || !hasWriteAuth) return;
        mSocket.emit("ack", alarmAck.level, alarmAck.group, alarmAck.silenceTime);
        RxBus.INSTANCE.send(new EventNSClientNewLog("ALARMACK ", alarmAck.level + " " + alarmAck.group + " " + alarmAck.silenceTime));
    }

    public void resend(final String reason) {
        if (UploadQueue.size() == 0)
            return;

        if (!isConnected || !hasWriteAuth) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mSocket == null || !mSocket.connected()) return;

                if (lastResendTime > System.currentTimeMillis() - 10 * 1000L) {
                    if (L.isEnabled(L.NSCLIENT))
                        log.debug("Skipping resend by lastResendTime: " + ((System.currentTimeMillis() - lastResendTime) / 1000L) + " sec");
                    return;
                }
                lastResendTime = System.currentTimeMillis();

                RxBus.INSTANCE.send(new EventNSClientNewLog("QUEUE", "Resend started: " + reason));

                CloseableIterator<DbRequest> iterator = null;
                int maxcount = 30;
                try {
                    iterator = MainApp.getDbHelper().getDbRequestInterator();
                    try {
                        while (iterator.hasNext() && maxcount > 0) {
                            DbRequest dbr = iterator.next();
                            if (dbr.action.equals("dbAdd")) {
                                NSAddAck addAck = new NSAddAck();
                                dbAdd(dbr, addAck);
                            } else if (dbr.action.equals("dbRemove")) {
                                NSUpdateAck removeAck = new NSUpdateAck(dbr.action, dbr._id);
                                dbRemove(dbr, removeAck);
                            } else if (dbr.action.equals("dbUpdate")) {
                                NSUpdateAck updateAck = new NSUpdateAck(dbr.action, dbr._id);
                                dbUpdate(dbr, updateAck);
                            } else if (dbr.action.equals("dbUpdateUnset")) {
                                NSUpdateAck updateUnsetAck = new NSUpdateAck(dbr.action, dbr._id);
                                dbUpdateUnset(dbr, updateUnsetAck);
                            }
                            maxcount--;
                        }
                    } finally {
                        iterator.close();
                    }
                } catch (SQLException e) {
                    log.error("Unhandled exception", e);
                }

                RxBus.INSTANCE.send(new EventNSClientNewLog("QUEUE", "Resend ended: " + reason));
            }
        });
    }

    public void restart() {
        destroy();
        initialize();
    }
}
