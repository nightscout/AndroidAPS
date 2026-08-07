package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode.RunningMode
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.utils.MenuListActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.source.RunningModeSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    private val pickerDisposable = CompositeDisposable()
    private var modeList: EventData.RunningModeList? = null
    private var rows: List<PickerRow> = emptyList()

    private class PickerRow(val label: String, val iconTint: Int, val action: Action)

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
                updateSubtitle()
            }
        rxBus.send(EventWearToMobile(EventData.RunningModeRequest(System.currentTimeMillis())))
        updateSubtitle()
    }

    /** Show the current mode under the title, with the remaining time when the mode is temporary. */
    private fun updateSubtitle() {
        lifecycleScope.launch {
            val status = complicationDataRepository.complicationData.first().statusData
            val modeLabel = status.loopMode.labelRes()?.let { getString(it) }
            val remaining = status.modeEndTime?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() }?.takeIf { it > 0 }
            subtitle = when {
                modeLabel == null -> null
                remaining != null -> getString(R.string.running_mode_picker_current, modeLabel, formatDurationMinutes(this@RunningModePickerActivity, remaining))
                else              -> modeLabel
            }
            subtitleColor = if (modeLabel != null) status.loopMode.toTextColor() else null
        }
    }

    @StringRes
    private fun LoopStatusData.LoopMode.labelRes(): Int? = when (this) {
        LoopStatusData.LoopMode.CLOSED         -> R.string.loop_status_closed
        LoopStatusData.LoopMode.OPEN           -> R.string.loop_status_open
        LoopStatusData.LoopMode.LGS            -> R.string.loop_status_lgs
        LoopStatusData.LoopMode.DISABLED       -> R.string.loop_status_disabled
        LoopStatusData.LoopMode.SUSPENDED      -> R.string.loop_status_suspended
        LoopStatusData.LoopMode.PUMP_SUSPENDED -> R.string.loop_status_pump_suspended
        LoopStatusData.LoopMode.DST_SUSPENDED  -> R.string.loop_status_dst_suspended
        LoopStatusData.LoopMode.DISCONNECTED   -> R.string.loop_status_disconnected
        LoopStatusData.LoopMode.SUPERBOLUS     -> R.string.loop_status_superbolus
        LoopStatusData.LoopMode.UNKNOWN        -> null
    }

    override fun onDestroy() {
        pickerDisposable.clear()
        super.onDestroy()
    }

    override fun provideElements(): List<MenuItem> {
        val list = modeList ?: runningModeSource.currentModes().also { modeList = it }
        // getSelectedActions is aligned with list.states by index. Pair state and action FIRST,
        // then sort for display - the selection index inside each action keeps pointing at the
        // phone's list position. Explicit icon tint: the ic_loop_* drawables carry a theme tint
        // that would otherwise paint every icon in the same color instead of the tile colors.
        rows = runningModeSource.getSelectedActions(list)
            .mapIndexed { index, action -> list.states[index].state to action }
            .sortedBy { (state, _) -> state.pickerOrder() }
            .map { (state, action) -> PickerRow(getString(state.labelRes()), state.colorArgb(), action) }
        return rows.map { MenuItem(it.action.iconRes, it.label, iconTint = it.iconTint) }
    }

    /** Display order in the picker; the resume actions first (the primary action while in a temporary mode). */
    private fun RunningMode.pickerOrder(): Int = when (this) {
        RunningMode.LOOP_RESUME       -> 0
        RunningMode.PUMP_RECONNECT    -> 1
        RunningMode.PUMP_DISCONNECT   -> 2
        RunningMode.LOOP_USER_SUSPEND -> 3
        RunningMode.LOOP_CLOSED       -> 4
        RunningMode.LOOP_LGS          -> 5
        RunningMode.LOOP_OPEN         -> 6
        RunningMode.LOOP_DISABLE      -> 7
        else                          -> 8
    }

    override fun doAction(position: String) {
        val row = rows.firstOrNull { it.label == position } ?: return
        val intent = Intent(this, Class.forName(row.action.activityClass))
        row.action.action?.let { intent.putExtra(DataLayerListenerServiceWear.KEY_ACTION, it.serialize()) }
        row.action.message?.let { intent.putExtra(DataLayerListenerServiceWear.KEY_MESSAGE, it) }
        startActivity(intent)
        finish()
    }

    /** Same colors as the tile icons (suspend icon is red; LoopSuspendedColor yellow is only for text). */
    private fun RunningMode.colorArgb(): Int = when (this) {
        RunningMode.LOOP_CLOSED       -> LoopClosedColor.toArgb()
        RunningMode.LOOP_LGS          -> LoopLgsColor.toArgb()
        RunningMode.LOOP_OPEN         -> LoopOpenColor.toArgb()
        RunningMode.LOOP_DISABLE      -> LoopDisabledColor.toArgb()
        RunningMode.LOOP_USER_SUSPEND -> LoopDisabledColor.toArgb()
        RunningMode.PUMP_DISCONNECT   -> LoopDisconnectedColor.toArgb()
        RunningMode.LOOP_RESUME,
        RunningMode.PUMP_RECONNECT    -> LoopClosedColor.toArgb()
        else                          -> LoopUnknownColor.toArgb()
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
