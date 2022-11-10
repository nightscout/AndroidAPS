package info.nightscout.androidaps.interaction.menus

import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.androidaps.interaction.utils.MenuListActivity
import info.nightscout.rx.weardata.EventData.ActionLoopStatus
import info.nightscout.rx.weardata.EventData.ActionPumpStatus
import info.nightscout.rx.weardata.EventData.ActionTddStatus

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
            getString(R.string.status_loop) -> rxBus.send(EventWearToMobile(ActionLoopStatus(System.currentTimeMillis())))
            getString(R.string.status_tdd)  -> rxBus.send(EventWearToMobile(ActionTddStatus(System.currentTimeMillis())))
        }
    }
}
