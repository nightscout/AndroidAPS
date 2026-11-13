package app.aaps.pump.aaruy

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyBolusOutcome
import app.aaps.pump.aaruy.services.AaruyReplaceSteps
import app.aaps.pump.aaruy.services.AaruyService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** What AAPS learns about a bolus: `success` false makes the queue show "bolus delivery failed" with the real amount. */
class AaruyPluginBolusTest {

    private val rh: ResourceHelper = mock()
    private val ch: ConcentrationHelper = mock()
    private val service: AaruyService = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), rh, mock<Preferences>(), mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())
    private val plugin = AaruyPlugin(
        mock<AAPSLogger>(), rh, mock<Preferences>(), mock<CommandQueue>(), mock<RxBus>(), mock<Context>(), pump,
        mock<TemporaryBasalStorage>(), mock<DateUtil>(), mock<AaruyHistoryDatabase>(), { FakeResult() },
        mock<AaruyBLE>(), mock<AaruyReplaceSteps>(), mock<BlePreCheck>(), mock<PumpSync>(), ch, mock<NotificationManager>()
    ).also { plugin ->
        // Set by the service binding in a running app.
        AaruyPlugin::class.java.getDeclaredField("aaruyService").apply { isAccessible = true }.set(plugin, service)
    }

    private fun bolus(outcome: AaruyBolusOutcome, delivered: Double) = runBlocking {
        whenever(service.setBolus(any())).thenAnswer {
            pump.bolusDelivered = delivered
            outcome
        }
        whenever(ch.insulinAmountString(any())).thenReturn("$delivered U")
        whenever(rh.gs(any<Int>())).thenAnswer { "${it.arguments[0]}" }
        whenever(rh.gs(any<Int>(), anyOrNull())).thenAnswer { "${it.arguments[0]}:${it.arguments.drop(1)}" }
        plugin.deliverTreatment(DetailedBolusInfo().apply { insulin = 3.0 })
    }

    @Test
    fun aFinishedBolusIsASuccess() {
        val result = bolus(AaruyBolusOutcome.FINISHED, 3.0)
        assertTrue(result.success)
        assertTrue(result.enacted)
        assertEquals(3.0, result.bolusDelivered, 0.0001)
    }

    /** Seen on a real pump with a blocked line: 0.55 U of 3 U. */
    @Test
    fun aBolusThePumpCutShortFailsWithTheRealAmount() {
        val result = bolus(AaruyBolusOutcome.CUT_SHORT, 0.55)
        assertFalse(result.success)
        assertTrue(result.enacted)
        assertEquals(0.55, result.bolusDelivered, 0.0001)
        assertTrue(result.comment.startsWith("${R.string.aaruy_bolus_cut_short}:"), result.comment)
        assertTrue(result.comment.contains("0.55 U"), result.comment)
    }

    @Test
    fun aBolusThatNeverStartedIsNotEnacted() {
        val result = bolus(AaruyBolusOutcome.NOT_STARTED, 0.0)
        assertFalse(result.success)
        assertFalse(result.enacted)
    }

    /** The pump may still give the rest, so AAPS must not take it for a failed bolus and offer to bolus again. */
    @Test
    fun aBolusWithALostLinkIsNotFinishedAndSaysSo() {
        val result = bolus(AaruyBolusOutcome.UNKNOWN, 0.6)
        assertFalse(result.success)
        assertTrue(result.enacted)
        assertTrue(result.comment.startsWith("${R.string.aaruy_bolus_outcome_unknown}:"), result.comment)
    }
}

/** Only the fields a bolus sets. The real one lives in a module this one does not see. */
internal class FakeResult : PumpEnactResult {

    override var success = false
    override var enacted = false
    override var comment = ""
    override var duration = 0
    override var absolute = 0.0
    override var percent = 0
    override var isPercent = false
    override var isTempCancel = false
    override var bolusDelivered = 0.0
    override var queued = false
    override var cancelled = false
    override fun success(success: Boolean) = apply { this.success = success }
    override fun cancelled(cancelled: Boolean) = apply { this.cancelled = cancelled }
    override fun enacted(enacted: Boolean) = apply { this.enacted = enacted }
    override fun comment(comment: String) = apply { this.comment = comment }
    override fun comment(ref: TextRef) = apply { comment = ref.toString() }
    override fun duration(duration: Int) = apply { this.duration = duration }
    override fun absolute(absolute: Double) = apply { this.absolute = absolute }
    override fun percent(percent: Int) = apply { this.percent = percent }
    override fun isPercent(isPercent: Boolean) = apply { this.isPercent = isPercent }
    override fun isTempCancel(isTempCancel: Boolean) = apply { this.isTempCancel = isTempCancel }
    override fun bolusDelivered(bolusDelivered: Double) = apply { this.bolusDelivered = bolusDelivered }
    override fun queued(queued: Boolean) = apply { this.queued = queued }
}
