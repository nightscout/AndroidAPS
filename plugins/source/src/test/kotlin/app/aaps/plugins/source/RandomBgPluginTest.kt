package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.keys.IntKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RandomBgPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump

    private lateinit var randomBgPlugin: RandomBgPlugin

    @BeforeEach fun prepare() {
        randomBgPlugin = RandomBgPlugin(context, rh, aapsLogger, persistenceLayer, virtualPump, preferences, config)
    }

    @Test
    fun `When plugin enabled then insert data`() {
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        whenever(persistenceLayer.insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        whenever(config.isUnfinishedMode()).thenReturn(true)
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        randomBgPlugin.setPluginEnabled(PluginType.BGSOURCE, true)
        randomBgPlugin.handleNewData()

        verify(persistenceLayer).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun startStopTest() {
        whenever(preferences.get(IntKey.BgSourceRandomInterval)).thenReturn(5)
        Assertions.assertNull(randomBgPlugin.handler)
        randomBgPlugin.onStart()
        Assertions.assertNotNull(randomBgPlugin.handler)
        randomBgPlugin.onStop()
        Assertions.assertNull(randomBgPlugin.handler)
    }

    @Test
    fun advancedFilteringSupported() {
        assertThat(randomBgPlugin.advancedFilteringSupported()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        randomBgPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
