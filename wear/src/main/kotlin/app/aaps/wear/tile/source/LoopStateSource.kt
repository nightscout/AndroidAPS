package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.LoopStatesList.AvailableLoopState.LoopState
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.LoopStateTimedActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.TileSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoopStateSource @Inject constructor(private val context: Context, private val sp: SP) : TileSource {

    override fun getSelectedActions(): List<Action> {
        val actions = mutableListOf<Action>()
        val states = getLoopStates(sp)

        for (state in states.states) {
            if (actions.size == 4) break
            val index = states.states.indexOf(state)
            actions.add(
                Action(
                    iconRes = when (state.state) {
                        LoopState.LOOP_OPEN -> R.drawable.ic_loop_open
                        LoopState.LOOP_LGS -> R.drawable.ic_loop_lgs
                        LoopState.LOOP_DISABLE      -> R.drawable.ic_loop_disabled
                        LoopState.LOOP_USER_SUSPEND -> R.drawable.ic_loop_paused
                        LoopState.LOOP_RESUME       -> R.drawable.ic_loop_resume
                        LoopState.PUMP_DISCONNECT -> R.drawable.ic_loop_disconnected
                        else -> R.drawable.ic_loop_closed_green
                    },
                    activityClass = when (state.state) {
                        LoopState.PUMP_DISCONNECT, LoopState.LOOP_USER_SUSPEND -> LoopStateTimedActivity::class.java.name
                        else                                                   -> BackgroundActionActivity::class.java.name
                    },
                    action = when (state.state) {
                        LoopState.PUMP_DISCONNECT, LoopState.LOOP_USER_SUSPEND -> EventData.LoopStatePreSelect(states.timeStamp, index, state.durations ?: listOf())
                        else                                                   -> EventData.LoopStateSelected(states.timeStamp, index)
                    },
                    message = when (state.state) {
                        LoopState.PUMP_DISCONNECT, LoopState.LOOP_USER_SUSPEND -> null
                        else                                                   -> context.resources.getString(R.string.action_loop_state_selected)
                    },
                )
            )
        }
        return actions
    }

    override fun getValidFor(): Long? = null

    private fun getLoopStates(sp: SP): EventData.LoopStatesList =
        EventData.deserialize(sp.getString(R.string.key_loop_states_data, EventData.LoopStatesList(0, arrayListOf()).serialize())) as EventData.LoopStatesList

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
