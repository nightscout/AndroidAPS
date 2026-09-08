package app.aaps.pump.carelevo.presentation.viewmodel

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
 * driven directly from `CarelevoAlarmNotifier.showTopNotification`'s action button; this shell is not
 * in that path and owns no state or sound lifecycle of its own.
 */
class CarelevoAlarmViewModelTest {

    private lateinit var alarmActionHandler: CarelevoAlarmActionHandler
    private val uiRequestsFlow: MutableSharedFlow<AlarmEvent> = MutableSharedFlow()
    private lateinit var sut: CarelevoAlarmViewModel

    @BeforeEach
    fun setUp() {
        alarmActionHandler = mock()
        whenever(alarmActionHandler.uiRequests).thenReturn(uiRequestsFlow)

        sut = CarelevoAlarmViewModel(alarmActionHandler)
    }

    @Test
    fun `re-exposes the handler's ui-request flow unchanged`() {
        assertThat(sut.event).isSameInstanceAs(uiRequestsFlow)
    }
}
