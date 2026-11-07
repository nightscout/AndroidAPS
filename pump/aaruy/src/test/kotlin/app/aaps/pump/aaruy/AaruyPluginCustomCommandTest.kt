package app.aaps.pump.aaruy

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyConfirmAlarmsCommand
import app.aaps.pump.aaruy.services.AaruyUnpairCommand
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** The commands the screens send through the queue. The queue opens the link for them, as for every command. */
class AaruyPluginCustomCommandTest {

    private val ble: AaruyBLE = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), mock<ResourceHelper>(), mock<Preferences>(), mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())
    private val plugin = AaruyPlugin(
        mock<AAPSLogger>(), mock<ResourceHelper>(), mock<Preferences>(), mock<CommandQueue>(), mock<RxBus>(), mock<Context>(), pump,
        mock<TemporaryBasalStorage>(), mock<DateUtil>(), mock<AaruyHistoryDatabase>(), { FakeResult() },
        ble, mock<BlePreCheck>(), mock<PumpSync>(), mock<ConcentrationHelper>(), mock<NotificationManager>()
    )

    @Test
    fun confirmingTheAlarmsSaysWhetherThePumpWasTold() {
        whenever(ble.confirmAlarms()).thenReturn(true)
        assertTrue(plugin.executeCustomCommand(AaruyConfirmAlarmsCommand())!!.success)
        whenever(ble.confirmAlarms()).thenReturn(false)
        assertFalse(plugin.executeCustomCommand(AaruyConfirmAlarmsCommand())!!.success)
    }

    @Test
    fun anUnpairWithALinkTellsThePump() {
        whenever(ble.isConnected).thenReturn(true)
        whenever(ble.unbindDevice()).thenReturn(true)
        assertTrue(plugin.executeCustomCommand(AaruyUnpairCommand())!!.success)
        verify(ble).unbindDevice()
    }

    /** Nothing is forgotten here: the screen asks first, as the pump stays paired on its side. */
    @Test
    fun anUnpairWithoutALinkForgetsNothing() {
        whenever(ble.isConnected).thenReturn(false)
        assertFalse(plugin.executeCustomCommand(AaruyUnpairCommand())!!.success)
        verify(ble, never()).unbindDevice()
    }

    @Test
    fun aCommandOfAnotherPumpIsNotHandled() {
        assertNull(plugin.executeCustomCommand(object : CustomCommand {
            override val statusDescription = "OTHER"
        }))
    }
}
