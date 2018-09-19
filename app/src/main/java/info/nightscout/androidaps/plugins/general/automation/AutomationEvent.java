package info.nightscout.androidaps.plugins.general.automation;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class AutomationEvent {

    private Trigger trigger;
    private List<Action> actions = new ArrayList<>();
    private String title;

    AutomationEvent(String title) {
        this.title = title;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public List<Action> getActions() {
        return actions;
    }

    public String getTitle() {
        return title;
    }
}
