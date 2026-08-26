package app.aaps.pump.carelevo.presentation.model

import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class CarelevoUiEventModelTest {

    private fun alarmInfo(cause: AlarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN) =
        CarelevoAlarmInfo(
            alarmId = "alarm-1",
            alarmType = cause.alarmType,
            cause = cause,
            value = null,
            createdAt = "2026-07-16T10:00:00",
            updatedAt = "2026-07-16T10:00:00",
            isAcknowledged = false
        )

    // --- Marker interface ---------------------------------------------------

    @Test
    fun `every event hierarchy implements the Event marker`() {
        assertThat(CarelevoOverviewEvent.NoAction).isInstanceOf(Event::class.java)
        assertThat(CarelevoConnectEvent.NoAction).isInstanceOf(Event::class.java)
        assertThat(CarelevoConnectPrepareEvent.NoAction).isInstanceOf(Event::class.java)
        assertThat(CarelevoConnectSafetyCheckEvent.NoAction).isInstanceOf(Event::class.java)
        assertThat(CarelevoConnectNeedleEvent.NoAction).isInstanceOf(Event::class.java)
        assertThat(AlarmEvent.NoAction).isInstanceOf(Event::class.java)
    }

    // --- CarelevoOverviewEvent ---------------------------------------------

    @Test
    fun `CarelevoOverviewEvent data objects are singletons`() {
        assertThat(CarelevoOverviewEvent.NoAction).isSameInstanceAs(CarelevoOverviewEvent.NoAction)
        assertThat(CarelevoOverviewEvent.ClickPumpStopResumeBtn)
            .isSameInstanceAs(CarelevoOverviewEvent.ClickPumpStopResumeBtn)
    }

    @Test
    fun `CarelevoOverviewEvent members are distinct subtypes`() {
        val all: List<CarelevoOverviewEvent> = listOf(
            CarelevoOverviewEvent.NoAction,
            CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled,
            CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected,
            CarelevoOverviewEvent.DiscardFailed,
            CarelevoOverviewEvent.ResumePumpFailed,
            CarelevoOverviewEvent.StopPumpFailed,
            CarelevoOverviewEvent.ClickPumpStopResumeBtn,
            CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog,
            CarelevoOverviewEvent.ShowPumpResumeDialog,
            CarelevoOverviewEvent.StartConnectionFlow,
            CarelevoOverviewEvent.ShowPumpDiscardDialog
        )
        assertThat(all.toSet()).hasSize(all.size)
        assertThat(all).containsNoDuplicates()
    }

    // --- CarelevoConnectEvent ----------------------------------------------

    @Test
    fun `CarelevoConnectEvent members are distinct singletons`() {
        val all: List<CarelevoConnectEvent> = listOf(
            CarelevoConnectEvent.NoAction,
            CarelevoConnectEvent.DiscardComplete,
            CarelevoConnectEvent.DiscardFailed,
            CarelevoConnectEvent.ExitFlow
        )
        assertThat(all.toSet()).hasSize(all.size)
        assertThat(CarelevoConnectEvent.ExitFlow).isSameInstanceAs(CarelevoConnectEvent.ExitFlow)
    }

    // --- CarelevoConnectPrepareEvent ---------------------------------------

    @Test
    fun `CarelevoConnectPrepareEvent members are distinct`() {
        val all: List<CarelevoConnectPrepareEvent> = listOf(
            CarelevoConnectPrepareEvent.NoAction,
            CarelevoConnectPrepareEvent.ShowConnectDialog,
            CarelevoConnectPrepareEvent.ShowMessageScanFailed,
            CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled,
            CarelevoConnectPrepareEvent.ShowMessageScanIsWorking,
            CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty,
            CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo,
            CarelevoConnectPrepareEvent.ConnectComplete,
            CarelevoConnectPrepareEvent.ConnectFailed,
            CarelevoConnectPrepareEvent.DiscardComplete,
            CarelevoConnectPrepareEvent.DiscardFailed
        )
        assertThat(all.toSet()).hasSize(all.size)
        assertThat(all).containsNoDuplicates()
    }

    // --- CarelevoConnectSafetyCheckEvent -----------------------------------

    @Test
    fun `CarelevoConnectSafetyCheckEvent members are distinct`() {
        val all: List<CarelevoConnectSafetyCheckEvent> = listOf(
            CarelevoConnectSafetyCheckEvent.NoAction,
            CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled,
            CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected,
            CarelevoConnectSafetyCheckEvent.SafetyCheckProgress,
            CarelevoConnectSafetyCheckEvent.SafetyCheckComplete,
            CarelevoConnectSafetyCheckEvent.SafetyCheckFailed,
            CarelevoConnectSafetyCheckEvent.DiscardComplete,
            CarelevoConnectSafetyCheckEvent.DiscardFailed
        )
        assertThat(all.toSet()).hasSize(all.size)
        assertThat(all).containsNoDuplicates()
    }

    // --- CarelevoConnectNeedleEvent (with data classes) --------------------

    @Test
    fun `CarelevoConnectNeedleEvent data objects are distinct singletons`() {
        val all: List<CarelevoConnectNeedleEvent> = listOf(
            CarelevoConnectNeedleEvent.NoAction,
            CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled,
            CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected,
            CarelevoConnectNeedleEvent.ShowMessageProfileNotSet,
            CarelevoConnectNeedleEvent.CheckNeedleError,
            CarelevoConnectNeedleEvent.DiscardComplete,
            CarelevoConnectNeedleEvent.DiscardFailed,
            CarelevoConnectNeedleEvent.SetBasalComplete,
            CarelevoConnectNeedleEvent.SetBasalFailed
        )
        assertThat(all.toSet()).hasSize(all.size)
    }

    @Test
    fun `CheckNeedleComplete carries its boolean result`() {
        assertThat(CarelevoConnectNeedleEvent.CheckNeedleComplete(true).result).isTrue()
        assertThat(CarelevoConnectNeedleEvent.CheckNeedleComplete(false).result).isFalse()
    }

    @Test
    fun `CheckNeedleComplete equality and copy behave as a data class`() {
        val a = CarelevoConnectNeedleEvent.CheckNeedleComplete(true)
        val b = CarelevoConnectNeedleEvent.CheckNeedleComplete(true)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a.copy(result = false)).isEqualTo(CarelevoConnectNeedleEvent.CheckNeedleComplete(false))
        assertThat(a).isNotEqualTo(CarelevoConnectNeedleEvent.CheckNeedleComplete(false))
        assertThat(a.component1()).isTrue()
    }

    @Test
    fun `CheckNeedleFailed carries its failed count`() {
        val e = CarelevoConnectNeedleEvent.CheckNeedleFailed(3)
        assertThat(e.failedCount).isEqualTo(3)
        assertThat(e).isEqualTo(CarelevoConnectNeedleEvent.CheckNeedleFailed(3))
        assertThat(e).isNotEqualTo(CarelevoConnectNeedleEvent.CheckNeedleFailed(4))
        assertThat(e.copy(failedCount = 0).failedCount).isEqualTo(0)
    }

    @Test
    fun `CheckNeedleComplete and CheckNeedleFailed are different subtypes`() {
        val complete: CarelevoConnectNeedleEvent = CarelevoConnectNeedleEvent.CheckNeedleComplete(true)
        val failed: CarelevoConnectNeedleEvent = CarelevoConnectNeedleEvent.CheckNeedleFailed(1)
        assertThat(complete).isNotEqualTo(failed)
    }

    // --- AlarmEvent (with data classes) ------------------------------------

    @Test
    fun `AlarmEvent data objects are distinct singletons`() {
        val all: List<AlarmEvent> = listOf(
            AlarmEvent.NoAction,
            AlarmEvent.RequestBluetoothEnable,
            AlarmEvent.Mute,
            AlarmEvent.Mute5min,
            AlarmEvent.StartAlarm
        )
        assertThat(all.toSet()).hasSize(all.size)
        assertThat(AlarmEvent.Mute5min).isSameInstanceAs(AlarmEvent.Mute5min)
    }

    @Test
    fun `ClearAlarm carries its alarm info`() {
        val info = alarmInfo()
        val event = AlarmEvent.ClearAlarm(info)
        assertThat(event.info).isSameInstanceAs(info)
        assertThat(event).isEqualTo(AlarmEvent.ClearAlarm(info))
        assertThat(event.component1()).isSameInstanceAs(info)
    }

    @Test
    fun `ClearAlarm distinguishes different alarm infos`() {
        val a = AlarmEvent.ClearAlarm(alarmInfo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN))
        val b = AlarmEvent.ClearAlarm(alarmInfo(AlarmCause.ALARM_WARNING_PUMP_CLOGGED))
        assertThat(a).isNotEqualTo(b)
        assertThat(a.copy(info = b.info)).isEqualTo(b)
    }

    @Test
    fun `ShowToastMessage carries its message resource id`() {
        val event = AlarmEvent.ShowToastMessage(42)
        assertThat(event.messageRes).isEqualTo(42)
        assertThat(event).isEqualTo(AlarmEvent.ShowToastMessage(42))
        assertThat(event).isNotEqualTo(AlarmEvent.ShowToastMessage(43))
        assertThat(event.copy(messageRes = 7).messageRes).isEqualTo(7)
    }
}
