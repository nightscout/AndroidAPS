package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.FillActivity
import app.aaps.wear.interaction.utils.MenuListActivity

class FillMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.menu_prime_fill)
        super.onCreate(savedInstanceState)
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_1)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_2)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_preset_3)))
            add(MenuItem(R.drawable.ic_canula, getString(R.string.action_free_amount)))
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.action_preset_1)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(1)))
            getString(R.string.action_preset_2)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(2)))
            getString(R.string.action_preset_3)    -> rxBus.send(EventWearToMobile(EventData.ActionFillPresetPreCheck(3)))
            getString(R.string.action_free_amount) -> startActivity(Intent(this, FillActivity::class.java))
        }
    }
}
