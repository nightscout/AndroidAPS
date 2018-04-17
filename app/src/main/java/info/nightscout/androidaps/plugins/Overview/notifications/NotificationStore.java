package info.nightscout.androidaps.plugins.Overview.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.AlarmSoundService;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;
//Added by Rumen for snooze time 
import info.nightscout.utils.SP;

/**
 * Created by mike on 03.12.2016.
 */

public class NotificationStore {
    private static Logger log = LoggerFactory.getLogger(NotificationStore.class);
    public List<Notification> store = new ArrayList<Notification>();
    public long snoozedUntil = 0L;

    public NotificationStore() {
    }

    public class NotificationComparator implements Comparator<Notification> {
        @Override
        public int compare(Notification o1, Notification o2) {
            return o1.level - o2.level;
        }
    }

    public Notification get(int index) {
        return store.get(index);
    }

    public void add(Notification n) {
        log.info("Notification received: " + n.text);
        for (int i = 0; i < store.size(); i++) {
            if (get(i).id == n.id) {
                get(i).date = n.date;
                get(i).validTo = n.validTo;
                return;
            }
        }
        store.add(n);

        if (SP.getBoolean(MainApp.sResources.getString(R.string.key_raise_notifications_as_android_notifications), false)) {
            raiseSystemNotification(n);
        } else {
            if (n.soundId != null) {
                Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
                alarm.putExtra("soundid", n.soundId);
                MainApp.instance().startService(alarm);
            }
        }

        Collections.sort(store, new NotificationComparator());
    }

    private void raiseSystemNotification(Notification n) {
        Context context = MainApp.instance().getApplicationContext();
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.blueowl);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setLargeIcon(largeIcon)
                        .setContentText(n.text)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setDeleteIntent(DismissNotificationService.deleteIntent(n.id));
        if (n.level == Notification.URGENT) {
            notificationBuilder.setVibrate(new long[]{1000, 1000, 1000, 1000})
                    .setContentTitle(MainApp.sResources.getString(R.string.urgent_alarm))
                    .setSound(sound, AudioAttributes.USAGE_ALARM);
        } else {
            notificationBuilder.setVibrate(new long[]{0, 100, 50, 100, 50})
                    .setContentTitle(MainApp.sResources.getString(R.string.info))
            ;
        }
        mgr.notify(n.id, notificationBuilder.build());
    }

    public boolean remove(int id) {
        for (int i = 0; i < store.size(); i++) {
            if (get(i).id == id) {
                if (get(i).soundId != null) {
                    Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
                    MainApp.instance().stopService(alarm);
                }
                store.remove(i);
                return true;
            }
        }
        return false;
    }

    public void removeExpired() {
        for (int i = 0; i < store.size(); i++) {
            Notification n = get(i);
            if (n.validTo.getTime() != 0 && n.validTo.getTime() < System.currentTimeMillis()) {
                store.remove(i);
                i--;
            }
        }
    }

    public void snoozeTo(long timeToSnooze) {
        log.debug("Snoozing alarm until: " + timeToSnooze);
        SP.putLong("snoozedTo", timeToSnooze);
    }

    public void unSnooze() {
        if (Notification.isAlarmForStaleData()) {
            Notification notification = new Notification(Notification.NSALARM, MainApp.sResources.getString(R.string.nsalarm_staledata), Notification.URGENT);
            SP.putLong("snoozedTo", System.currentTimeMillis());
            add(notification);
            log.debug("Snoozed to current time and added back notification!");
        }
    }
}
