package info.nightscout.androidaps.plugins.Persistentnotification;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Keeps AndroidAPS in foreground state, so it won't be terminated by Android nor get restricted by the background execution limits
 */
public class DummyService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = PersistentNotificationPlugin.getPlugin().updateNotification();
        if (notification != null)
            startForeground(PersistentNotificationPlugin.ONGOING_NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
    }
}
