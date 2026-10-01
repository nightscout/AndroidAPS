package app.aaps.implementation.plugin

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.os.PowerManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers the non-plugin permission mechanism added for the standalone Automation runtime:
 * permissions contributed via the `Set<PermissionProvider>` multibinding must surface from
 * [PluginPermissionsImpl.collectAllPermissions] alongside plugin and global permissions.
 *
 * `collectMissingPermissions` is exercised only for the exact-alarm group, with the system services it
 * reads mocked. The other groups need real Android permission APIs (ContextCompat) and an instrumented
 * context.
 */
class PluginPermissionsImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var context: Context

    private val locationGroup = PermissionGroup(
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
        rationaleTitle = TextRef.Literal(""),
        rationaleDescription = TextRef.Literal("")
    )

    private fun store(providers: Set<PermissionProvider>): PluginPermissionsImpl {
        // The registry is a mock now: permissions ask it for the plugin list and nothing else.
        val activePlugin = mock<ActivePlugin>()
        whenever(activePlugin.getPluginsList()).thenReturn(ArrayList<PluginBase>())
        return PluginPermissionsImpl(context, activePlugin, preferences) { providers }
    }

    @Test
    fun `collectAllPermissions includes non-plugin provider permissions`() {
        val provider = mock<PermissionProvider>()
        whenever(provider.requiredPermissions()).thenReturn(listOf(locationGroup))

        assertThat(store(setOf(provider)).collectAllPermissions()).contains(locationGroup)
    }

    @Test
    fun `collectAllPermissions omits the provider group when no providers are registered`() {
        assertThat(store(emptySet()).collectAllPermissions()).doesNotContain(locationGroup)
    }

    @Test
    fun `exact alarms are asked for even with no plugin enabled`() {
        // Background alarms and reminders both use setAlarmClock, which needs SCHEDULE_EXACT_ALARM. It
        // used to be asked for only while the EOPatch plugin was enabled, so everyone else on Android 14
        // and later - where a new install is denied it by default - never got the prompt.
        val group = store(emptySet()).collectAllPermissions().single { Manifest.permission.SCHEDULE_EXACT_ALARM in it.permissions }

        // Special: there is no runtime dialog for it, the user has to be sent to the settings screen.
        assertThat(group.special).isTrue()
    }

    private fun missingWithExactAlarms(allowed: Boolean): List<String> {
        val alarmManager = mock<AlarmManager>()
        whenever(alarmManager.canScheduleExactAlarms()).thenReturn(allowed)
        whenever(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(alarmManager)
        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(mock<PowerManager>())
        return store(emptySet()).collectMissingPermissions().flatMap { it.permissions }
    }

    @Test
    fun `the exact-alarm row is shown while alarms cannot be scheduled exactly`() {
        // canScheduleExactAlarms() is false only with neither the permission nor the battery-optimization
        // exemption - exactly when setAlarmClock would throw.
        assertThat(missingWithExactAlarms(allowed = false)).contains(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }

    @Test
    fun `the exact-alarm row is not shown once they can`() {
        // Also the case for a user who excluded AAPS from battery optimization: no extra prompt.
        assertThat(missingWithExactAlarms(allowed = true)).doesNotContain(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }
}
