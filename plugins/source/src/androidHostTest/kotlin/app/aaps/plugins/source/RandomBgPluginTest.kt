package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RandomBgPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump

    /** Counts what the plugin does with its wake lock, as the platform would see it. */
    private class RecordingWakeLock(private val available: Boolean = true) : RandomBgWakeLock {

        var acquired = 0
        var released = 0

        override fun acquire(): RandomBgWakeLock.Held? {
            if (!available) return null
            acquired++
            return object : RandomBgWakeLock.Held {
                override fun release() {
                    released++
                }
            }
        }
    }

    private val scope = TestScope(StandardTestDispatcher())
    private val wakeLock = RecordingWakeLock()
    private lateinit var text: TextResolver
    private lateinit var randomBgPlugin: RandomBgPlugin

    @BeforeEach fun prepare() {
        // The plugin's name is shared text now, not an Android resource, so it is resolved from the
        // generated English strings - which also checks that the name really exists.
        text = generatedTextResolver(rh, "source" to SourceStringsValues::textOf)
        randomBgPlugin = plugin(wakeLock)
    }

    private fun plugin(lock: RandomBgWakeLock) =
        RandomBgPlugin(text, aapsLogger, persistenceLayer, virtualPump, preferences, config, mock(), dateUtil, scope, lock)

    /**
     * Enable the plugin with the BLOCKING test API so onStart() runs in-line, run [block], then disable
     * it to stop the refresh loop. The loop runs on [scope], so it moves only when a test moves that.
     */
    private fun enabled(block: () -> Unit) {
        whenever(config.isEnabled(ExternalOptions.UNFINISHED_MODE)).thenReturn(true)
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        randomBgPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        try {
            block()
        } finally {
            randomBgPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, false)
        }
    }

    private fun stubInserts() = runTest {
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(PersistenceLayer.TransactionResult())
        whenever(persistenceLayer.insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(PersistenceLayer.TransactionResult())
    }

    private fun insertsSoFar(count: Int) = runTest {
        verify(persistenceLayer, times(count)).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `When plugin enabled then insert data`() {
        stubInserts()
        enabled {
            runTest { randomBgPlugin.handleNewData() }
            insertsSoFar(1)
        }
    }

    @Test
    fun `handleNewData in randomize mode inserts data`() {
        stubInserts()
        whenever(preferences.get(BooleanKey.BgSourceRandomBgRandomize)).thenReturn(true)
        enabled {
            runTest { randomBgPlugin.handleNewData() }
            insertsSoFar(1)
        }
    }

    @Test
    fun `handleNewData does nothing when disabled`() {
        runTest { randomBgPlugin.handleNewData() }
        runTest { verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) }
    }

    @Test
    fun startStopTest() = runBlocking {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        assertThat(randomBgPlugin.job).isNull()
        randomBgPlugin.onStart()
        assertThat(randomBgPlugin.job).isNotNull()
        randomBgPlugin.onStop()
        assertThat(randomBgPlugin.job).isNull()
    }

    /** The lock is held for exactly as long as the plugin runs, the way the Android plugin always held it. */
    @Test
    fun `the start takes the wake lock and the stop gives it back`() = runBlocking {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        randomBgPlugin.onStart()
        assertThat(wakeLock.acquired).isEqualTo(1)
        assertThat(wakeLock.released).isEqualTo(0)

        randomBgPlugin.onStop()
        assertThat(wakeLock.released).isEqualTo(1)
    }

    /** A second start must not take a second lock and leak the first. */
    @Test
    fun `starting twice holds one lock`() = runBlocking {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        randomBgPlugin.onStart()
        randomBgPlugin.onStart()
        randomBgPlugin.onStop()

        assertThat(wakeLock.acquired - wakeLock.released).isEqualTo(0)
    }

    /** iOS and the desktop have no lock, and the plugin still starts and stops there. */
    @Test
    fun `a platform with no wake lock still runs the plugin`() = runBlocking {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        val noLock = plugin(RecordingWakeLock(available = false))

        noLock.onStart()
        assertThat(noLock.job).isNotNull()
        noLock.onStop()
        assertThat(noLock.job).isNull()
    }

    /**
     * The timer that replaced the Android `Handler`: the first reading at the next whole interval plus a
     * second, then one each interval. Moved over a bounded window and counted, never run until idle -
     * the loop re-arms itself, so "idle" never comes.
     */
    @Test
    fun `the loop makes one reading per interval`() {
        stubInserts()
        enabled {
            scope.runCurrent()
            insertsSoFar(0)

            // The first reading is due at most five minutes and one second after the start.
            scope.testScheduler.advanceTimeBy((5.minutes + 2.seconds).inWholeMilliseconds)
            scope.runCurrent()
            insertsSoFar(1)

            scope.testScheduler.advanceTimeBy(5.minutes.inWholeMilliseconds)
            scope.runCurrent()
            insertsSoFar(2)
        }
    }

    /**
     * One reading that fails must not end the loop. The loop runs on the app scope, which has no handler: a
     * throw there ends the app on Android, and without the loop no more readings come.
     */
    @Test
    fun `a failed reading does not stop the loop`() {
        runTest {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenThrow(IllegalStateException("database is closed"))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        enabled {
            scope.testScheduler.advanceTimeBy((5.minutes + 2.seconds).inWholeMilliseconds)
            scope.runCurrent()
            insertsSoFar(1)

            scope.testScheduler.advanceTimeBy(5.minutes.inWholeMilliseconds)
            scope.runCurrent()
            insertsSoFar(2)
            assertThat(randomBgPlugin.job?.isActive).isTrue()
        }
    }

    @Test
    fun `unfinished mode leaves the choice to the user`() {
        whenever(config.isEnabled(ExternalOptions.UNFINISHED_MODE)).thenReturn(true)
        assertThat(randomBgPlugin.enforcedState()).isNull()
    }

    @Test
    fun `the plugin is named in shared text`() {
        assertThat(randomBgPlugin.name).isEqualTo("Random BG")
    }

    @Test
    fun `preference screen content is provided`() {
        assertThat(randomBgPlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }
}
