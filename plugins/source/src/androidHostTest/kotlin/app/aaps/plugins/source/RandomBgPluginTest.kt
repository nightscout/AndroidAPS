package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RandomBgPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump

    private lateinit var randomBgPlugin: RandomBgPlugin

    @BeforeEach fun prepare() {
        randomBgPlugin = RandomBgPlugin(context, rh, aapsLogger, persistenceLayer, virtualPump, preferences, config)
    }

    /**
     * Enable the plugin with the BLOCKING test API so onStart() runs in-line (it does not leak a
     * coroutine that would touch cleared mocks later), run [block], then disable to tear down the
     * handler thread and its scheduled refresh loop.
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

    @Test
    fun `When plugin enabled then insert data`() {
        stubInserts()
        enabled {
            randomBgPlugin.handleNewData()
            runTest { verify(persistenceLayer).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) }
        }
    }

    @Test
    fun `handleNewData in randomize mode inserts data`() {
        stubInserts()
        whenever(preferences.get(BooleanKey.BgSourceRandomBgRandomize)).thenReturn(true)
        enabled {
            randomBgPlugin.handleNewData()
            runTest { verify(persistenceLayer).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) }
        }
    }

    @Test
    fun `handleNewData does nothing when disabled`() {
        randomBgPlugin.handleNewData()
        runTest { verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) }
    }

    @Test
    fun startStopTest() = runBlocking {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        Assertions.assertNull(randomBgPlugin.handler)
        randomBgPlugin.onStart()
        Assertions.assertNotNull(randomBgPlugin.handler)
        randomBgPlugin.onStop()
        Assertions.assertNull(randomBgPlugin.handler)
    }

    @Test
    fun `specialEnableCondition is true in unfinished mode`() {
        whenever(config.isEnabled(ExternalOptions.UNFINISHED_MODE)).thenReturn(true)
        assertThat(randomBgPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun `preference screen content is provided`() {
        assertThat(randomBgPlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }
}
