package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode.RunningMode
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.source.RunningModeSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

/**
 * Small picker with the allowed running-mode changes. Opened by a tap on the
 * running-mode complication (see `ComplicationAction.RUNNING_MODE`).
 *
 * The rows reuse the tile's launch contract wholesale ([RunningModeSource.getSelectedActions]):
 * timed modes open [RunningModeTimedActivity], the others go through [BackgroundActionActivity].
 * Both re-read the latest list timestamp from SP at send time, and the phone parks the change
 * for a wear confirmation - so the picker itself needs no confirm step.
 *
 * Works standalone: on open it shows the SP-cached list, asks the phone for a fresh
 * [EventData.RunningModeList] and refreshes the rows when the answer arrives.
 */
class RunningModePickerActivity : MenuListActivity() {

    @Inject lateinit var runningModeSource: RunningModeSource

    private val pickerDisposable = CompositeDisposable()
    private var modeList: EventData.RunningModeList? = null
    private var rows: List<PickerRow> = emptyList()

    private class PickerRow(val label: String, val action: Action)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.label_running_mode_title)
        super.onCreate(savedInstanceState)
        // Refresh the rows when a fresh list arrives. Built from the event payload directly -
        // reading SP here could race with DataHandlerWear's SP write for the same event.
        pickerDisposable += rxBus
            .toObservable(EventData.RunningModeList::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                modeList = it
                refreshElements()
            }
        rxBus.send(EventWearToMobile(EventData.RunningModeRequest(System.currentTimeMillis())))
    }

    override fun onDestroy() {
        pickerDisposable.clear()
        super.onDestroy()
    }

    override fun provideElements(): List<MenuItem> {
        val list = modeList ?: runningModeSource.currentModes().also { modeList = it }
        val actions = runningModeSource.getSelectedActions(list)
        // getSelectedActions is aligned with list.states by index
        rows = actions.mapIndexed { index, action ->
            PickerRow(getString(list.states[index].state.labelRes()), action)
        }
        return rows.map { MenuItem(it.action.iconRes, it.label) }
    }

    override fun doAction(position: String) {
        val row = rows.firstOrNull { it.label == position } ?: return
        val intent = Intent(this, Class.forName(row.action.activityClass))
        row.action.action?.let { intent.putExtra(DataLayerListenerServiceWear.KEY_ACTION, it.serialize()) }
        row.action.message?.let { intent.putExtra(DataLayerListenerServiceWear.KEY_MESSAGE, it) }
        startActivity(intent)
        finish()
    }

    @StringRes
    private fun RunningMode.labelRes(): Int = when (this) {
        RunningMode.LOOP_CLOSED       -> R.string.loop_status_closed
        RunningMode.LOOP_LGS          -> R.string.loop_status_lgs
        RunningMode.LOOP_OPEN         -> R.string.loop_status_open
        RunningMode.LOOP_DISABLE      -> R.string.loop_status_disabled
        RunningMode.LOOP_USER_SUSPEND -> R.string.title_activity_suspend_loop
        RunningMode.PUMP_DISCONNECT   -> R.string.title_activity_disconnect_pump
        RunningMode.LOOP_RESUME       -> R.string.running_mode_resume
        RunningMode.PUMP_RECONNECT    -> R.string.running_mode_reconnect
        // Not selectable - the phone never sends these in the list
        else                          -> R.string.loop_status_unknown
    }
}
