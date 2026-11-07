package app.aaps.plugins.source.eversense

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.EversensePlugin
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EversensePluginTest {
    @Test fun registersPortableSettingsAndPreservesSharedSourcePreferencesWithoutBluetoothHardware() {
        val context: Context = mock()
        whenever(context.applicationContext).thenReturn(context)
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(context, mock(), mock(), preferences, mock())

        verify(preferences).registerPreferences(EversenseStringKey.entries)
        assertThat(plugin.pluginDescription.mainType).isEqualTo(PluginType.BGSOURCE)
        assertThat(plugin.pluginDescription.composeContentProvider).isNotNull()
        assertThat(plugin.pluginDescription.defaultPlugin).isFalse()
        val screen = plugin.getPreferenceScreenContent()
        assertThat(screen.items).containsAtLeast(BooleanKey.BgSourceUploadToNs, BooleanKey.BgSourceCreateSensorChange)
        val glucose = screen.items.filterIsInstance<PreferenceSubScreenDef>().single()
        val keys = glucose.items.filterIsInstance<PreferenceSubScreenDef>().flatMap { it.items }
        assertThat(keys).containsExactlyElementsIn(EversenseStringKey.entries)
        assertThat(EversenseStringKey.entries.associate { it.key to it.defaultValue }).containsExactly(
            "eversense_target_high", "7.8", "eversense_target_low", "4.4",
            "eversense_alert_high", "11.1", "eversense_alert_low", "3.9"
        )
    }
}
