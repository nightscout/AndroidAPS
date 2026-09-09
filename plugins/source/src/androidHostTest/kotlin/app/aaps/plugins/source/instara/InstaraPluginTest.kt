package app.aaps.plugins.source.instara

import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InstaraPluginTest : TestBaseWithProfile() {

    private lateinit var instaraPlugin: InstaraPlugin

    @BeforeEach fun prepare() {
        instaraPlugin = InstaraPlugin(context, rh, aapsLogger, preferences, config)
    }

    @Test
    fun `plugin is created`() {
        assertThat(instaraPlugin).isNotNull()
    }

    @Test
    fun `preference screen content is provided`() {
        assertThat(instaraPlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }
}
