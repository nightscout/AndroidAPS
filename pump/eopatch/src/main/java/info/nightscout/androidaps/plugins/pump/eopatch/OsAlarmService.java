package info.nightscout.androidaps.plugins.pump.eopatch;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.util.Objects;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class OsAlarmService extends Service {

    public static final int FOREGROUND_NOTIFICATION_ID = 34534554;

    private CompositeDisposable compositeDisposable;

    private boolean foreground = false;

    @Override
    public void onCreate() {
        super.onCreate();

        compositeDisposable = new CompositeDisposable();

        startForeground();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();

        String action = null;

        if (action == null) {
            return Service.START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((NotificationManager) Objects.requireNonNull(getSystemService(Context.NOTIFICATION_SERVICE))).cancel(FOREGROUND_NOTIFICATION_ID);

        compositeDisposable.dispose();
    }

    public synchronized void startForeground() {
        if (!foreground) {
            foreground = true;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, OsAlarmService.class);

//        context.startForegroundService(intent);
    }

    public static void notifyNotification(Context context, boolean isNetworkAvailable) {
        notifyNotification(context);
    }

    public static void notifyNotification(Context context) {
//        Notification builder = getNotification(context);
//        ((NotificationManager) Objects.requireNonNull(context.getSystemService(Context.NOTIFICATION_SERVICE))).notify(FOREGROUND_NOTIFICATION_ID, builder);
    }
}