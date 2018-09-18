package info.nightscout.androidaps.plugins.general.automation.actions;

import java.util.ArrayList;
import java.util.List;

public class TriggerAnd extends Trigger {

    private List<Trigger> list = new ArrayList<>();

    @Override
    synchronized boolean shouldRun() {
        boolean result = true;

        for (Trigger t : list) {
            result = result && t.shouldRun();
        }
        return result;
    }

    synchronized void add(Trigger t) {
        list.add(t);
    }

    synchronized boolean remove(Trigger t) {
        return list.remove(t);
    }

    int size() {
        return list.size();
    }

    Trigger get(int i) {
        return list.get(i);
    }
}
