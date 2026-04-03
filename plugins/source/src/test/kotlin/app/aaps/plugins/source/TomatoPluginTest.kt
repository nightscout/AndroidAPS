package app.aaps.plugins.source

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class TomatoPluginTest : TestBase() {

    private lateinit var tomatoPlugin: TomatoPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config

    @BeforeEach
    fun setup() {
        tomatoPlugin = TomatoPlugin(rh, aapsLogger, preferences, config)
    }

    @Test
    fun `plugin is created`() {
        assertThat(tomatoPlugin).isNotNull()
    }
}
