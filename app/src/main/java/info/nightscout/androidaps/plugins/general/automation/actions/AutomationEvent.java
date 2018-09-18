package info.nightscout.androidaps.plugins.general.automation.actions;

import java.util.ArrayList;
import java.util.List;

public class AutomationEvent {

    Trigger trigger;
    List<Action> actions = new ArrayList<>();

    AutomationEvent() {
    }
}
