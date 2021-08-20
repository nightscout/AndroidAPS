package info.nightscout.androidaps.interaction.menus;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class StatusMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                getString(R.string.status_pump),
                getString(R.string.status_loop),
                getString(R.string.status_cpp),
                getString(R.string.status_tdd)};


    }

    @Override
    protected void doAction(String action) {
        if (getString(R.string.status_pump).equals(action)) {
            ListenerService.initiateAction(this, "status pump");
        } else if (getString(R.string.status_loop).equals(action)) {
            ListenerService.initiateAction(this, "status loop");
        } else if (getString(R.string.status_cpp).equals(action)) {
            ListenerService.initiateAction(this, "opencpp");
        } else if (getString(R.string.status_tdd).equals(action)) {
            ListenerService.initiateAction(this, "tddstats");
        }
    }
}
