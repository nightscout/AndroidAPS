package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationReaderPluginTest : TestBaseWithProfile() {

    private lateinit var notificationReaderPlugin: NotificationReaderPlugin

    @BeforeEach
    fun setup() {
        notificationReaderPlugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        notificationReaderPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
