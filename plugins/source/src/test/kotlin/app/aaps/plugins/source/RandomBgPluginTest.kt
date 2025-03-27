package app.aaps.plugins.source

import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class RandomBgPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var virtualPump: VirtualPump

    private lateinit var randomBgPlugin: RandomBgPlugin

    @BeforeEach fun prepare() {
        preferenceManager = PreferenceManager(context)
        randomBgPlugin = RandomBgPlugin(context, rh, aapsLogger, persistenceLayer, virtualPump, preferences, config)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        randomBgPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
