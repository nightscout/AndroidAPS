package info.nightscout.androidaps.plugins.general.automation.events;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class EventAutomationUpdateTrigger extends Event {
    private Trigger trigger;

    public EventAutomationUpdateTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public Trigger getTrigger() {
        return trigger;
    }
}
