package info.nightscout.androidaps.plugins.DanaR.Services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPumpConnecting;
import info.nightscout.androidaps.plugins.DanaR.DanaConnection;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

public class DanaRService extends Service {
    private static Logger log = LoggerFactory.getLogger(DanaRService.class);

    Handler mHandler;
    private HandlerThread mHandlerThread;

    private Notification mNotification;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationCompatBuilder;
    private DanaConnection mDanaConnection;

    private static final int notifyId = 130;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Config.logFunctionCalls)
            log.info("onStartCommand");

        if (mHandlerThread == null) {
            enableForeground();
            if (Config.logFunctionCalls)
                log.debug("Creating handler thread");
            this.mHandlerThread = new HandlerThread(DanaRService.class.getSimpleName() + "Handler");
            mHandlerThread.start();

            this.mHandler = new Handler(mHandlerThread.getLooper());

            DanaRFragment danaRFragment = (DanaRFragment) MainActivity.getSpecificPlugin(DanaRFragment.class);
            mDanaConnection = danaRFragment.getDanaConnection();

            registerBus();
            if (mDanaConnection == null) {
                mDanaConnection = new DanaConnection(MainApp.bus());
                danaRFragment.setDanaConnection(mDanaConnection);
            }
        }

        if (Config.DANAR)
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDanaConnection.connectIfNotConnected("onStartCommand connectionCheck");
                }
            });

        if (Config.logFunctionCalls)
            log.info("onStartCommand end");
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

    private void enableForeground() {
        mNotificationCompatBuilder = new NotificationCompat.Builder(getApplicationContext());
        mNotificationCompatBuilder.setContentTitle(MainApp.sResources.getString(R.string.app_name))
                .setSmallIcon(R.drawable.notification_icon)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setLocalOnly(true);

        mNotification = mNotificationCompatBuilder.build();
        notifyManagerNotify();
        startForeground(notifyId, mNotification);
    }

    private void notifyManagerNotify() {
        mNotificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(notifyId, mNotification);
    }

    @Subscribe
    public void onStatusEvent(final EventPumpConnecting c) {
        if (Config.DANAR) {
            String connectionText;
            if (c.sConnecting) {
                connectionText = MainApp.sResources.getString(R.string.connecting);
            } else {
                if (c.sConnected) {
                    connectionText = MainApp.sResources.getString(R.string.connected);
                } else {
                    connectionText = MainApp.sResources.getString(R.string.disconnected);
                }
            }

            mNotificationCompatBuilder.setWhen(System.currentTimeMillis())
                    .setContentText(connectionText);

            mNotification = mNotificationCompatBuilder.build();
            notifyManagerNotify();
        }
    }

    @Subscribe
    public void onStopEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("onStopEvent received");
        mDanaConnection.stop();

        stopForeground(true);
        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("onStopEvent finished");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (Config.logFunctionCalls)
            log.info("onCreate");
        mHandler = new Handler(); // TODO: not needed???
    }
}
