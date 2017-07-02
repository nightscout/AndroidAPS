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
                "Targets",
                "OAPS Result"};

    }

    @Override
    protected void doAction(int position) {
        switch (position) {

            case 0:
                ListenerService.initiateAction(this, "status pump");
                break;
            case 1:
                ListenerService.initiateAction(this, "status loop");
                break;
            case 2:
                ListenerService.initiateAction(this, "status targets");
                break;
            case 3:
                ListenerService.initiateAction(this, "status oapsresult");
                break;
        }

    }
}
