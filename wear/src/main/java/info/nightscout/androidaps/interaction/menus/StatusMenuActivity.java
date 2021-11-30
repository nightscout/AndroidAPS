package info.nightscout.androidaps.interaction.menus;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class StatusMenuActivity extends MenuListActivity {

    @Override
    protected List<MenuElement> getElements() {
        List<MenuElement> menuitems = new ArrayList<>();
        menuitems.add(new MenuElement(R.drawable.ic_status, getString(R.string.status_pump)));
        menuitems.add(new MenuElement(R.drawable.ic_loop_closed_white, getString(R.string.status_loop)));
        menuitems.add(new MenuElement(R.drawable.ic_status, getString(R.string.status_cpp)));
        menuitems.add(new MenuElement(R.drawable.ic_iob_detailed, getString(R.string.status_tdd)));

        return menuitems;
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
