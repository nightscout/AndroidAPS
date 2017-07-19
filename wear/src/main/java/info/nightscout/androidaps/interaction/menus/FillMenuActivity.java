package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.actions.FillActivity;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class FillMenuActivity extends MenuListActivity {

    @Override
    protected String[] getElements() {
        return new String[]{
                "Preset 1",
                "Preset 2",
                "Preset 3",
                "Free amount"
        };
    }

    @Override
    protected void doAction(String action) {
        if ("Preset 1".equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 1");
        } else if ("Preset 2".equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 2");
        } else if ("Preset 3".equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 3");
        } else if ("Free amount".equals(action)) {
            Intent intent = new Intent(this, FillActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        }
    }
}
