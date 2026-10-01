package app.aaps.plugins.source

import org.mockito.kotlin.mock
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach

class GlimpPluginTest : TestBaseWithProfile() {

    private lateinit var glimpPlugin: GlimpPlugin

    @BeforeEach
    fun setup() {
        glimpPlugin = GlimpPlugin(rh, aapsLogger, preferences, config, mock())
    }
}
