package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Acceptance D9: a pump that does not answer can be removed. */
class AaruyUnpairTest {

    private val commandQueue: CommandQueue = mock()
    private val ble: AaruyBLE = mock()
    private val notificationManager: NotificationManager = mock()
    private val rxBus: RxBus = mock()
    private val unpair = AaruyUnpair(commandQueue, ble, notificationManager, mock<ResourceHelper>(), rxBus, mock<AAPSLogger>())

    private fun queueAnswers(success: Boolean) = runBlocking {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever(commandQueue.customCommand(any<AaruyUnpairCommand>())).thenReturn(result)
    }

    /** The pump was told. It drops the link, and AAPS counts nothing more from it. */
    @Test
    fun aToldPumpIsRemovedWithNoWarning(): Unit = runBlocking {
        queueAnswers(success = true)
        assertTrue(unpair.unpair())
        verify(ble, never()).unbindDevice()
        verify(notificationManager, never()).post(any(), any<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
        verify(rxBus).send(any<EventAaruyDeviceChange>())
    }

    /** The queue gave up on the link. The pump is removed anyway, and the user is told to take it off. */
    @Test
    fun aPumpThatIsNotReachedIsRemovedAnywayAndTheUserIsTold(): Unit = runBlocking {
        queueAnswers(success = false)
        assertFalse(unpair.unpair())
        verify(ble).unbindDevice()
        verify(notificationManager).post(eq(NotificationId.PUMP_WARNING), anyOrNull<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
        // What ends the temp basal of the removed pump in AAPS (PumpSync.connectNewPump).
        verify(rxBus).send(any<EventAaruyDeviceChange>())
    }
}
