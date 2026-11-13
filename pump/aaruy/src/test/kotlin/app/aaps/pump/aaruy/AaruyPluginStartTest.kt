package app.aaps.pump.aaruy

import android.content.Context
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyReplaceSteps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AaruyPluginStartTest {

    private val preferences: Preferences = mock()
    private val commandQueue: CommandQueue = mock()
    private val rh: ResourceHelper = mock()
    private val rxBus: RxBus = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), rh, preferences, mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())
    private val plugin = AaruyPlugin(
        mock<AAPSLogger>(), rh, preferences, commandQueue, rxBus, mock<Context>(), pump,
        mock<TemporaryBasalStorage>(), mock<DateUtil>(), mock<AaruyHistoryDatabase>(), { mock<PumpEnactResult>() },
        mock<AaruyBLE>(), mock<AaruyReplaceSteps>(), mock<BlePreCheck>(), mock<PumpSync>(), mock<ConcentrationHelper>(), mock<NotificationManager>()
    )

    /**
     * Seen on a real pump: onStart waited for the first status read. During a bolus that read waits for the bolus, so the
     * app start was held for 30 s, and the pump log it read was written before the app could store it.
     */
    @Test
    fun theFirstStatusReadIsStartedAndNotWaitedFor(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        // Set by onStart in a running app.
        AaruyPlugin::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(plugin, scope)
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenReturn("AARUY-1234")
        whenever(rh.gs(any<Int>())).thenReturn("Device changed")
        val readStarted = CompletableDeferred<Unit>()
        whenever(commandQueue.readStatus(any())).doSuspendableAnswer {
            readStarted.complete(Unit)
            awaitCancellation()
        }
        try {
            // Returns although the read never ends.
            withTimeout(5_000) { plugin.changePump(newPump = false) }
            withTimeout(5_000) { readStarted.await() }
        } finally {
            scope.cancel()
        }
    }

    /** Seen on a real pump: after a new time zone it ran the old times for minutes, until the keep alive worker came. */
    @Test
    fun aTimeZoneChangeAsksForTheProfileAtOnce(): Unit = runBlocking {
        whenever(rh.gs(any<Int>())).thenReturn("Time or time zone changed")
        whenever(commandQueue.readStatus(any())).thenReturn(mock<PumpEnactResult>())
        plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged)
        inOrder(commandQueue, rxBus) {
            verify(commandQueue).readStatus(any())
            verify(rxBus).send(EventProfileChangeRequested())
        }
    }
}
