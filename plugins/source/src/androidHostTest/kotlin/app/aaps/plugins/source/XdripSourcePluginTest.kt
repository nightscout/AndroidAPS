package app.aaps.plugins.source

import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XdripSourcePluginTest : TestBaseWithProfile() {

    private lateinit var xdripSourcePlugin: XdripSourcePlugin

    @BeforeEach
    fun setup() {
        xdripSourcePlugin = XdripSourcePlugin(rh, aapsLogger, preferences, config)
    }

    @Test
    fun `plugin is created`() {
        assertThat(xdripSourcePlugin).isNotNull()
    }

    @Test
    fun `preference screen content is provided`() {
        // exercises AbstractBgSourceWithSensorInsertLogPlugin.getPreferenceScreenContent
        assertThat(xdripSourcePlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }
}
