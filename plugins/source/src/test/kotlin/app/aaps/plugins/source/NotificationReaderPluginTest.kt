package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach

class NotificationReaderPluginTest : TestBaseWithProfile() {

    private lateinit var notificationReaderPlugin: NotificationReaderPlugin

    @BeforeEach
    fun setup() {
        notificationReaderPlugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context)
    }
}
