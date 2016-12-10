package info.nightscout.androidaps.plugins.Overview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


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
        store.add(n);
        Collections.sort(store, new NotificationComparator());
    }

    public void remove(int id) {
        for (int i = 0; i < store.size(); i++) {
            if (get(i).id == id) {
                store.remove(i);
                return;
            }
        }
    }

    public void removeExpired() {
        for (int i = 0; i < store.size(); i++) {
            Notification n = get(i);
            if (n.validTo.getTime() != 0 && n.validTo.getTime() < new Date().getTime()) {
                store.remove(i);
                i--;
            }
        }
    }
}
