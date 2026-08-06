package app.aaps.plugins.source

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationReaderPluginTest : TestBaseWithProfile() {

    private lateinit var notificationReaderPlugin: NotificationReaderPlugin

    @BeforeEach
    fun setup() {
        // construction runs loadPackageConfig() (falls back to an empty config when the asset/pref is absent)
        notificationReaderPlugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context)
    }

    @Test
    fun `plugin is created and exposes a package config`() {
        assertThat(notificationReaderPlugin).isNotNull()
        assertThat(notificationReaderPlugin.packageConfig).isNotNull()
    }

    @Test
    fun `requires the special notification listener permission`() {
        val permissions = notificationReaderPlugin.requiredPermissions()
        assertThat(permissions).isNotEmpty()
        assertThat(permissions.first().special).isTrue()
        assertThat(permissions.flatMap { it.permissions })
            .contains(NotificationReaderPlugin.PERMISSION_NOTIFICATION_LISTENER)
    }
}
