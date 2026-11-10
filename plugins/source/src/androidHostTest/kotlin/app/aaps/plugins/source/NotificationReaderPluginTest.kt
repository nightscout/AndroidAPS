package app.aaps.plugins.source

import android.content.res.AssetManager
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.keys.StringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NotificationReaderPluginTest : TestBaseWithProfile() {

    private lateinit var notificationReaderPlugin: NotificationReaderPlugin

    private val bundledDefinitions = """
        {
          "version": 3,
          "packages": [
            {"package":"com.senseonics.eversense365.us","sensor":"Eversense","intervalMinutes":5},
            {"package":"com.senseonics.eversense365.ous","sensor":"Eversense","intervalMinutes":5}
          ]
        }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        // construction runs loadPackageConfig() (falls back to an empty config when the asset/pref is absent)
        notificationReaderPlugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context, mock())
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

    @Test
    fun `new bundled definitions add both Eversense365 packages after an app update`() {
        val stored = """{"version":2,"packages":[{"package":"com.senseonics.eversense365.us","sensor":"Eversense"}]}"""
        whenever(preferences.get(StringNonKey.NotificationReaderPackages)).thenReturn(stored)
        val assets = mock<AssetManager>()
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open("notification_reader_packages.json")).thenReturn(bundledDefinitions.byteInputStream())

        val plugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context, mock())

        assertThat(plugin.packageConfig.version).isEqualTo(3)
        for (pkg in listOf("com.senseonics.eversense365.us", "com.senseonics.eversense365.ous")) {
            assertThat(plugin.packageConfig.isSupportedPackage(pkg)).isTrue()
            assertThat(plugin.packageConfig.sensorForPackage(pkg)).isEqualTo(SourceSensor.EVERSENSE)
            assertThat(plugin.packageConfig.intervalForPackage(pkg, 0L)).isEqualTo(300_000L)
        }
        verify(preferences).put(StringNonKey.NotificationReaderPackages, bundledDefinitions)
    }

    @Test
    fun `newer cached definitions are preserved when bundled definitions are older`() {
        val stored = """{"version":4,"packages":[{"package":"com.senseonics.eversense365.ous","sensor":"Eversense"}]}"""
        whenever(preferences.get(StringNonKey.NotificationReaderPackages)).thenReturn(stored)
        val assets = mock<AssetManager>()
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open("notification_reader_packages.json")).thenReturn(bundledDefinitions.byteInputStream())

        val plugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, context, mock())

        assertThat(plugin.packageConfig.version).isEqualTo(4)
        assertThat(plugin.packageConfig.isSupportedPackage("com.senseonics.eversense365.ous")).isTrue()
        verify(preferences, never()).put(StringNonKey.NotificationReaderPackages, bundledDefinitions)
    }
}
