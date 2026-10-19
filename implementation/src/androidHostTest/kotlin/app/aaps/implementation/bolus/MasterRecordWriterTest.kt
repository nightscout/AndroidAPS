package app.aaps.implementation.bolus

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [MasterRecordWriter] is where a removal and a calibration actually happen, for the master's own screens and for
 * every paired client. A client sends only Nightscout ids, so the master must find its own copies by them, must not
 * touch a record it does not have, and must say so in the confirmation the client shows.
 */
class MasterRecordWriterTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var translator: Translator
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var xDripSource: XDripSource
    @Mock lateinit var xDripBroadcast: XDripBroadcast
    @Mock lateinit var calibration: Calibration

    private lateinit var sut: MasterRecordWriter

    @BeforeEach
    fun setUp() {
        whenever(activePlugin.activeCalibration).thenReturn(calibration)
        sut = MasterRecordWriter(
            persistenceLayer, baseText, dateUtil, profileUtil, profileFunction, decimalFormatter, translator, activePlugin,
            uel, xDripSource, xDripBroadcast, aapsLogger
        )
    }

    private fun bolus(id: Long, nsId: String?, valid: Boolean = true) =
        BS(id = id, ids = IDs(nightscoutId = nsId), isValid = valid, timestamp = now, amount = 2.0, type = BS.Type.NORMAL, iCfg = someICfg)

    private fun relayed(vararg nsIds: String) = BatchAction.Remove(BatchAction.Remove.Kind.BOLUS, nsIds.map { BatchAction.Remove.Ref(nsId = it) })

    @Test
    fun `a relayed record the master has is described from the master's own copy`() = runTest {
        whenever(persistenceLayer.getBolusByNSId("ns1")).thenReturn(bolus(1L, "ns1"))

        val plan = sut.planRemovals(listOf(relayed("ns1")))

        assertThat(plan.found).isEqualTo(1)
        assertThat(plan.lines).hasSize(2)
        assertThat(plan.lines[0].role).isEqualTo(ConfirmationRole.PRIMARY)
        assertThat(plan.lines[0].text).contains("2")
    }

    @Test
    fun `a relayed record the master does not have, or has removed already, is counted and named`() = runTest {
        whenever(persistenceLayer.getBolusByNSId("ns1")).thenReturn(bolus(1L, "ns1"))
        whenever(persistenceLayer.getBolusByNSId("gone")).thenReturn(null)
        whenever(persistenceLayer.getBolusByNSId("removed")).thenReturn(bolus(2L, "removed", valid = false))

        val plan = sut.planRemovals(listOf(relayed("ns1", "gone", "removed")))

        assertThat(plan.found).isEqualTo(1)
        assertThat(plan.notHave).isEqualTo(2)
        assertThat(plan.lines.last().text).isEqualTo(baseText.gs(CoreUiStrings.remove_not_on_master, 2))
    }

    @Test
    fun `on the master's own screen a record removed in the meantime is left out without a word`() = runTest {
        whenever(persistenceLayer.getBolusById(5L)).thenReturn(null)

        val plan = sut.planRemovals(listOf(BatchAction.Remove(BatchAction.Remove.Kind.BOLUS, listOf(BatchAction.Remove.Ref(nsId = null, localId = 5L)))))

        assertThat(plan).isEqualTo(MasterRecordWriter.RemovalPlan(lines = emptyList(), found = 0, notHave = 0))
    }

    @Test
    fun `more than five records are shown as a count`() = runTest {
        val ids = (1..6).map { "ns$it" }
        ids.forEachIndexed { i, nsId -> whenever(persistenceLayer.getBolusByNSId(nsId)).thenReturn(bolus(i.toLong() + 1, nsId)) }

        val plan = sut.planRemovals(listOf(relayed(*ids.toTypedArray())))

        assertThat(plan.lines.map { it.text }).containsExactly(baseText.gs(CoreUiStrings.confirm_remove_multiple_items, 6))
    }

    @Test
    fun `remove invalidates the master's own copy found by its Nightscout id`() = runTest {
        whenever(persistenceLayer.getBolusByNSId("ns1")).thenReturn(bolus(1L, "ns1"))

        sut.remove(listOf(relayed("ns1")), Sources.NSClient)

        verify(persistenceLayer).invalidateBolus(eq(1L), eq(Action.BOLUS_REMOVED), eq(Sources.NSClient), anyOrNull(), any())
    }

    @Test
    fun `remove skips a record that is already removed`() = runTest {
        whenever(persistenceLayer.getBolusByNSId("ns1")).thenReturn(bolus(1L, "ns1", valid = false))

        sut.remove(listOf(relayed("ns1")), Sources.NSClient)

        verify(persistenceLayer, never()).invalidateBolus(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `on the master's own screen a record is found by its local id`() = runTest {
        whenever(persistenceLayer.getBolusById(7L)).thenReturn(bolus(7L, null))

        sut.remove(listOf(BatchAction.Remove(BatchAction.Remove.Kind.BOLUS, listOf(BatchAction.Remove.Ref(nsId = null, localId = 7L)))), Sources.Treatments)

        verify(persistenceLayer).invalidateBolus(eq(7L), eq(Action.BOLUS_REMOVED), eq(Sources.Treatments), anyOrNull(), any())
    }

    @Test
    fun `a calibration entry is removed through the calibration table`() = runTest {
        val entry = CAL(id = 3L, ids = IDs(nightscoutId = "cal3"), timestamp = now, fingerstickMgdl = 110.0, sensorMgdlAtPairing = 100.0)
        whenever(persistenceLayer.getCalibrationEntryByNSId("cal3")).thenReturn(entry)

        sut.remove(listOf(BatchAction.Remove(BatchAction.Remove.Kind.CALIBRATION, listOf(BatchAction.Remove.Ref(nsId = "cal3")))), Sources.NSClient)

        verify(persistenceLayer).invalidateCalibrationEntry(eq(3L), eq(Action.CALIBRATION_REMOVED), eq(Sources.NSClient), anyOrNull(), any())
    }

    @Test
    fun `the start events are matched by the master's own text`() = runTest {
        sut.removeStartEvents(Sources.NSClient)

        verify(persistenceLayer).invalidateTherapyEventsWithNote(baseText.gs(CoreUiStrings.androidaps_start), Action.RESTART_EVENTS_REMOVED, Sources.NSClient)
    }

    @Test
    fun `a calibration the master cannot take now is refused with its reason`() = runTest {
        whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Rejected.NoSession)

        assertThat(sut.calibrationRefusal()).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_session))
    }

    @Test
    fun `a calibration the master can take is not refused`() = runTest {
        whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Accepted)

        assertThat(sut.calibrationRefusal()).isNull()
    }

    @Test
    fun `a stored calibration is logged and also sent to xDrip when xDrip is the BG source`() = runTest {
        whenever(calibration.addEntry(eq(120.0), any())).thenReturn(AddEntryResult.Accepted)
        whenever(xDripSource.isEnabled()).thenReturn(true)
        var failure: WizardBolusExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure).isNull()
        verify(uel).log(eq(Action.CALIBRATION), eq(Sources.NSClient), anyOrNull(), any<ValueWithUnit>())
        verify(xDripBroadcast).sendCalibration(any())
    }

    @Test
    fun `a calibration refused when it is stored reports the reason`() = runTest {
        whenever(calibration.addEntry(eq(120.0), any())).thenReturn(AddEntryResult.Rejected.NoSensorPair)
        var failure: WizardBolusExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_pair))
        verify(xDripBroadcast, never()).sendCalibration(any())
    }
}
