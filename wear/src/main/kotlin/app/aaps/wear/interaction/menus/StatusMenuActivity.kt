package app.aaps.wear.interaction.menus

import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionPumpStatus
import app.aaps.core.interfaces.rx.weardata.EventData.ActionTddStatus
import app.aaps.wear.R
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.watchdirect.WatchDirect
import app.aaps.wear.watchdirect.WatchDirectActivity
import dev.zacsweers.metro.Inject

class StatusMenuActivity : MenuListActivity() {

    @Inject lateinit var watchDirect: WatchDirect

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.menu_status)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Direct to watch may have been turned on or off on the phone since the last visit.
        refreshElements()
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            add(MenuItem(R.drawable.ic_status, getString(R.string.status_pump)))
            add(MenuItem(R.drawable.ic_loop_closed, getString(R.string.status_loop)))
            add(MenuItem(R.drawable.ic_tdd, getString(R.string.status_tdd)))
            // Only while the phone has turned the feature on. Without this entry the screen could
            // only be opened from a notification, and not at all once that was swiped away.
            if (directToWatchIsOn()) add(MenuItem(R.drawable.ic_watch_direct, getString(R.string.watch_direct_title)))
        }

    private fun directToWatchIsOn(): Boolean = when (watchDirect.status.value.state) {
        EventData.CgmWatchState.OFF,
        EventData.CgmWatchState.NOT_SUPPORTED -> false

        else                                  -> true
    }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.status_pump)        -> rxBus.send(EventWearToMobile(ActionPumpStatus(System.currentTimeMillis())))
            getString(R.string.status_loop)        -> {
                // Launch new detailed status activity
                val intent = Intent(this, LoopStatusActivity::class.java)
                startActivity(intent)
            }
            getString(R.string.status_tdd)         -> rxBus.send(EventWearToMobile(ActionTddStatus(System.currentTimeMillis())))
            getString(R.string.watch_direct_title) -> startActivity(Intent(this, WatchDirectActivity::class.java))
        }
    }
}
