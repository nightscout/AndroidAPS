package info.nightscout.androidaps.interaction.menus;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class StatusMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "Pump",
                "Loop",
                "CPP",
                "TDD"};


    }

    @Override
    protected void doAction(String action) {
        if ("Pump".equals(action)) {
            ListenerService.initiateAction(this, "status pump");
        } else if ("Loop".equals(action)) {
            ListenerService.initiateAction(this, "status loop");
        } else if ("CPP".equals(action)) {
            ListenerService.initiateAction(this, "opencpp");
        } else if ("TDD".equals(action)) {
            ListenerService.initiateAction(this, "tddstats");
        }
    }
}
