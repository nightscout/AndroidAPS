package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionPumpStatus
import app.aaps.core.interfaces.rx.weardata.EventData.ActionTddStatus
import app.aaps.wear.R
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.utils.MenuListActivity

class StatusMenuActivity : MenuListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.menu_status)
        super.onCreate(savedInstanceState)
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            add(MenuItem(R.drawable.ic_status, getString(R.string.status_pump)))
            add(MenuItem(R.drawable.ic_loop_closed, getString(R.string.status_loop)))
            add(MenuItem(R.drawable.ic_tdd, getString(R.string.status_tdd)))
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.status_pump) -> rxBus.send(EventWearToMobile(ActionPumpStatus(System.currentTimeMillis())))
            getString(R.string.status_loop) -> {
                // Launch new detailed status activity
                val intent = Intent(this, LoopStatusActivity::class.java)
                startActivity(intent)
            }
            getString(R.string.status_tdd)  -> rxBus.send(EventWearToMobile(ActionTddStatus(System.currentTimeMillis())))
        }
    }
}