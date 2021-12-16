package info.nightscout.androidaps.interaction.menus;

import android.content.Intent;
import android.os.Bundle;

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
    protected void onCreate(Bundle savedInstanceState) {
        setTitle(R.string.menu_prime_fill);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected List<MenuItem> getElements() {
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_1)));
        menuItems.add(new MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_2)));
        menuItems.add(new MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_3)));
        menuItems.add(new MenuItem(R.drawable.ic_canula, getString(R.string.action_free_amount)));

        return menuItems;
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
