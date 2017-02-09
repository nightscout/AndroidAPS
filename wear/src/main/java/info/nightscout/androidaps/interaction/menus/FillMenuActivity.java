package info.nightscout.androidaps.interaction.menus;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class FillMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[] {
                "Preset 1",
                "Preset 2",
                "Preset 3"};
    }

    @Override
    protected void doAction(int position) {
        switch (position) {
            case 0:
                ListenerService.initiateAction(this, "fillpreset 1");
                break;
            case 1:
                ListenerService.initiateAction(this, "fillpreset 2");
                break;
            case 2:
                ListenerService.initiateAction(this, "fillpreset 3");
                break;
        }

    }
}
