package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlimpPluginTest : TestBaseWithProfile() {

    private lateinit var glimpPlugin: GlimpPlugin

    @BeforeEach
    fun setup() {
        glimpPlugin = GlimpPlugin(rh, aapsLogger, preferences)
    }

    @Test
    fun advancedFilteringSupported() {
        assertThat(glimpPlugin.advancedFilteringSupported()).isFalse()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        glimpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
