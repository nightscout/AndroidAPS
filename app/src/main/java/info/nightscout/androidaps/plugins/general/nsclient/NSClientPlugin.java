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

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.jetbrains.annotations.NotNull;
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
import info.nightscout.androidaps.logging.AAPSLoggerProduction;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.data.AlarmAck;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class NSClientPlugin extends PluginBase {
    private Logger log = LoggerFactory.getLogger(L.NSCLIENT);
    private CompositeDisposable disposable = new CompositeDisposable();

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

    // TODO: dagger

    private NSClientPlugin() {
        super(new PluginDescription()
                        .mainType(PluginType.GENERAL)
                        .fragmentClass(NSClientFragment.class.getName())
                        .pluginName(R.string.nsclientinternal)
                        .shortName(R.string.nsclientinternal_shortname)
                        .preferencesId(R.xml.pref_nsclientinternal)
                        .description(R.string.description_ns_client),
                new RxBusWrapper(), new AAPSLoggerProduction() // TODO: dagger
        );

        if (Config.NSCLIENT) {
            getPluginDescription().alwaysEnabled(true).visibleByDefault(true);
        }
        paused = SP.getBoolean(R.string.key_nsclientinternal_paused, false);
        autoscroll = SP.getBoolean(R.string.key_nsclientinternal_autoscroll, true);

        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSClientPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        nsClientReceiverDelegate =
                new NsClientReceiverDelegate();
    }

    public boolean isAllowed() {
        return nsClientReceiverDelegate.allowed;
    }


    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, NSClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        super.onStart();

        nsClientReceiverDelegate.grabReceiversState();
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventNSClientStatus.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    status = event.getStatus(MainApp.instance().getResourceHelper());
                    RxBus.Companion.getINSTANCE().send(new EventNSClientUpdateGUI());
                }, exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventNetworkChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (nsClientService != null) {
                        MainApp.instance().getApplicationContext().unbindService(mConnection);
                    }
                }, exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventNSClientNewLog.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    addToLog(event);
                    if (L.isEnabled(L.NSCLIENT))
                        log.debug(event.getAction() + " " + event.getLogText());
                }, exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(RxBus.Companion.getINSTANCE()
                .toObservable(EventChargingState.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), exception -> FabricPrivacy.getInstance().logException(exception))
        );
    }

    @Override
    protected void onStop() {
        MainApp.instance().getApplicationContext().unbindService(mConnection);
        disposable.clear();
        super.onStop();
    }

    @Override
    public void preprocessPreferences(@NotNull PreferenceFragmentCompat preferenceFragment) {
        super.preprocessPreferences(preferenceFragment);

        if (Config.NSCLIENT) {
            PreferenceScreen scrnAdvancedSettings = (PreferenceScreen) preferenceFragment.findPreference(MainApp.gs(R.string.key_advancedsettings));
            if (scrnAdvancedSettings != null) {
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_statuslights_res_warning)));
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_statuslights_res_critical)));
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_statuslights_bat_warning)));
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_statuslights_bat_critical)));
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_show_statuslights)));
                scrnAdvancedSettings.removePreference(preferenceFragment.findPreference(MainApp.gs(R.string.key_show_statuslights_extended)));
            }
        }
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

    synchronized void clearLog() {
        handler.post(() -> {
            synchronized (listLog) {
                listLog.clear();
            }
            RxBus.Companion.getINSTANCE().send(new EventNSClientUpdateGUI());
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
            RxBus.Companion.getINSTANCE().send(new EventNSClientUpdateGUI());
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
        RxBus.Companion.getINSTANCE().send(new EventPreferenceChange(MainApp.resources(), R.string.key_nsclientinternal_paused));
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

    public void handleClearAlarm(NSAlarm originalAlarm, long silenceTimeInMsec) {

        if (!isEnabled(PluginType.GENERAL)) {
            return;
        }
        if (SP.getBoolean(R.string.key_ns_noupload, false)) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("Upload disabled. Message dropped");
            return;
        }

        AlarmAck ack = new AlarmAck();
        ack.level = originalAlarm.level();
        ack.group = originalAlarm.group();
        ack.silenceTime = silenceTimeInMsec;

        if (nsClientService != null)
            nsClientService.sendAlarmAck(ack);
    }

}
