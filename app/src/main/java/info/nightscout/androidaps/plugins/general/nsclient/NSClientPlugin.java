package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.Html;
import android.text.Spanned;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

public class NSClientPlugin extends PluginBase {
    private Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    static NSClientPlugin nsClientPlugin;

    static public NSClientPlugin getPlugin() {
        if (nsClientPlugin == null) {
            nsClientPlugin = new NSClientPlugin();
        }
        return nsClientPlugin;
    }

    public Handler handler;

    private final List<EventNSClientNewLog> listLog = new ArrayList<>();
    Spanned textLog = Html.fromHtml("");

    public boolean paused;
    boolean autoscroll;

    public String status = "";

    public NSClientService nsClientService = null;

    private NsClientReceiverDelegate nsClientReceiverDelegate;

    private NSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(NSClientFragment.class.getName())
                .pluginName(R.string.nsclientinternal)
                .shortName(R.string.nsclientinternal_shortname)
                .preferencesId(R.xml.pref_nsclientinternal)
                .description(R.string.description_ns_client)
        );

        if (Config.NSCLIENT) {
            pluginDescription.alwaysEnabled(true).visibleByDefault(true);
        }
        paused = SP.getBoolean(R.string.key_nsclientinternal_paused, false);
        autoscroll = SP.getBoolean(R.string.key_nsclientinternal_autoscroll, true);

        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSClientPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        nsClientReceiverDelegate =
                new NsClientReceiverDelegate(MainApp.instance().getApplicationContext(), MainApp.bus());
    }

    public boolean isAllowed() {
        return nsClientReceiverDelegate.allowed;
    }


    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, NSClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        super.onStart();

        nsClientReceiverDelegate.registerReceivers();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);

        nsClientReceiverDelegate.unregisterReceivers();
    }

    @Subscribe
    public void onStatusEvent(EventPreferenceChange ev) {
        nsClientReceiverDelegate.onStatusEvent(ev);
    }

    @Subscribe
    public void onStatusEvent(final EventChargingState ev) {
        nsClientReceiverDelegate.onStatusEvent(ev);
    }

    @Subscribe
    public void onStatusEvent(final EventNetworkChange ev) {
        nsClientReceiverDelegate.onStatusEvent(ev);
    }


    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("Service is disconnected");
            nsClientService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("Service is connected");
            NSClientService.LocalBinder mLocalBinder = (NSClientService.LocalBinder) service;
            if (mLocalBinder != null) // is null when running in roboelectric
                nsClientService = mLocalBinder.getServiceInstance();
        }
    };

    @Subscribe
    public void onStatusEvent(final EventAppExit ignored) {
        if (nsClientService != null) {
            MainApp.instance().getApplicationContext().unbindService(mConnection);
            nsClientReceiverDelegate.unregisterReceivers();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientNewLog ev) {
        addToLog(ev);
        if (L.isEnabled(L.NSCLIENT))
            log.debug(ev.action + " " + ev.logText);
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientStatus ev) {
        status = ev.status;
        MainApp.bus().post(new EventNSClientUpdateGUI());
    }

    synchronized void clearLog() {
        handler.post(() -> {
            synchronized (listLog) {
                listLog.clear();
            }
            MainApp.bus().post(new EventNSClientUpdateGUI());
        });
    }

    private synchronized void addToLog(final EventNSClientNewLog ev) {
        handler.post(() -> {
            synchronized (listLog) {
                listLog.add(ev);
                // remove the first line if log is too large
                if (listLog.size() >= Constants.MAX_LOG_LINES) {
                    listLog.remove(0);
                }
            }
            MainApp.bus().post(new EventNSClientUpdateGUI());
        });
    }

    synchronized void updateLog() {
        try {
            StringBuilder newTextLog = new StringBuilder();
            synchronized (listLog) {
                for (EventNSClientNewLog log : listLog) {
                    newTextLog.append(log.toPreparedHtml());
                }
            }
            textLog = Html.fromHtml(newTextLog.toString());
        } catch (OutOfMemoryError e) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Out of memory!\nStop using this phone !!!", R.raw.error);
        }
    }

    void resend(String reason) {
        if (nsClientService != null)
            nsClientService.resend(reason);
    }

    public void pause(boolean newState) {
        SP.putBoolean(R.string.key_nsclientinternal_paused, newState);
        paused = newState;
        MainApp.bus().post(new EventPreferenceChange(R.string.key_nsclientinternal_paused));
    }

    public UploadQueue queue() {
        return NSClientService.uploadQueue;
    }

    public String url() {
        return NSClientService.nsURL;
    }

    public boolean hasWritePermission() {
        return nsClientService.hasWriteAuth;
    }
}
