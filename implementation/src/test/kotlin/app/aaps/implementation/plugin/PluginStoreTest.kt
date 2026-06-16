package app.aaps.implementation.plugin

import android.content.Context
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers the non-plugin permission mechanism added for the standalone Automation runtime:
 * permissions contributed via the `Set<PermissionProvider>` multibinding must surface from
 * [PluginStore.collectAllPermissions] alongside plugin and global permissions.
 *
 * `collectMissingPermissions` is intentionally not exercised here — it calls into the Android
 * permission APIs (ContextCompat/PowerManager) which need an instrumented context.
 */
class PluginStoreTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var context: Context

    private val locationGroup = PermissionGroup(
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
        rationaleTitle = 0,
        rationaleDescription = 0
    )

    private fun store(providers: Set<PermissionProvider>): PluginStore {
        // dagger.Lazy supplied directly; pump lazy is never dereferenced by the permission paths.
        val pumpLazy = Lazy<PumpWithConcentration> { mock() }
        val providerLazy = Lazy { providers }
        return PluginStore(aapsLogger, preferences, pumpLazy, providerLazy).also {
            it.plugins = emptyList<PluginBase>()
        }
    }

    @Test
    fun `collectAllPermissions includes non-plugin provider permissions`() {
        val provider = mock<PermissionProvider>()
        whenever(provider.requiredPermissions()).thenReturn(listOf(locationGroup))

        assertThat(store(setOf(provider)).collectAllPermissions(context)).contains(locationGroup)
    }

    @Test
    fun `collectAllPermissions omits the provider group when no providers are registered`() {
        assertThat(store(emptySet()).collectAllPermissions(context)).doesNotContain(locationGroup)
    }
}
