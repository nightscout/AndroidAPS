package app.aaps.pump.carelevo.presentation.viewmodel

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [CarelevoAlarmViewModel].
 *
 * The VM is now a pure pass-through: [app.aaps.pump.carelevo.compose.alarm.CarelevoAlarmHost] only
 * ever needs the handler's UI-request stream (Bluetooth-enable intent, failure toast). The actual
 * alarm state machine — including clearing an alarm — lives entirely in [CarelevoAlarmActionHandler],
 * driven directly from the action of each pump alert (`CarelevoAlarmNotifier.toPumpAlert`); this shell is not
 * in that path and owns no state or sound lifecycle of its own.
 */
class CarelevoAlarmViewModelTest {

    private lateinit var alarmActionHandler: CarelevoAlarmActionHandler
    private lateinit var rh: ResourceHelper
    private val uiRequestsFlow: MutableSharedFlow<AlarmEvent> = MutableSharedFlow()
    private lateinit var sut: CarelevoAlarmViewModel

    @BeforeEach
    fun setUp() {
        alarmActionHandler = mock()
        rh = mock()
        whenever(alarmActionHandler.uiRequests).thenReturn(uiRequestsFlow)

        sut = CarelevoAlarmViewModel(alarmActionHandler, rh)
    }

    @Test
    fun `re-exposes the handler's ui-request flow unchanged`() {
        assertThat(sut.event).isSameInstanceAs(uiRequestsFlow)
    }

    @Test
    fun `the text of a message comes from the resource helper`() {
        whenever(rh.gs(CarelevoStrings.alarm_feat_msg_check_patch_connect)).thenReturn("Check the patch connection")

        assertThat(sut.text(CarelevoStrings.alarm_feat_msg_check_patch_connect)).isEqualTo("Check the patch connection")
    }
}
