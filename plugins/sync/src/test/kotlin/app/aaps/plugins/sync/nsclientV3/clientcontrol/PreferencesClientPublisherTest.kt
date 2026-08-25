package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesClientPublisherTest {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var clientControlRoundTrip: ClientControlRoundTrip
    @Mock lateinit var config: Config
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var aapsLogger: AAPSLogger

    private val changes = MutableSharedFlow<NonPreferenceKey>(extraBufferCapacity = 10)
    private val key = BooleanKey.GeneralInsulinConcentration

    private lateinit var sut: PreferencesClientPublisher

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(rh.gs(any<Int>())).thenReturn("update settings")
        whenever(preferences.syncedLocalChanges).thenReturn(changes)
        whenever(preferences.get(key as BooleanNonPreferenceKey)).thenReturn(true)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, key.key)).thenReturn(100L)
        sut = PreferencesClientPublisher(preferences, clientControlRoundTrip, config, rh, aapsLogger)
    }

    @Test
    fun editRoutesToConfirmedRoundTrip() = runTest {
        sut.start(backgroundScope)
        runCurrent()

        changes.emit(key)
        advanceTimeBy(600); runCurrent() // settle window

        verify(clientControlRoundTrip).run(eq(ClientControlActionDispatcher.Command.PreferenceEdit(mapOf(key.key to ("true" to 100L)))), any())
    }

    @Test
    fun batchesKeysChangedWithinSettleWindowIntoOneRoundTrip() = runTest {
        val pct = IntKey.OverviewBolusPercentage
        whenever(preferences.get(pct as IntNonPreferenceKey)).thenReturn(80)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, pct.key)).thenReturn(200L)
        sut.start(backgroundScope)
        runCurrent()

        changes.emit(key)
        changes.emit(pct)
        runCurrent()                     // both accumulate in pending
        advanceTimeBy(600); runCurrent() // single settle → single round-trip

        verify(clientControlRoundTrip).run(
            eq(ClientControlActionDispatcher.Command.PreferenceEdit(mapOf(key.key to ("true" to 100L), pct.key to ("80" to 200L)))), any()
        )
    }

    @Test
    fun definitionBlobRoundTrips() = runTest {
        val qw = StringNonKey.QuickWizard
        val blob = """[{"buttonText":"Meal"}]"""
        whenever(preferences.get(qw as StringNonPreferenceKey)).thenReturn(blob)
        whenever(preferences.get(LongComposedKey.SyncedPrefModified, qw.key)).thenReturn(300L)
        sut.start(backgroundScope)
        runCurrent()

        changes.emit(qw)
        advanceTimeBy(600); runCurrent()

        verify(clientControlRoundTrip).run(eq(ClientControlActionDispatcher.Command.PreferenceEdit(mapOf(qw.key to (blob to 300L)))), any())
    }

    @Test
    fun doesNotRunOnMaster() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(false)
        sut.start(backgroundScope)

        changes.emit(key)
        advanceTimeBy(600); runCurrent()

        verify(clientControlRoundTrip, never()).run(any(), any())
    }
}
