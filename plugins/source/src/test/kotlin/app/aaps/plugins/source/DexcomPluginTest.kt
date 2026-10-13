package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DexcomPluginTest : TestBaseWithProfile() {

    private lateinit var dexcomPlugin: DexcomPlugin

    @BeforeEach
    fun setup() {
        dexcomPlugin = DexcomPlugin(rh, aapsLogger, context, config)
    }

    @Test
    fun advancedFilteringSupported() {
        assertThat(dexcomPlugin.advancedFilteringSupported()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        dexcomPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
