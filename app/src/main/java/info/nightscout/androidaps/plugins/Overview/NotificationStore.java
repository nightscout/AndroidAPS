package info.nightscout.androidaps.plugins.Overview;

import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.AlarmSoundService;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;


/**
 * Created by mike on 03.12.2016.
 */

public class NotificationStore {
    private static Logger log = LoggerFactory.getLogger(NotificationStore.class);
    public List<Notification> store = new ArrayList<Notification>();

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
        if (n.soundId != null) {
            Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
            alarm.putExtra("soundid", n.soundId);
            MainApp.instance().startService(alarm);
        }
        store.add(n);

        WearPlugin wearPlugin = (WearPlugin) MainApp.getSpecificPlugin(WearPlugin.class);
        if(wearPlugin!= null && wearPlugin.isEnabled()) {
            wearPlugin.overviewNotification(n.id, "OverviewNotification:\n" + n.text);
        }

        Collections.sort(store, new NotificationComparator());
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
}
