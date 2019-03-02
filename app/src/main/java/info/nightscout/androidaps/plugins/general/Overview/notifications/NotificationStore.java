package info.nightscout.androidaps.plugins.general.overview.notifications;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.services.AlarmSoundService;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 03.12.2016.
 */

public class NotificationStore {

    private static final String CHANNEL_ID = "AndroidAPS-Overview";

    private static Logger log = LoggerFactory.getLogger(L.NOTIFICATION);
    public List<Notification> store = new ArrayList<>();
    private boolean usesChannels;

    public NotificationStore() {
        createNotificationChannel();
    }

    public class NotificationComparator implements Comparator<Notification> {
        @Override
        public int compare(Notification o1, Notification o2) {
            return o1.level - o2.level;
        }
    }

    public synchronized boolean add(Notification n) {
        if (L.isEnabled(L.NOTIFICATION))
            log.debug("Notification received: " + n.text);
        for (Notification storeNotification : store) {
            if (storeNotification.id == n.id) {
                storeNotification.date = n.date;
                storeNotification.validTo = n.validTo;
                return false;
            }
        }
        store.add(n);

        if (SP.getBoolean(MainApp.gs(R.string.key_raise_notifications_as_android_notifications), false) && !(n instanceof NotificationWithAction)) {
            raiseSystemNotification(n);
            if (usesChannels && n.soundId != null) {
                Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
                alarm.putExtra("soundid", n.soundId);
                MainApp.instance().startService(alarm);
            }

        } else {
            if (n.soundId != null) {
                Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
                alarm.putExtra("soundid", n.soundId);
                MainApp.instance().startService(alarm);
            }
        }

        Collections.sort(store, new NotificationComparator());
        return true;
    }

    public synchronized boolean remove(int id) {
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).id == id) {
                if (store.get(i).soundId != null) {
                    Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
                    MainApp.instance().stopService(alarm);
                }
                store.remove(i);
                return true;
            }
        }
        return false;
    }

    public synchronized void removeExpired() {
        for (int i = 0; i < store.size(); i++) {
            Notification n = store.get(i);
            if (n.validTo.getTime() != 0 && n.validTo.getTime() < System.currentTimeMillis()) {
                store.remove(i);
                i--;
            }
        }
    }

    public void snoozeTo(long timeToSnooze) {
        if (L.isEnabled(L.NOTIFICATION))
            log.debug("Snoozing alarm until: " + timeToSnooze);
        SP.putLong("snoozedTo", timeToSnooze);
    }

    public void unSnooze() {
        if (Notification.isAlarmForStaleData()) {
            Notification notification = new Notification(Notification.NSALARM, MainApp.gs(R.string.nsalarm_staledata), Notification.URGENT);
            SP.putLong("snoozedTo", System.currentTimeMillis());
            add(notification);
            if (L.isEnabled(L.NOTIFICATION))
                log.debug("Snoozed to current time and added back notification!");
        }
    }

    private void raiseSystemNotification(Notification n) {
        Context context = MainApp.instance().getApplicationContext();
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), MainApp.getIcon());
        int smallIcon = MainApp.getNotificationIcon();
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(smallIcon)
                        .setLargeIcon(largeIcon)
                        .setContentText(n.text)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setDeleteIntent(DismissNotificationService.deleteIntent(n.id));
        if (n.level == Notification.URGENT) {
            notificationBuilder.setVibrate(new long[]{1000, 1000, 1000, 1000})
                    .setContentTitle(MainApp.gs(R.string.urgent_alarm))
                    .setSound(sound, AudioAttributes.USAGE_ALARM);
        } else {
            notificationBuilder.setVibrate(new long[]{0, 100, 50, 100, 50})
                    .setContentTitle(MainApp.gs(R.string.info))
            ;
        }
        mgr.notify(n.id, notificationBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            usesChannels = true;
            NotificationManager mNotificationManager =
                    (NotificationManager) MainApp.instance().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

}
