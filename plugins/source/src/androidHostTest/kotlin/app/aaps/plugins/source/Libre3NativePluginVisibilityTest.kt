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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Libre 3 native is listed in every build, with nothing to set up first, so the plugin shows up
 * wherever `ActivePlugin.getSpecificPluginsVisibleInList` is read - Config Builder, the Setup
 * Wizard, search and Quick Launch.
 *
 * These tests exist so a visibility gate cannot be added by accident.
 */
class Libre3NativePluginVisibilityTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var notificationManager: NotificationManager


    private lateinit var plugin: Libre3NativePlugin

    @BeforeEach
    fun setup() {
        plugin = Libre3NativePlugin(
            rh, aapsLogger, preferences, config, notificationManager, context, persistenceLayer, mock<ActivePlugin>(),
        )
    }

    @Test
    fun `1 - Libre 3 native is visible with no set-up at all`() {
        assertThat(plugin.showInList()).isTrue()
    }

    @Test
    fun `2 - Libre 3 native stays visible when no AAPS directory is selected`() {
        whenever(preferences.getIfExists(StringKey.AapsDirectoryUri)).thenReturn(null)

        assertThat(plugin.showInList()).isTrue()
    }

    @Test
    fun `6 - a sibling BG source plugin behaves the same way`() {
        val aidex = AidexPlugin(rh, aapsLogger, preferences, config, notificationManager)

        assertThat(plugin.showInList()).isTrue()
        assertThat(aidex.showInList()).isTrue()
        assertThat(aidex.enforcedState()).isNotEqualTo(EnforcedState.Disabled)
    }

    @Test
    fun `7 - being listed does not enable anything by itself`() {
        assertThat(plugin.enforcedState()).isNotEqualTo(EnforcedState.Disabled)
    }
}
