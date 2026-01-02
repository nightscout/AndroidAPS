package app.aaps.plugins.source

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class SyaiPluginTest : TestBase() {

    private lateinit var syaiPlugin: SyaiPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences

    @BeforeEach
    fun setup() {
        syaiPlugin = SyaiPlugin(rh, aapsLogger, preferences)
    }

    @Test fun advancedFilteringSupported() {
        assertThat(syaiPlugin.advancedFilteringSupported()).isTrue()
    }
}
