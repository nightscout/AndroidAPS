package info.nightscout.androidaps.plugins.general.persistentNotification;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.logging.L;

/**
 * Keeps AndroidAPS in foreground state, so it won't be terminated by Android nor get restricted by the background execution limits
 */
public class DummyService extends Service {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startForeground(PersistentNotificationPlugin.ONGOING_NOTIFICATION_ID, PersistentNotificationPlugin.getPlugin().getLastNotification());
        return START_STICKY;
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (L.isEnabled(L.CORE))
            log.debug("EventAppExit received");

        stopSelf();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: I guess this was moved here in order to adhere to the 5 seconds rule to call "startForeground" after a Service was called as Foreground service?
        // As onCreate() is not called every time a service is started, copied to onStartCommand().
        startForeground(PersistentNotificationPlugin.ONGOING_NOTIFICATION_ID, PersistentNotificationPlugin.getPlugin().getLastNotification());
        MainApp.bus().register(this);
    }

    @Override
    public void onDestroy() {
        if (L.isEnabled(L.CORE))
            log.debug("onDestroy");
        MainApp.bus().unregister(this);
        stopForeground(true);
    }
}
