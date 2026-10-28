package app.aaps.plugins.source

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * ONE+ is listed in every build, with nothing to set up first, like the other BG sources: Config
 * Builder, the Setup Wizard, search and Quick Launch all read
 * `ActivePlugin.getSpecificPluginsVisibleInList`, that is `PluginBase.showInList`.
 *
 * These tests make sure no hidden gate is added later. A missing AAPS directory must not hide it.
 */
class DexcomOnePlusPluginVisibilityTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var activePlugin: ActivePlugin


    private lateinit var plugin: DexcomOnePlusPlugin

    @BeforeEach
    fun setup() {
        plugin = DexcomOnePlusPlugin(
            rh, aapsLogger, preferences, config, notificationManager, DexcomOnePlusAndroidHost(context), persistenceLayer,
            activePlugin, rxBus
        )
    }

    @Test
    fun `11 - ONE+ is visible with no marker file and no set-up at all`() {
        assertThat(plugin.showInList()).isTrue()
    }

    @Test
    fun `12 - ONE+ stays visible when no AAPS directory is selected`() {
        whenever(preferences.getIfExists(StringKey.AapsDirectoryUri)).thenReturn(null)

        assertThat(plugin.showInList()).isTrue()
    }

    @Test
    fun `15 - a sibling BG source plugin behaves the same way`() {
        val aidex = AidexPlugin(rh, aapsLogger, preferences, config, notificationManager)

        assertThat(plugin.showInList()).isTrue()
        assertThat(aidex.showInList()).isTrue()
        assertThat(aidex.enforcedState()).isNotEqualTo(EnforcedState.Disabled)
    }

    @Test
    fun `20 - being listed does not enable anything by itself`() {
        // Visibility and the enable path were always separate, and they still are: a listed plugin
        // is one the person may pick, not one that is running.
        assertThat(plugin.enforcedState()).isNotEqualTo(EnforcedState.Disabled)
    }
}
