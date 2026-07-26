package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode.RunningMode
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.RunningModeTimedActivity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class RunningModeSourceTest {

    private val context: Context = mock()
    private val resources: Resources = mock()
    private val sp: SP = mock()

    private val disconnectTitle = "Disconnect pump"
    private val suspendTitle = "Suspend loop"
    private val selectedMessage = "Running mode selected"

    private lateinit var source: RunningModeSource

    @BeforeEach
    fun setup() {
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getString(R.string.title_activity_disconnect_pump)).thenReturn(disconnectTitle)
        whenever(resources.getString(R.string.title_activity_suspend_loop)).thenReturn(suspendTitle)
        whenever(resources.getString(R.string.action_running_mode_selected)).thenReturn(selectedMessage)
        source = RunningModeSource(context, sp)
    }

    /** Feeds the given running-mode states through the sp stub used by [RunningModeSource.getRunningModes]. */
    private fun stubStates(timeStamp: Long, states: List<AvailableRunningMode>) {
        val serialized = EventData.RunningModeList(timeStamp, states).serialize()
        whenever(sp.getString(eq(R.string.key_running_mode_data), any())).thenReturn(serialized)
    }

    private fun mode(state: RunningMode, durations: List<Int>? = null, title: String? = null) =
        AvailableRunningMode(state, durations, title)

    @Test
    fun getValidForIsNull() {
        assertThat(source.getValidFor()).isNull()
    }

    @Test
    fun getResourceReferencesReturnsEightDrawables() {
        val refs = source.getResourceReferences(resources)
        assertThat(refs).hasSize(8)
        assertThat(refs).containsExactly(
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

    @Test
    fun emptyStatesProduceNoActions() {
        stubStates(1000L, emptyList())
        assertThat(source.getSelectedActions()).isEmpty()
    }

    @Test
    fun nonTimedStateMapsToBackgroundActivityWithSelectedEvent() {
        stubStates(1234L, listOf(mode(RunningMode.LOOP_OPEN)))

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(1)
        val action = actions[0]
        assertThat(action.iconRes).isEqualTo(R.drawable.ic_loop_open)
        assertThat(action.activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
        assertThat(action.message).isEqualTo(selectedMessage)
        val event = action.action
        assertThat(event).isInstanceOf(EventData.RunningModeSelected::class.java)
        val selected = event as EventData.RunningModeSelected
        assertThat(selected.timeStamp).isEqualTo(1234L)
        assertThat(selected.index).isEqualTo(0)
    }

    @Test
    fun pumpDisconnectMapsToTimedActivityWithPreSelectAndNullMessage() {
        val durations = listOf(15, 30, 60)
        stubStates(5000L, listOf(mode(RunningMode.PUMP_DISCONNECT, durations)))

        val action = source.getSelectedActions().single()

        assertThat(action.iconRes).isEqualTo(R.drawable.ic_loop_disconnected)
        assertThat(action.activityClass).isEqualTo(RunningModeTimedActivity::class.java.name)
        assertThat(action.message).isNull()
        val event = action.action
        assertThat(event).isInstanceOf(EventData.RunningModePreSelect::class.java)
        val preSelect = event as EventData.RunningModePreSelect
        assertThat(preSelect.timeStamp).isEqualTo(5000L)
        assertThat(preSelect.stateIndex).isEqualTo(0)
        assertThat(preSelect.durations).containsExactlyElementsIn(durations).inOrder()
        assertThat(preSelect.title).isEqualTo(disconnectTitle)
    }

    @Test
    fun userSuspendMapsToTimedActivityWithSuspendTitleAndEmptyDurationsWhenNull() {
        stubStates(6000L, listOf(mode(RunningMode.LOOP_USER_SUSPEND, durations = null)))

        val action = source.getSelectedActions().single()

        assertThat(action.iconRes).isEqualTo(R.drawable.ic_loop_paused)
        assertThat(action.activityClass).isEqualTo(RunningModeTimedActivity::class.java.name)
        assertThat(action.message).isNull()
        val preSelect = action.action as EventData.RunningModePreSelect
        assertThat(preSelect.title).isEqualTo(suspendTitle)
        assertThat(preSelect.durations).isEmpty()
        assertThat(preSelect.stateIndex).isEqualTo(0)
    }

    @Test
    fun unmappedStateFallsBackToClosedGreenIcon() {
        stubStates(7000L, listOf(mode(RunningMode.LOOP_CLOSED)))

        val action = source.getSelectedActions().single()

        assertThat(action.iconRes).isEqualTo(R.drawable.ic_loop_closed_green)
        assertThat(action.activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
        assertThat(action.action).isInstanceOf(EventData.RunningModeSelected::class.java)
        assertThat(action.message).isEqualTo(selectedMessage)
    }

    @Test
    fun iconMappingCoversEachEnumeratedState() {
        stubStates(
            8000L,
            listOf(
                mode(RunningMode.LOOP_LGS),
                mode(RunningMode.LOOP_DISABLE),
                mode(RunningMode.LOOP_RESUME),
                mode(RunningMode.PUMP_RECONNECT)
            )
        )

        val actions = source.getSelectedActions()

        assertThat(actions.map { it.iconRes }).containsExactly(
            R.drawable.ic_loop_lgs,
            R.drawable.ic_loop_disabled,
            R.drawable.ic_loop_resume,
            R.drawable.ic_loop_reconnect
        ).inOrder()
        // None of these are timed states
        assertThat(actions.map { it.activityClass }.distinct())
            .containsExactly(BackgroundActionActivity::class.java.name)
    }

    @Test
    fun indexIsDerivedFromListPosition() {
        stubStates(
            9000L,
            listOf(
                mode(RunningMode.LOOP_OPEN),
                mode(RunningMode.LOOP_LGS),
                mode(RunningMode.LOOP_RESUME)
            )
        )

        val actions = source.getSelectedActions()

        val indices = actions.map { (it.action as EventData.RunningModeSelected).index }
        assertThat(indices).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun actionsAreCappedAtFourStates() {
        stubStates(
            10_000L,
            listOf(
                mode(RunningMode.LOOP_OPEN),
                mode(RunningMode.LOOP_LGS),
                mode(RunningMode.LOOP_DISABLE),
                mode(RunningMode.LOOP_RESUME),
                mode(RunningMode.PUMP_RECONNECT),
                mode(RunningMode.LOOP_CLOSED)
            )
        )

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(4)
        assertThat(actions.map { it.iconRes }).containsExactly(
            R.drawable.ic_loop_open,
            R.drawable.ic_loop_lgs,
            R.drawable.ic_loop_disabled,
            R.drawable.ic_loop_resume
        ).inOrder()
    }
}
