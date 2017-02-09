package info.nightscout.androidaps.actions;

import info.nightscout.androidaps.ListenerService;
import info.nightscout.androidaps.actions.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class StatusMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "General",
                "Pump",
                "Loop",
                "Targets"};
    }

    @Override
    protected void doAction(int position) {
        switch (position) {
            case 0:
                ListenerService.initiateAction(this, "status general");
                break;
            case 1:
                ListenerService.initiateAction(this, "status pump");
                break;
            case 2:
                ListenerService.initiateAction(this, "status loop");
                break;
            case 3:
                ListenerService.initiateAction(this, "status targets");
                break;
        }

    }
}
