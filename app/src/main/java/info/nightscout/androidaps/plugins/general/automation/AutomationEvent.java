package info.nightscout.androidaps.plugins.general.automation;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.general.automation.actions.Action;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;

public class AutomationEvent {

    Trigger trigger;
    List<Action> actions = new ArrayList<>();

    AutomationEvent() {
    }
}
