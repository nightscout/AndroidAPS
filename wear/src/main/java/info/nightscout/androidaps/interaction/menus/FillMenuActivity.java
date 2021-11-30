package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.actions.FillActivity;
import info.nightscout.androidaps.interaction.utils.MenuListActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class FillMenuActivity extends MenuListActivity {

    @Override
    protected List<MenuElement> getElements() {
        List<MenuElement> menuitems = new ArrayList<>();
        menuitems.add(new MenuElement(R.drawable.ic_canula, getString(R.string.action_preset_1)));
        menuitems.add(new MenuElement(R.drawable.ic_canula, getString(R.string.action_preset_2)));
        menuitems.add(new MenuElement(R.drawable.ic_canula, getString(R.string.action_preset_3)));
        menuitems.add(new MenuElement(R.drawable.ic_canula, getString(R.string.action_free_amount)));

        return menuitems;
    }

    @Override
    protected void doAction(String action) {
        if (getString(R.string.action_preset_1).equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 1");
        } else if (getString(R.string.action_preset_2).equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 2");
        } else if (getString(R.string.action_preset_3).equals(action)) {
            ListenerService.initiateAction(this, "fillpreset 3");
        } else if (getString(R.string.action_free_amount).equals(action)) {
            Intent intent = new Intent(this, FillActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);
        }
    }
}
