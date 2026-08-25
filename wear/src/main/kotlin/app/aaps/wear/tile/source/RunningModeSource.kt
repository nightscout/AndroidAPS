package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode.RunningMode
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.RunningModeTimedActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.TileSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningModeSource @Inject constructor(private val context: Context, private val sp: SP) : TileSource {

    // The tile has only 4 slots: drop LOOP_DISABLE when LOOP_USER_SUSPEND is also offered and cap
    // at 4. This filter lives here (not on the phone) so the running-mode picker can show every entry.
    override fun getSelectedActions(): List<Action> {
        val states = getRunningModes(sp)
        val tileStates = if (states.states.any { it.state == RunningMode.LOOP_USER_SUSPEND })
            states.states.filter { it.state != RunningMode.LOOP_DISABLE }
        else states.states
        return buildActions(states, tileStates).take(4)
    }

    /**
     * Build the launchable actions for the FULL [states] list, one per entry, aligned by index.
     * Used by the running-mode complication picker, which passes a freshly received
     * [EventData.RunningModeList] instead of the SP-cached one and shows every entry.
     */
    fun getSelectedActions(states: EventData.RunningModeList): List<Action> = buildActions(states, states.states)

    /** [subset] entries must come from [states]`.states` — the action index is looked up there. */
    private fun buildActions(states: EventData.RunningModeList, subset: List<EventData.RunningModeList.AvailableRunningMode>): List<Action> {
        val actions = mutableListOf<Action>()

        for (state in subset) {
            val index = states.states.indexOf(state)
            actions.add(
                Action(
                    iconRes = when (state.state) {
                        RunningMode.LOOP_OPEN -> R.drawable.ic_loop_open
                        RunningMode.LOOP_LGS -> R.drawable.ic_loop_lgs
                        RunningMode.LOOP_DISABLE      -> R.drawable.ic_loop_disabled
                        RunningMode.LOOP_USER_SUSPEND -> R.drawable.ic_loop_paused
                        RunningMode.LOOP_RESUME       -> R.drawable.ic_loop_resume
                        RunningMode.PUMP_DISCONNECT   -> R.drawable.ic_loop_disconnected
                        RunningMode.PUMP_RECONNECT    -> R.drawable.ic_loop_reconnect
                        else -> R.drawable.ic_loop_closed_green
                    },
                    activityClass = when (state.state) {
                        RunningMode.PUMP_DISCONNECT, RunningMode.LOOP_USER_SUSPEND -> RunningModeTimedActivity::class.java.name
                        else                                                   -> BackgroundActionActivity::class.java.name
                    },
                    action = when (state.state) {
                        RunningMode.PUMP_DISCONNECT, RunningMode.LOOP_USER_SUSPEND -> EventData.RunningModePreSelect(
                            states.timeStamp, index, state.durations ?: listOf(),
                            title = context.resources.getString(
                                if (state.state == RunningMode.PUMP_DISCONNECT) R.string.title_activity_disconnect_pump
                                else R.string.title_activity_suspend_loop
                            )
                        )
                        else                                                   -> EventData.RunningModeSelected(states.timeStamp, index)
                    },
                    message = when (state.state) {
                        RunningMode.PUMP_DISCONNECT, RunningMode.LOOP_USER_SUSPEND -> null
                        else                                                   -> context.resources.getString(R.string.action_running_mode_selected)
                    },
                )
            )
        }
        return actions
    }

    override fun getValidFor(): Long? = null

    /** Latest mode list received from the phone (SP-cached). */
    fun currentModes(): EventData.RunningModeList = getRunningModes(sp)

    private fun getRunningModes(sp: SP): EventData.RunningModeList =
        EventData.deserialize(sp.getString(R.string.key_running_mode_data, EventData.RunningModeList(0, arrayListOf()).serialize())) as EventData.RunningModeList

    override fun getResourceReferences(resources: Resources): List<Int> = listOf(
        R.drawable.ic_loop_open,
        R.drawable.ic_loop_lgs,
        R.drawable.ic_loop_disabled,
        R.drawable.ic_loop_paused,
        R.drawable.ic_loop_resume,
        R.drawable.ic_loop_reconnect,
        R.drawable.ic_loop_disconnected,
        R.drawable.ic_loop_closed_green
    )
}
