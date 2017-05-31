package info.nightscout.androidaps.plugins.NSClientInternal.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.j256.ormlite.dao.CloseableIterator;
import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.androidaps.plugins.NSClientInternal.acks.NSAddAck;
import info.nightscout.androidaps.plugins.NSClientInternal.acks.NSAuthAck;
import info.nightscout.androidaps.plugins.NSClientInternal.acks.NSUpdateAck;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastCals;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastDeviceStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastMbgs;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastProfile;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastSgvs;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastTreatment;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSCal;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSTreatment;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.Services.ExecutionService;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class NSClientService extends Service {
    private static Logger log = LoggerFactory.getLogger(ExecutionService.class);

    static public PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new NSClientService.LocalBinder();

    static NSProfile nsProfile;

    static public Handler handler;
    static private HandlerThread handlerThread;

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
    private Integer nsHours = 24;

    public long lastResendTime = 0;

    public long latestDateInReceivedData = 0;

    private String nsAPIhashCode = "";

    public static UploadQueue uploadQueue = new UploadQueue();

    public NSClientService() {
        registerBus();
        if (handler == null) {
            handlerThread = new HandlerThread(NSClientService.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NSClientService");
        initialize();
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

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("EventAppExit received");

        destroy();

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    @Subscribe
    public void onStatusEvent(EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_nsclientinternal_url) ||
                ev.isChanged(R.string.key_nsclientinternal_api_secret) ||
                ev.isChanged(R.string.key_nsclientinternal_paused)
                ) {
            destroy();
            initialize();
        }
    }

    @Subscribe
    public void onStatusEvent(EventConfigBuilderChange ev) {
        if (nsEnabled != MainApp.getSpecificPlugin(NSClientInternalPlugin.class).isEnabled(PluginBase.GENERAL)) {
            destroy();
            initialize();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientRestart ev) {
        latestDateInReceivedData = 0;
        restart();
    }

    public static void setNsProfile(NSProfile profile) {
        nsProfile = profile;
    }

    public static NSProfile getNsProfile() {
        return nsProfile;
    }

    public void initialize() {
        dataCounter = 0;

        NSClientService.mWakeLock.acquire();

        readPreferences();

        if (!nsAPISecret.equals(""))
            nsAPIhashCode = Hashing.sha1().hashString(nsAPISecret, Charsets.UTF_8).toString();

        MainApp.bus().post(new EventNSClientStatus("Initializing"));
        if (((NSClientInternalPlugin)MainApp.getSpecificPlugin(NSClientInternalPlugin.class)).paused) {
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "paused"));
            MainApp.bus().post(new EventNSClientStatus("Paused"));
        } else if (!nsEnabled) {
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "disabled"));
            MainApp.bus().post(new EventNSClientStatus("Disabled"));
        } else if (!nsURL.equals("")) {
            try {
                MainApp.bus().post(new EventNSClientStatus("Connecting ..."));
                IO.Options opt = new IO.Options();
                opt.forceNew = true;
                opt.reconnection = true;
                mSocket = IO.socket(nsURL, opt);
                mSocket.on(Socket.EVENT_CONNECT, onConnect);
                mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                mSocket.on(Socket.EVENT_PING, onPing);
                MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "do connect"));
                mSocket.connect();
                mSocket.on("dataUpdate", onDataUpdate);
            } catch (URISyntaxException | RuntimeException e) {
                MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "Wrong URL syntax"));
                MainApp.bus().post(new EventNSClientStatus("Wrong URL syntax"));
            }
        } else {
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "No NS URL specified"));
            MainApp.bus().post(new EventNSClientStatus("Not configured"));
        }
        NSClientService.mWakeLock.release();
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            connectCounter++;
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "connect #" + connectCounter + " event. ID: " + mSocket.id()));
            sendAuthMessage(new NSAuthAck());
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "disconnect event"));
        }
    };

    public void destroy() {
        if (mSocket != null) {
            MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "destroy"));
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
            e.printStackTrace();
            return;
        }
        MainApp.bus().post(new EventNSClientNewLog("AUTH", "requesting auth"));
        mSocket.emit("authorize", authMessage, ack);
    }

    @Subscribe
    public void onStatusEvent(NSAuthAck ack) {
        String connectionStatus = "Authenticated (";
        if (ack.read) connectionStatus += "R";
        if (ack.write) connectionStatus += "W";
        if (ack.write_treatment) connectionStatus += "T";
        connectionStatus += ')';
        isConnected = true;
        hasWriteAuth = ack.write && ack.write_treatment;
        MainApp.bus().post(new EventNSClientStatus(connectionStatus));
        MainApp.bus().post(new EventNSClientNewLog("AUTH", connectionStatus));
        if (!ack.write) {
            MainApp.bus().post(new EventNSClientNewLog("ERROR", "Write permission not granted !!!!"));
        }
        if (!ack.write_treatment) {
            MainApp.bus().post(new EventNSClientNewLog("ERROR", "Write treatment permission not granted !!!!"));
        }
        if (!hasWriteAuth) {
            Notification noperm = new Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, MainApp.sResources.getString(R.string.nowritepermission), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(noperm));
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.NSCLIENT_NO_WRITE_PERMISSION));
        }
    }

    public void readPreferences() {
        nsEnabled = MainApp.getSpecificPlugin(NSClientInternalPlugin.class).isEnabled(PluginBase.GENERAL);
        nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        nsAPISecret = SP.getString(R.string.key_nsclientinternal_api_secret, "");
        nsDevice = SP.getString("careportal_enteredby", "");
    }

    private Emitter.Listener onPing = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (Config.detailedLog)
                MainApp.bus().post(new EventNSClientNewLog("PING", "received"));
            // send data if there is something waiting
            resend("Ping received");
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
                            "onDataUpdate");
                    wakeLock.acquire();
                    try {
                        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

                        JSONObject data = (JSONObject) args[0];
                        NSCal actualCal = new NSCal();
                        boolean broadcastProfile = false;
                        try {
                            // delta means only increment/changes are comming
                            boolean isDelta = data.has("delta");
                            boolean isFull = !isDelta;
                            MainApp.bus().post(new EventNSClientNewLog("DATA", "Data packet #" + dataCounter++ + (isDelta ? " delta" : " full")));

                            if (data.has("profiles")) {
                                JSONArray profiles = (JSONArray) data.getJSONArray("profiles");
                                if (profiles.length() > 0) {
                                    JSONObject profile = (JSONObject) profiles.get(profiles.length() - 1);
                                    String activeProfile = NSClientService.getNsProfile() == null ? null : NSClientService.getNsProfile().getActiveProfile();
                                    NSProfile nsProfile = new NSProfile(profile, activeProfile);
                                    NSClientService.setNsProfile(nsProfile);
                                    broadcastProfile = true;
                                    MainApp.bus().post(new EventNSClientNewLog("PROFILE", "profile received"));
                                }
                            }

                            if (data.has("status")) {
                                JSONObject status = data.getJSONObject("status");
                                NSStatus nsStatus = new NSStatus(status);

                                if (!status.has("versionNum")) {
                                    if (status.getInt("versionNum") < 900) {
                                        MainApp.bus().post(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version !!!!"));
                                    }
                                } else {
                                    nightscoutVersionName = status.getString("version");
                                    nightscoutVersionCode = status.getInt("versionNum");
                                }

                                BroadcastStatus bs = new BroadcastStatus();
                                bs.handleNewStatus(nsStatus, MainApp.instance().getApplicationContext(), isDelta);

                                if (NSClientService.getNsProfile() != null) {
                                    String oldActiveProfile = NSClientService.getNsProfile().getActiveProfile();
                                    String receivedActiveProfile = nsStatus.getActiveProfile();
                                    NSClientService.getNsProfile().setActiveProfile(receivedActiveProfile);
                                    if (receivedActiveProfile != null) {
                                        MainApp.bus().post(new EventNSClientNewLog("PROFILE", "status activeProfile received: " + receivedActiveProfile));
                                    }
                                    // Change possible nulls to ""
                                    String oldP = oldActiveProfile == null ? "" : oldActiveProfile;
                                    String newP = receivedActiveProfile == null ? "" : receivedActiveProfile;
                                    if (!newP.equals(oldP)) {
                                        broadcastProfile = true;
                                    }
                                }
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
                                MainApp.bus().post(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version !!!!"));
                            }

                            // If new profile received or change detected broadcast it
                            if (broadcastProfile && nsProfile != null) {
                                BroadcastProfile bp = new BroadcastProfile();
                                bp.handleNewTreatment(nsProfile, MainApp.instance().getApplicationContext(), isDelta);
                                MainApp.bus().post(new EventNSClientNewLog("PROFILE", "broadcasting"));
                            }

                            if (data.has("treatments")) {
                                JSONArray treatments = (JSONArray) data.getJSONArray("treatments");
                                JSONArray removedTreatments = new JSONArray();
                                JSONArray updatedTreatments = new JSONArray();
                                JSONArray addedTreatments = new JSONArray();
                                BroadcastTreatment bt = new BroadcastTreatment();
                                if (treatments.length() > 0)
                                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + treatments.length() + " treatments"));
                                for (Integer index = 0; index < treatments.length(); index++) {
                                    JSONObject jsonTreatment = treatments.getJSONObject(index);
                                    NSTreatment treatment = new NSTreatment(jsonTreatment);

                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonTreatment);
                                    //Find latest date in treatment
                                    if (treatment.getMills() != null && treatment.getMills() < new Date().getTime())
                                        if (treatment.getMills() > latestDateInReceivedData)
                                            latestDateInReceivedData = treatment.getMills();

                                    if (treatment.getAction() == null) {
                                        if (!isCurrent(treatment)) continue;
                                        addedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("update")) {
                                        if (!isCurrent(treatment)) continue;
                                        updatedTreatments.put(jsonTreatment);
                                    } else if (treatment.getAction().equals("remove")) {
                                        removedTreatments.put(jsonTreatment);
                                    }
                                }
                                if (removedTreatments.length() > 0) {
                                    bt.handleRemovedTreatment(removedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (updatedTreatments.length() > 0) {
                                    bt.handleChangedTreatment(updatedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                                if (addedTreatments.length() > 0) {
                                    bt.handleNewTreatment(addedTreatments, MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("devicestatus")) {
                                BroadcastDeviceStatus bds = new BroadcastDeviceStatus();
                                JSONArray devicestatuses = (JSONArray) data.getJSONArray("devicestatus");
                                if (devicestatuses.length() > 0) {
                                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + devicestatuses.length() + " devicestatuses"));
                                    for (Integer index = 0; index < devicestatuses.length(); index++) {
                                        JSONObject jsonStatus = devicestatuses.getJSONObject(index);
                                        // remove from upload queue if Ack is failing
                                        UploadQueue.removeID(jsonStatus);
                                    }
                                    // send only last record
                                    bds.handleNewDeviceStatus(devicestatuses.getJSONObject(devicestatuses.length() - 1), MainApp.instance().getApplicationContext(), isDelta);
                                }
                            }
                            if (data.has("mbgs")) {
                                BroadcastMbgs bmbg = new BroadcastMbgs();
                                JSONArray mbgs = (JSONArray) data.getJSONArray("mbgs");
                                if (mbgs.length() > 0)
                                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + mbgs.length() + " mbgs"));
                                for (Integer index = 0; index < mbgs.length(); index++) {
                                    JSONObject jsonMbg = mbgs.getJSONObject(index);
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonMbg);
                                }
                                bmbg.handleNewMbg(mbgs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("cals")) {
                                BroadcastCals bc = new BroadcastCals();
                                JSONArray cals = (JSONArray) data.getJSONArray("cals");
                                if (cals.length() > 0)
                                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + cals.length() + " cals"));
                                // Retreive actual calibration
                                for (Integer index = 0; index < cals.length(); index++) {
                                    if (index == 0) {
                                        actualCal.set(cals.optJSONObject(index));
                                    }
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(cals.optJSONObject(index));
                                }
                                bc.handleNewCal(cals, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            if (data.has("sgvs")) {
                                BroadcastSgvs bs = new BroadcastSgvs();
                                String units = nsProfile != null ? nsProfile.getUnits() : "mg/dl";
                                JSONArray sgvs = (JSONArray) data.getJSONArray("sgvs");
                                if (sgvs.length() > 0)
                                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + sgvs.length() + " sgvs"));
                                for (Integer index = 0; index < sgvs.length(); index++) {
                                    JSONObject jsonSgv = sgvs.getJSONObject(index);
                                    // MainApp.bus().post(new EventNSClientNewLog("DATA", "svg " + sgvs.getJSONObject(index).toString());
                                    NSSgv sgv = new NSSgv(jsonSgv);
                                    // Handle new sgv here
                                    // remove from upload queue if Ack is failing
                                    UploadQueue.removeID(jsonSgv);
                                    //Find latest date in sgv
                                    if (sgv.getMills() != null && sgv.getMills() < new Date().getTime())
                                        if (sgv.getMills() > latestDateInReceivedData)
                                            latestDateInReceivedData = sgv.getMills();
                                }
                                bs.handleNewSgv(sgvs, MainApp.instance().getApplicationContext(), isDelta);
                            }
                            MainApp.bus().post(new EventNSClientNewLog("LAST", DateUtil.dateAndTimeString(latestDateInReceivedData)));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        //MainApp.bus().post(new EventNSClientNewLog("NSCLIENT", "onDataUpdate end");
                    } finally {
                        wakeLock.release();
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
            MainApp.bus().post(new EventNSClientNewLog("DBUPDATE " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            e.printStackTrace();
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
            MainApp.bus().post(new EventNSClientNewLog("DBUPDATEUNSET " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void dbRemove(DbRequest dbr, NSUpdateAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("_id", dbr._id);
            mSocket.emit("dbRemove", message, ack);
            MainApp.bus().post(new EventNSClientNewLog("DBREMOVE " + dbr.collection, "Sent " + dbr._id));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onStatusEvent(NSUpdateAck ack) {
        if (ack.result) {
            uploadQueue.removeID(ack.action, ack._id);
            MainApp.bus().post(new EventNSClientNewLog("DBUPDATE/DBREMOVE", "Acked " + ack._id));
        } else {
            MainApp.bus().post(new EventNSClientNewLog("ERROR", "DBUPDATE/DBREMOVE Unknown response"));
        }
    }

    public void dbAdd(DbRequest dbr, NSAddAck ack) {
        try {
            if (!isConnected || !hasWriteAuth) return;
            JSONObject message = new JSONObject();
            message.put("collection", dbr.collection);
            message.put("data", new JSONObject(dbr.data));
            mSocket.emit("dbAdd", message, ack);
            MainApp.bus().post(new EventNSClientNewLog("DBADD " + dbr.collection, "Sent " + dbr.nsClientID));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onStatusEvent(NSAddAck ack) {
        if (ack.nsClientID != null) {
            uploadQueue.removeID(ack.json);
            MainApp.bus().post(new EventNSClientNewLog("DBADD", "Acked " + ack.nsClientID));
        } else {
            MainApp.bus().post(new EventNSClientNewLog("ERROR", "DBADD Unknown response"));
        }
    }

    private boolean isCurrent(NSTreatment treatment) {
        long now = (new Date()).getTime();
        long minPast = now - nsHours * 60L * 60 * 1000;
        if (treatment.getMills() == null) {
            log.debug("treatment.getMills() == null " + treatment.getData().toString());
            return false;
        }
        if (treatment.getMills() > minPast) return true;
        return false;
    }

    public void resend(final String reason) {
        if (UploadQueue.size() == 0)
            return;

        if (!isConnected || !hasWriteAuth) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mSocket == null || !mSocket.connected()) return;

                if (lastResendTime  > new Date().getTime() - 10 * 1000L) {
                    log.debug("Skipping resend by lastResendTime: " + ((new Date().getTime() - lastResendTime) / 1000L) + " sec");
                    return;
                }
                lastResendTime = new Date().getTime();

                MainApp.bus().post(new EventNSClientNewLog("QUEUE", "Resend started: " + reason));

                CloseableIterator<DbRequest> iterator = null;
                try {
                    iterator = MainApp.getDbHelper().getDaoDbRequest().closeableIterator();
                    try {
                        while (iterator.hasNext()) {
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
                        }
                    } finally {
                        iterator.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                MainApp.bus().post(new EventNSClientNewLog("QUEUE", "Resend ended: " + reason));
            }
        });
    }

    public void restart() {
        destroy();
        initialize();
    }
}
