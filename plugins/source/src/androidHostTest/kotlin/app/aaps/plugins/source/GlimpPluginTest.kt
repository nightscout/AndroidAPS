package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock

class GlimpPluginTest : TestBaseWithProfile() {

    private lateinit var glimpPlugin: GlimpPlugin

    @BeforeEach
    fun setup() {
        glimpPlugin = GlimpPlugin(rh, aapsLogger, preferences, config, mock())
    }
}
