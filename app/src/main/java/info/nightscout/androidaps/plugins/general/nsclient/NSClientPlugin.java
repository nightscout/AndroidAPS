package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.Spanned;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.data.AlarmAck;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientResend;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.HtmlHelper;
import info.nightscout.androidaps.utils.ToastUtils;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class NSClientPlugin extends PluginBase {
    private final CompositeDisposable disposable = new CompositeDisposable();

    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final Context context;
    private final FabricPrivacy fabricPrivacy;
    private final SP sp;
    private final Config config;
    private final BuildHelper buildHelper;

    public Handler handler;

    private final List<EventNSClientNewLog> listLog = new ArrayList<>();
    Spanned textLog = HtmlHelper.INSTANCE.fromHtml("");

    public boolean paused;
    boolean autoscroll;

    public String status = "";

    public NSClientService nsClientService = null;

    private final NsClientReceiverDelegate nsClientReceiverDelegate;

    @Inject
    public NSClientPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            Context context,
            FabricPrivacy fabricPrivacy,
            SP sp,
            NsClientReceiverDelegate nsClientReceiverDelegate,
            Config config,
            BuildHelper buildHelper
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.GENERAL)
                        .fragmentClass(NSClientFragment.class.getName())
                        .pluginIcon(R.drawable.ic_nightscout_syncs)
                        .pluginName(R.string.nsclientinternal)
                        .shortName(R.string.nsclientinternal_shortname)
                        .preferencesId(R.xml.pref_nsclientinternal)
                        .description(R.string.description_ns_client),
                aapsLogger, resourceHelper, injector
        );

        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.sp = sp;
        this.nsClientReceiverDelegate = nsClientReceiverDelegate;
        this.config = config;
        this.buildHelper = buildHelper;

        if (config.getNSCLIENT()) {
            getPluginDescription().alwaysEnabled(true).visibleByDefault(true);
        }
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSClientPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

    }

    public boolean isAllowed() {
        return nsClientReceiverDelegate.allowed;
    }


    @Override
    protected void onStart() {
        paused = sp.getBoolean(R.string.key_nsclientinternal_paused, false);
        autoscroll = sp.getBoolean(R.string.key_nsclientinternal_autoscroll, true);

        Intent intent = new Intent(context, NSClientService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        super.onStart();

        nsClientReceiverDelegate.grabReceiversState();
        disposable.add(rxBus
                .toObservable(EventNSClientStatus.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    status = event.getStatus(resourceHelper);
                    rxBus.send(new EventNSClientUpdateGUI());
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventNetworkChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (nsClientService != null) {
                        context.unbindService(mConnection);
                    }
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventNSClientNewLog.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    addToLog(event);
                    aapsLogger.debug(LTag.NSCLIENT, event.getAction() + " " + event.getLogText());
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventChargingState.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> nsClientReceiverDelegate.onStatusEvent(event), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventNSClientResend.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> resend(event.getReason()), fabricPrivacy::logException)
        );
    }

    @Override
    protected void onStop() {
        context.getApplicationContext().unbindService(mConnection);
        disposable.clear();
        super.onStop();
    }

    @Override
    public void preprocessPreferences(@NotNull PreferenceFragmentCompat preferenceFragment) {
        super.preprocessPreferences(preferenceFragment);

        if (config.getNSCLIENT()) {
            SwitchPreference key_ns_uploadlocalprofile = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_uploadlocalprofile));
            if (key_ns_uploadlocalprofile != null) key_ns_uploadlocalprofile.setVisible(false);
            SwitchPreference key_ns_autobackfill = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_autobackfill));
            if (key_ns_autobackfill != null) key_ns_autobackfill.setVisible(false);
            SwitchPreference key_ns_create_announcements_from_errors = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_create_announcements_from_errors));
            if (key_ns_create_announcements_from_errors != null)
                key_ns_create_announcements_from_errors.setVisible(false);
            SwitchPreference key_ns_create_announcements_from_carbs_req = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_create_announcements_from_carbs_req));
            if (key_ns_create_announcements_from_carbs_req != null)
                key_ns_create_announcements_from_carbs_req.setVisible(false);
            SwitchPreference key_ns_upload_only = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_upload_only));
            if (key_ns_upload_only != null) {
                key_ns_upload_only.setVisible(false);
                key_ns_upload_only.setEnabled(false);
            }
            SwitchPreference key_ns_sync_use_absolute = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_sync_use_absolute));
            if (key_ns_sync_use_absolute != null) key_ns_sync_use_absolute.setVisible(false);
        } else {
            // APS or pumpcontrol mode
            SwitchPreference key_ns_upload_only = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_ns_upload_only));
            if (key_ns_upload_only != null)
                key_ns_upload_only.setVisible(buildHelper.isEngineeringMode());
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is disconnected");
            nsClientService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected");
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
            rxBus.send(new EventNSClientUpdateGUI());
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
            rxBus.send(new EventNSClientUpdateGUI());
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
            textLog = HtmlHelper.INSTANCE.fromHtml(newTextLog.toString());
        } catch (OutOfMemoryError e) {
            ToastUtils.showToastInUiThread(context, rxBus, "Out of memory!\nStop using this phone !!!", R.raw.error);
        }
    }

    void resend(String reason) {
        if (nsClientService != null)
            nsClientService.resend(reason);
    }

    public void pause(boolean newState) {
        sp.putBoolean(R.string.key_nsclientinternal_paused, newState);
        paused = newState;
        rxBus.send(new EventPreferenceChange(resourceHelper, R.string.key_nsclientinternal_paused));
    }

    public String url() {
        return NSClientService.nsURL;
    }

    public boolean hasWritePermission() {
        return NSClientService.hasWriteAuth;
    }

    public void handleClearAlarm(NSAlarm originalAlarm, long silenceTimeInMsec) {

        if (!isEnabled(PluginType.GENERAL)) {
            return;
        }
        if (sp.getBoolean(R.string.key_ns_noupload, false)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped");
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
