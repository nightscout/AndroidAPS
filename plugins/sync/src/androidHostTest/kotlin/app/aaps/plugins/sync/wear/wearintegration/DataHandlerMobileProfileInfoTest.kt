package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.weardata.ProfileInfo
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The profile part of the watch's Loop Status: name, modifiers, end time and the profile that
 * returns after a temporary switch, plus the mark for a switch the active scene made. Driven
 * through the `internal` builder, since the whole status builder is private and touches half the
 * app.
 */
class DataHandlerMobileProfileInfoTest : TestBaseWithProfile() {

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var trendCalculator: TrendCalculator
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var uiInteraction: UiInteraction
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var importExportPrefs: ImportExportPrefs
    @Mock private lateinit var pumpStatusProvider: PumpStatusProvider
    @Mock private lateinit var runningModeGuard: RunningModeGuard
    @Mock private lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var wizardExecutor: WizardExecutor

    private lateinit var sut: DataHandlerMobile

    // `now` comes from the shared test base

    @BeforeEach
    fun prepare() {
        sut = DataHandlerMobile(
            context, rxBus, aapsLogger, rh, preferences, config,
            iobCobCalculator, processedTbrEbData, smbGlucoseStatusProvider, profileFunction, profileUtil,
            loop, processedDeviceStatusData, receiverStatusStore, quickWizard, trendCalculator, dateUtil,
            constraintsChecker, activePlugin, commandQueue, fabricPrivacy, uiInteraction,
            persistenceLayer, importExportPrefs, decimalFormatter, pumpStatusProvider,
            ch, runningModeGuard, wizardBolusExecutor, batchExecutor, wizardExecutor
        )
    }

    /** An effective switch as the database hands it out; the blocks do not matter here, so a mock stands in */
    private fun effectiveSwitch(name: String, percentage: Int = 100, timeshiftHours: Int = 0, durationMs: Long = 0L): EPS =
        mock<EPS>().also {
            whenever(it.timestamp).thenReturn(now - T.mins(10).msecs())
            whenever(it.originalProfileName).thenReturn(name)
            whenever(it.originalPercentage).thenReturn(percentage)
            whenever(it.originalTimeshift).thenReturn(T.hours(timeshiftHours.toLong()).msecs())
            whenever(it.originalDuration).thenReturn(durationMs)
            // Left at zero by the sync paths, which is why the builder must not read it
            whenever(it.originalEnd).thenReturn(0L)
        }

    @Test
    fun `no profile in force gives nothing`() = runTest {
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(now)).thenReturn(null)

        assertThat(sut.profileInfo(now, null)).isNull()
    }

    @Test
    fun `a permanent switch is the name and its modifiers, with no end`() = runTest {
        // Built before the stubbing call: a mock made inside thenReturn() is a stubbing inside a stubbing
        val switch = effectiveSwitch("Default", percentage = 90, timeshiftHours = 1)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(now)).thenReturn(switch)

        assertThat(sut.profileInfo(now, null))
            .isEqualTo(ProfileInfo(name = "Default", percentage = 90, timeshiftHours = 1, endTime = null, returnsTo = null, fromScene = false))
    }

    @Test
    fun `a temporary switch ends at start plus duration and names the profile that returns`() = runTest {
        val start = now - T.mins(10).msecs()
        val duration = T.hours(1).msecs()
        val switch = effectiveSwitch("Night", percentage = 120, timeshiftHours = -2, durationMs = duration)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(now)).thenReturn(switch)
        val underlying = mock<PS>().also { whenever(it.profileName).thenReturn("Default") }
        // The one in force one millisecond after the temporary switch ends
        whenever(persistenceLayer.getProfileSwitchActiveAt(start + duration + 1)).thenReturn(underlying)

        assertThat(sut.profileInfo(now, null))
            .isEqualTo(ProfileInfo(name = "Night", percentage = 120, timeshiftHours = -2, endTime = start + duration, returnsTo = "Default", fromScene = false))
    }

    /**
     * The mark compares the scene's profile switch id with the switch in force now, not with the
     * link stored on the effective switch: on a client that link is the master's id from
     * Nightscout, while the scene's id is resolved to a local one.
     */
    @Test
    fun `a switch the active scene made is marked`() = runTest {
        val switch = effectiveSwitch("Sport", durationMs = T.hours(1).msecs())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(now)).thenReturn(switch)
        val inForce = mock<PS>().also { whenever(it.id).thenReturn(42L) }
        whenever(persistenceLayer.getProfileSwitchActiveAt(now)).thenReturn(inForce)

        val marked = sut.profileInfo(now, ActiveSceneState.ScopedRecords(psId = 42L))
        val other = sut.profileInfo(now, ActiveSceneState.ScopedRecords(psId = 7L))
        val noScene = sut.profileInfo(now, null)

        assertThat(marked?.fromScene).isTrue()
        assertThat(other?.fromScene).isFalse()
        assertThat(noScene?.fromScene).isFalse()
    }
}
