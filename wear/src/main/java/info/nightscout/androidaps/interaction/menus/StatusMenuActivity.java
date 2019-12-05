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
                getResources().getString(R.string.status_pump),
                getResources().getString(R.string.status_loop),
                getResources().getString(R.string.status_cpp),
                getResources().getString(R.string.status_tdd)};


    }

    @Override
    protected void doAction(String action) {
        if (getResources().getString(R.string.status_pump).equals(action)) {
            ListenerService.initiateAction(this, "status pump");
        } else if (getResources().getString(R.string.status_loop).equals(action)) {
            ListenerService.initiateAction(this, "status loop");
        } else if (getResources().getString(R.string.status_cpp).equals(action)) {
            ListenerService.initiateAction(this, "opencpp");
        } else if (getResources().getString(R.string.status_tdd).equals(action)) {
            ListenerService.initiateAction(this, "tddstats");
        }
    }
}
