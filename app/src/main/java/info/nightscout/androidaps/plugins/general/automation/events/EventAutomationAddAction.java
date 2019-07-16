package info.nightscout.androidaps.plugins.general.automation.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;

public class EventAutomationAddAction extends Event {
    private Action action;

    public EventAutomationAddAction(Action action) {
        this.action = action;
    }

    public Action getAction() {
        return action;
    }
}
