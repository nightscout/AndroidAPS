package info.nightscout.androidaps.plugins.general.automation.actions;

import info.nightscout.androidaps.queue.Callback;

public abstract class Action {

    abstract int friendlyName();
    abstract void doAction(Callback callback);
}
