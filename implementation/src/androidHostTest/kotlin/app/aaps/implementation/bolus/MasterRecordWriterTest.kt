package app.aaps.implementation.bolus

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CalibrationMode
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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

    /** The active calibration plugin, as the Linear one: a real plugin, not the default one that stores nothing. */
    private val calibration: Calibration = calibrationPlugin(defaultPlugin = false)

    private fun calibrationPlugin(defaultPlugin: Boolean): Calibration {
        val plugin = mock<PluginBase>(extraInterfaces = arrayOf(Calibration::class))
        whenever(plugin.pluginDescription).thenReturn(PluginDescription().apply { this.defaultPlugin = defaultPlugin })
        return plugin as Calibration
    }

    private lateinit var sut: MasterRecordWriter

    @BeforeEach
    fun setUp() {
        whenever(activePlugin.activeCalibration).thenReturn(calibration)
        sut = MasterRecordWriter(
            persistenceLayer, baseText, dateUtil, profileUtil, profileFunction, decimalFormatter, translator, activePlugin,
            uel, aapsLogger
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

    // ----- calibration: AAPS, the BG source does not calibrate -----

    /** The BG source and its calibration mode. A Mockito mock does not run interface defaults. */
    private fun bgSource(mode: CalibrationMode): BgSource = mock<BgSource>().also {
        whenever(it.calibrationMode).thenReturn(mode)
        whenever(activePlugin.activeBgSource).thenReturn(it)
    }

    @Test
    fun `a calibration the master cannot take now is refused with its reason`() = runTest {
        bgSource(CalibrationMode.AAPS)
        whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Rejected.NoSession)

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_session))
    }

    @Test
    fun `a calibration the master can take is not refused`() = runTest {
        bgSource(CalibrationMode.AAPS)
        whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Accepted)

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isNull()
    }

    @Test
    fun `a calibration stored by the calibration plugin is logged`() = runTest {
        val source = bgSource(CalibrationMode.AAPS)
        whenever(calibration.addEntry(eq(120.0), any())).thenReturn(AddEntryResult.Accepted)
        var failure: MasterActionExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure).isNull()
        verify(uel).log(eq(Action.CALIBRATION), eq(Sources.NSClient), anyOrNull(), any<ValueWithUnit>())
        verify(source, never()).calibrate(any())
    }

    @Test
    fun `a calibration refused when it is stored reports the reason`() = runTest {
        bgSource(CalibrationMode.AAPS)
        whenever(calibration.addEntry(eq(120.0), any())).thenReturn(AddEntryResult.Rejected.NoSensorPair)
        var failure: MasterActionExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_pair))
    }

    /**
     * No calibration plugin: the default one would take the value and store nothing, so it would look done and be
     * lost. An SMS CAL or a client with an out-of-date plugin selection can send one.
     */
    @Test
    fun `a calibration with no calibration plugin is refused, not lost`() = runTest {
        bgSource(CalibrationMode.AAPS)
        val noCalibration = calibrationPlugin(defaultPlugin = true)
        whenever(activePlugin.activeCalibration).thenReturn(noCalibration)
        var failure: MasterActionExecutor.Failure? = null

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_plugin))
        sut.addCalibration(BatchAction.Calibration(120.0), Sources.SMS) { failure = it }

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_no_plugin))
        verify(noCalibration, never()).addEntry(any(), any())
        verify(uel).log(eq(Action.CALIBRATION), eq(Sources.SMS), anyOrNull(), any<ValueWithUnit>())
    }

    // ----- calibration: the value -----

    /** "CAL 100" sent by SMS with mmol/L is 1800 mg/dL: a typo, refused before it can bend the fit or reach a sensor. */
    @Test
    fun `a calibration outside what the dialog offers is refused before it goes anywhere`() = runTest {
        val source = bgSource(CalibrationMode.SOURCE)
        doReturn("mg/dL").whenever(rh).gs(CoreUiStrings.bg_mgdl)
        val typo = BatchAction.Calibration(1800.0)
        val expected = baseText.gs(
            CoreUiStrings.cal_reject_out_of_range,
            profileUtil.fromMgdlToStringWithUnits(1800.0), profileUtil.fromMgdlToStringWithUnits(36.0), profileUtil.fromMgdlToStringWithUnits(500.0)
        )
        var failure: MasterActionExecutor.Failure? = null

        assertThat(sut.calibrationRefusal(typo)).isEqualTo(expected)
        sut.addCalibration(typo, Sources.SMS) { failure = it }

        assertThat(failure?.comment).isEqualTo(expected)
        verify(source, never()).calibrationRefusal()
        verify(source, never()).calibrate(any())
        verify(calibration, never()).addEntry(any(), any())
    }

    /** A sensor that takes 40 to 400 mg/dL (an Eversense): a value it would refuse is refused before the confirmation. */
    @Test
    fun `a self-calibrating source narrows the range to what it takes`() = runTest {
        val source = bgSource(CalibrationMode.SOURCE)
        whenever(source.calibrationRangeMgdl).thenReturn(40.0..400.0)
        doReturn("mg/dL").whenever(rh).gs(CoreUiStrings.bg_mgdl)
        val expected = baseText.gs(
            CoreUiStrings.cal_reject_out_of_range,
            profileUtil.fromMgdlToStringWithUnits(39.0), profileUtil.fromMgdlToStringWithUnits(40.0), profileUtil.fromMgdlToStringWithUnits(400.0)
        )

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(39.0))).isEqualTo(expected)
        assertThat(sut.calibrationRefusal(BatchAction.Calibration(401.0))).isNotNull()
        assertThat(sut.calibrationRefusal(BatchAction.Calibration(40.0))).isNull()
        assertThat(sut.calibrationRefusal(BatchAction.Calibration(400.0))).isNull()
    }

    /** The ends of the range are what the dialog offers, so they are taken. */
    @Test
    fun `the ends of the range are taken`() = runTest {
        bgSource(CalibrationMode.AAPS)
        doReturn("mg/dL").whenever(rh).gs(CoreUiStrings.bg_mgdl)
        whenever(calibration.checkPreconditions()).thenReturn(AddEntryResult.Accepted)

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(36.0))).isNull()
        assertThat(sut.calibrationRefusal(BatchAction.Calibration(500.0))).isNull()
        assertThat(sut.calibrationRefusal(BatchAction.Calibration(35.0))).isNotNull()
    }

    // ----- calibration: SOURCE, a CGM driver or xDrip takes it -----

    @Test
    fun `a source that takes calibrations decides the refusal, not the calibration plugin`() = runTest {
        val source = bgSource(CalibrationMode.SOURCE)
        whenever(source.calibrationRefusal()).thenReturn("transmitter busy")

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isEqualTo("transmitter busy")
        verify(calibration, never()).checkPreconditions()
    }

    @Test
    fun `a calibration goes to the source that takes calibrations and is logged`() = runTest {
        val source = bgSource(CalibrationMode.SOURCE)
        whenever(source.calibrate(120.0)).thenReturn(null)
        var failure: MasterActionExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure).isNull()
        verify(source).calibrate(120.0)
        verify(calibration, never()).addEntry(any(), any())
        verify(uel).log(eq(Action.CALIBRATION), eq(Sources.NSClient), anyOrNull(), any<ValueWithUnit>())
    }

    /** xDrip+ gets the broadcast also when it ignores it, so the user reads what it needs before and after. */
    @Test
    fun `the confirmation of a calibration the source takes carries the source's note`() {
        val source = bgSource(CalibrationMode.SOURCE)
        whenever(source.calibrationNote()).thenReturn("turn it on")
        doReturn("mg/dL").whenever(rh).gs(CoreUiStrings.bg_mgdl)

        val lines = sut.calibrationLines(BatchAction.Calibration(120.0))

        assertThat(lines.map { it.role }).containsExactly(ConfirmationRole.PRIMARY, ConfirmationRole.INFO).inOrder()
        assertThat(lines.last().text).isEqualTo("turn it on")
    }

    @Test
    fun `the note of a source that does not take the calibration is not shown`() {
        val source = bgSource(CalibrationMode.AAPS)
        whenever(source.calibrationNote()).thenReturn("turn it on")
        doReturn("mg/dL").whenever(rh).gs(CoreUiStrings.bg_mgdl)

        assertThat(sut.calibrationLines(BatchAction.Calibration(120.0)).map { it.role }).containsExactly(ConfirmationRole.PRIMARY)
    }

    @Test
    fun `a calibration the source refuses reports the source's reason`() = runTest {
        val source = bgSource(CalibrationMode.SOURCE)
        whenever(source.calibrate(120.0)).thenReturn("sensor refused")
        var failure: MasterActionExecutor.Failure? = null

        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure?.comment).isEqualTo("sensor refused")
    }

    // ----- calibration: OWN_APP, the vendor app calibrates -----

    /** Only a client can send one: on this phone the button opens the vendor app. It must not look done. */
    @Test
    fun `a calibration for a sensor calibrated in its own app is refused, not dropped`() = runTest {
        val source = bgSource(CalibrationMode.OWN_APP)
        var failure: MasterActionExecutor.Failure? = null

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_in_cgm_app))
        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_in_cgm_app))
        verify(calibration, never()).addEntry(any(), any())
        verify(source, never()).calibrate(any())
    }

    // ----- calibration: PUMP, the sensor is calibrated on the pump -----

    /** A Medtronic sensor read through the pump: neither AAPS nor an app can take it, and the user is told where. */
    @Test
    fun `a calibration for a sensor calibrated on the pump is refused with where to do it`() = runTest {
        val source = bgSource(CalibrationMode.PUMP)
        var failure: MasterActionExecutor.Failure? = null

        assertThat(sut.calibrationRefusal(BatchAction.Calibration(120.0))).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_on_pump))
        sut.addCalibration(BatchAction.Calibration(120.0), Sources.NSClient) { failure = it }

        assertThat(failure?.comment).isEqualTo(baseText.gs(CoreUiStrings.cal_reject_on_pump))
        verify(calibration, never()).checkPreconditions()
        verify(calibration, never()).addEntry(any(), any())
        verify(source, never()).calibrate(any())
    }
}
